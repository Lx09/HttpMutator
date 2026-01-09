package es.us.isa.httpmutator.core.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.luben.zstd.ZstdOutputStream;
import es.us.isa.httpmutator.core.model.HttpExchange;
import es.us.isa.httpmutator.core.model.Mutant;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A MutantWriter that writes mutated responses as sharded
 * Zstandard-compressed JSONL files (*.jsonl.zst).
 *
 * <p>Each line is one JSON object. Only the field
 * {@code _hm_original_id} is attached as metadata.</p>
 */
public class ShardedZstdJsonlMutantWriter implements MutantWriter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path outputDir;
    private final String shardPrefix;

    /** Sharding limits */
    private final long maxLinesPerShard;
    private final long maxUncompressedBytes;

    /** Zstd compression level */
    private final int zstdLevel;

    /** Current shard state */
    private int shardIndex = 0;
    private long currentLines = 0;
    private long currentBytes = 0;

    private Writer out;
    private ZstdOutputStream zstdOut;
    private boolean closed = false;

    public ShardedZstdJsonlMutantWriter(
            Path outputDir,
            String shardPrefix,
            long maxLinesPerShard,
            long maxUncompressedBytes,
            int zstdLevel
    ) throws IOException {

        this.outputDir = Objects.requireNonNull(outputDir, "outputDir must not be null");
        this.shardPrefix = Objects.requireNonNull(shardPrefix, "shardPrefix must not be null");
        this.maxLinesPerShard = maxLinesPerShard;
        this.maxUncompressedBytes = maxUncompressedBytes;
        this.zstdLevel = zstdLevel;

        Files.createDirectories(outputDir);
        openNextShard();
    }

    /**
     * Convenience constructor with conservative defaults.
     */
    public ShardedZstdJsonlMutantWriter(Path outputDir, String shardPrefix)
            throws IOException {
        this(
                outputDir,
                shardPrefix,
                500_000,   // lines per shard
                1L << 30,  // 1 GB uncompressed
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

        // 2) Ensure ObjectNode
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

        // 4) Serialize one JSONL line
        String line = objectMapper.writeValueAsString(lineObject);
        out.write(line);
        out.write('\n');

        currentLines++;
        currentBytes += line.length() + 1;

        // 5) Rotate shard if needed
        if (shouldRotateShard()) {
            rotateShard();
        }
    }

    private boolean shouldRotateShard() {
        return currentLines >= maxLinesPerShard
                || currentBytes >= maxUncompressedBytes;
    }

    private void rotateShard() throws IOException {
        closeCurrentShard();
        openNextShard();
    }

    private void openNextShard() throws IOException {
        String filename = String.format(
                "%s-%05d.jsonl.zst",
                shardPrefix,
                shardIndex++
        );

        Path shardPath = outputDir.resolve(filename);

        zstdOut = new ZstdOutputStream(
                new BufferedOutputStream(Files.newOutputStream(shardPath)),
                zstdLevel
        );
        out = new OutputStreamWriter(zstdOut, StandardCharsets.UTF_8);

        currentLines = 0;
        currentBytes = 0;
    }

    private void closeCurrentShard() throws IOException {
        if (out != null) {
            try {
                out.flush();
            } finally {
                out.close();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        if (!closed && out != null) {
            out.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            closeCurrentShard();
        }
    }
}
