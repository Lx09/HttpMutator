package es.us.isa.httpmutator.core.writer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.luben.zstd.ZstdOutputStream;
import es.us.isa.httpmutator.core.model.HttpExchange;
import es.us.isa.httpmutator.core.model.Mutant;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * A MutantWriter that writes mutated responses as sharded
 * Zstandard-compressed JSONL files (*.jsonl.zst).
 *
 * <p>Each line is one JSON object. Only the field {@code _hm_original_id}
 * is attached as metadata.</p>
 *
 * <p>Shard files are written to a temporary name first (suffix ".tmp"),
 * and atomically renamed to the final name when the shard is closed.</p>
 *
 * <p>This implementation uses streaming JSON output (JsonGenerator) to avoid
 * building a huge intermediate String per line (writeValueAsString), which
 * significantly reduces GC pressure and improves throughput for large bodies.</p>
 */
public class ShardedZstdJsonlMutantWriter implements MutantWriter {

    private final ObjectMapper objectMapper;
    private final JsonFactory jsonFactory;

    private final Path outputDir;
    private final String shardPrefix;

    /** Sharding limits */
    private final long maxLinesPerShard;
    private final long maxUncompressedBytesApprox;

    /** Zstd compression level */
    private final int zstdLevel;

    /** Current shard state */
    private int shardIndex = 0;
    private long currentLines = 0;

    /**
     * Note: with streaming we cannot cheaply know exact "uncompressed bytes".
     * We maintain an approximate counter based on bytes written to the generator
     * (tracked in CountingOutputStream).
     */
    private long currentUncompressedBytes = 0;

    /** Current shard streams */
    private OutputStream fileOut;          // raw file output stream
    private ZstdOutputStream zstdOut;      // compression stream
    private CountingOutputStream countOut; // counts bytes written into zstd stream (pre-compression)
    private JsonGenerator jsonGen;         // streaming JSON writer

    private boolean closed = false;

    /** Current shard paths */
    private Path currentTmpPath;
    private Path currentFinalPath;

    public ShardedZstdJsonlMutantWriter(
            Path outputDir,
            String shardPrefix,
            long maxLinesPerShard,
            long maxUncompressedBytesApprox,
            int zstdLevel
    ) throws IOException {

        this.outputDir = Objects.requireNonNull(outputDir, "outputDir must not be null");
        this.shardPrefix = Objects.requireNonNull(shardPrefix, "shardPrefix must not be null");
        this.maxLinesPerShard = maxLinesPerShard;
        this.maxUncompressedBytesApprox = maxUncompressedBytesApprox;
        this.zstdLevel = zstdLevel;

        this.objectMapper = new ObjectMapper();
        this.jsonFactory = objectMapper.getFactory();

        Files.createDirectories(outputDir);
        openNextShard();
    }

    /**
     * Convenience constructor with conservative defaults.
     */
    public ShardedZstdJsonlMutantWriter(Path outputDir, String shardPrefix) throws IOException {
        this(
                outputDir,
                shardPrefix,
                500_000,   // lines per shard
                1L << 30,  // ~1 GB uncompressed (approx)
                6          // zstd compression level
        );
    }

    @Override
    public void write(HttpExchange exchange,
                      StandardHttpResponse mutatedResponse,
                      Mutant mutant) throws IOException {

        if (closed) {
            throw new IOException("ShardedZstdJsonlMutantWriter is already closed");
        }

        // 1) Convert response to canonical JsonNode
        JsonNode canonical = mutatedResponse.toJsonNode();
        if (canonical == null || canonical.isNull()) {
            return;
        }

        // 2) Ensure ObjectNode (same semantics as your previous code)
        final ObjectNode lineObject;
        if (canonical instanceof ObjectNode) {
            lineObject = (ObjectNode) canonical;
        } else {
            lineObject = objectMapper.createObjectNode();
            lineObject.set("Body", canonical);
        }

        // 3) Attach ONLY _hm_original_id
        String originalId = exchange.getId();
        if (originalId != null) {
            lineObject.put("_hm_original_id", originalId);
        }

        // 4) Streaming write: JSON object + '\n' (JSONL)
        //    IMPORTANT: do NOT pretty-print; JSONL requires single-line objects.
        objectMapper.writeValue(jsonGen, lineObject);
        jsonGen.writeRaw('\n');

        currentLines++;
        // update byte counter from CountingOutputStream (pre-compression bytes)
        currentUncompressedBytes = countOut.getCount();

        // 5) Rotate shard if needed
        if (shouldRotateShard()) {
            rotateShard();
        }
    }

