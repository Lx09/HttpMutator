package es.us.isa.httpmutator.core.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import es.us.isa.httpmutator.core.converter.ConversionException;
import es.us.isa.httpmutator.core.converter.impl.HarConverter;
import es.us.isa.httpmutator.core.model.HttpExchange;
import es.us.isa.httpmutator.core.model.Mutant;
import es.us.isa.httpmutator.core.model.StandardHttpRequest;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;

/**
 * A {@link MutantWriter} implementation that produces a HAR (HTTP Archive)
 * document containing mutated HTTP interactions.
 *
 * <p>This writer:</p>
 * <ul>
 *     <li>Preserves the original HAR-style request (from {@link StandardHttpRequest})</li>
 *     <li>Converts the mutated {@link StandardHttpResponse} into HAR response format</li>
 *     <li>Appends every mutated pair as a new HAR entry under {@code log.entries}</li>
 * </ul>
 *
 * <p>Note: this implementation accumulates all entries in memory. For very large
 * mutation sets, a streaming writer would be required.</p>
 */
public class HarMutantWriter implements MutantWriter {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HarConverter converter = new HarConverter();
    private final Writer out;

    /** Root HAR object: { "log": { ... } } */
    private final ObjectNode root;

    /** HAR log object: contains metadata + entries[] */
    private final ObjectNode log;

    /** HAR log.entries array (mutated interactions appended here) */
    private final ArrayNode entries;

    private boolean flushed = false;
    private boolean closed = false;

    /**
     * Creates a new HAR writer that will output the HAR JSON to the given {@link Writer}
     * when flushed or closed.
     */
    public HarMutantWriter(Writer out) {
        this.out = out;

        this.root = objectMapper.createObjectNode();
        this.log = objectMapper.createObjectNode();
        this.entries = objectMapper.createArrayNode();

        log.put("version", "1.2");

        ObjectNode creator = objectMapper.createObjectNode();
        creator.put("name", "HttpMutator");
        creator.put("version", "1.0.0");
        log.set("creator", creator);

        log.set("entries", entries);
        root.set("log", log);
    }

    /**
     * Append a mutated exchange into HAR log.entries.
     */
    @Override
    public void write(HttpExchange exchange,
                      StandardHttpResponse mutatedResponse,
                      Mutant mutant) throws IOException {

        if (closed) {
            throw new IOException("HarMutantWriter is already closed");
        }

        ObjectNode entry = objectMapper.createObjectNode();

        // ------------------------------------------------------------------
        // 1) Request: convert StandardHttpRequest → HAR format
        // ------------------------------------------------------------------
        StandardHttpRequest req = exchange.getRequest();
        if (req != null) {
            JsonNode harReq;
            try {
                harReq = converter.fromStandardRequest(req);
            } catch (ConversionException e) {
                throw new IOException("Failed to convert request", e);
            }
            entry.set("request", harReq);
        } else {
            entry.putNull("request");
        }

        // ------------------------------------------------------------------
        // 2) Response: convert StandardHttpResponse → HAR format
        // ------------------------------------------------------------------
        JsonNode harResp;
        try {
            harResp = converter.fromStandardResponse(mutatedResponse);
        } catch (ConversionException e) {
            throw new IOException("Failed to convert mutated response", e);
        }
        entry.set("response", harResp);

        // ------------------------------------------------------------------
        // 3) Add optional HAR timings block
        // ------------------------------------------------------------------
        ObjectNode timings = objectMapper.createObjectNode();
        timings.put("send", 0);
        timings.put("wait", 0);
        timings.put("receive", 0);
        entry.set("timings", timings);

        // ------------------------------------------------------------------
        // 4) Attach traceability metadata
        // ------------------------------------------------------------------
        entry.put("_hm_original_id", exchange.getId());
        entry.put("_hm_mutator", mutant.getMutatorClassName());
        entry.put("_hm_operator", mutant.getOperatorClassName());
        entry.put("_hm_original_json_path", mutant.getOriginalJsonPath());

        // ------------------------------------------------------------------
        // 5) Append entry to log.entries
        // ------------------------------------------------------------------
        entries.add(entry);
    }

    @Override
    public void flush() throws IOException {
        if (flushed) {
            return;
        }
        objectMapper.writeValue(out, root);
        out.flush();
        flushed = true;
    }

    @Override
    public void close() throws IOException {
        if (!flushed) {
            flush();
        }
        if (!closed) {
            out.close();
            closed = true;
        }
    }
}