    private boolean shouldRotateShard() {
        return currentLines >= maxLinesPerShard
                || currentUncompressedBytes >= maxUncompressedBytesApprox;
    }

    private void rotateShard() throws IOException {
        closeCurrentShardAndCommit();
        openNextShard();
    }

    private void openNextShard() throws IOException {
        String baseName = String.format("%s-%05d.jsonl.zst", shardPrefix, shardIndex++);
        currentFinalPath = outputDir.resolve(baseName);
        currentTmpPath = outputDir.resolve(baseName + ".tmp");

        // Defensive: if a stale tmp exists (e.g., previous crash), remove it to avoid surprises.
        Files.deleteIfExists(currentTmpPath);

        fileOut = Files.newOutputStream(currentTmpPath);
        zstdOut = new ZstdOutputStream(new BufferedOutputStream(fileOut), zstdLevel);

        // Count bytes written BEFORE compression.
        // (This is the "uncompressed JSONL bytes" going into the compressor.)
        countOut = new CountingOutputStream(zstdOut);

        // JsonGenerator writes UTF-8 bytes directly to OutputStream (no intermediate String)
        jsonGen = jsonFactory.createGenerator(countOut);
        // Keep defaults: no pretty printer.

        currentLines = 0;
        currentUncompressedBytes = 0;
    }

    /**
     * Close current shard stream, then atomically rename tmp -> final.
     * If atomic move is not supported by the filesystem, fall back to a regular move.
     */
    private void closeCurrentShardAndCommit() throws IOException {
        if (jsonGen == null) {
            return;
        }

        IOException closeError = null;

        // Close in the right order:
        // - jsonGen.close() flushes JSON generator buffers and closes underlying stream by default.
        //   However, we want explicit control; so close generator, then close streams defensively.
        try {
            jsonGen.flush();
        } catch (IOException e) {
            closeError = e;
        }

        try {
            jsonGen.close();
        } catch (IOException e) {
            if (closeError == null) closeError = e;
        } finally {
            jsonGen = null;
        }

        // At this point countOut/zstdOut/fileOut may already be closed via jsonGen.close(),
        // but closing closed streams is typically safe; we do it defensively.
        try {
            if (countOut != null) countOut.close();
        } catch (IOException e) {
            if (closeError == null) closeError = e;
        } finally {
            countOut = null;
        }

        try {
            if (zstdOut != null) zstdOut.close();
        } catch (IOException e) {
            if (closeError == null) closeError = e;
        } finally {
            zstdOut = null;
        }

        try {
            if (fileOut != null) fileOut.close();
        } catch (IOException e) {
            if (closeError == null) closeError = e;
        } finally {
            fileOut = null;
        }

        // If closing failed, do NOT rename to final; surface the error.
        if (closeError != null) {
            throw closeError;
        }

        // Commit tmp -> final
        try {
            Files.move(currentTmpPath, currentFinalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(currentTmpPath, currentFinalPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            return;
        }
        if (jsonGen != null) {
            jsonGen.flush();
        }
        if (zstdOut != null) {
            zstdOut.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        // Close & commit the last shard as well
        closeCurrentShardAndCommit();
    }

    /**
     * Simple OutputStream wrapper to count bytes written into it.
     * This counts bytes BEFORE compression because it sits "above" zstdOut.
     */
    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count = 0;

        CountingOutputStream(OutputStream delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }

        long getCount() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
            count += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
