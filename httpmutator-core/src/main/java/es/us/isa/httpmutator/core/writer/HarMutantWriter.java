package es.us.isa.httpmutator.core.writer;

import com.fasterxml.jackson.core.JsonGenerator;
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

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * HAR writer with viewer-friendly compatibility:
 * - entry.startedDateTime fixed to millisecond precision
 * - entry.time present
 * - request is never null (minimal placeholder if missing)
 * - response includes common fields and bodySize is aligned with content.size when possible
 * - log.pages present (empty)
 */
public class HarMutantWriter implements MutantWriter {

    private static final DateTimeFormatter ISO_MILLIS_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    private static final String DEFAULT_HTTP_VERSION = "HTTP/1.1";
    private static final String UNKNOWN_URL = "http://localhost/unknown";
    private static final String UNKNOWN_METHOD = "GET";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HarConverter converter = new HarConverter();
    private final Writer out;

    /** Root HAR object: { "log": { ... } } */
    private final ObjectNode root;

    /** HAR log object: contains metadata + pages[] + entries[] */
    private final ObjectNode log;

    /** HAR log.entries array (mutated interactions appended here) */
    private final ArrayNode entries;

    private boolean flushed = false;
    private boolean closed = false;

    public HarMutantWriter(Writer out) {
        this.out = out;
        // Prevent Jackson from closing the underlying Writer in writeValue(...)
        this.objectMapper.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        this.root = objectMapper.createObjectNode();
        this.log = objectMapper.createObjectNode();
        this.entries = objectMapper.createArrayNode();

        log.put("version", "1.2");

        ObjectNode creator = objectMapper.createObjectNode();
        creator.put("name", "HttpMutator");
        creator.put("version", "1.0.0");
        log.set("creator", creator);

        // Many viewers expect pages[] to exist.
        log.set("pages", objectMapper.createArrayNode());

        log.set("entries", entries);
        root.set("log", log);
    }

    @Override
    public void write(HttpExchange exchange,
                      StandardHttpResponse mutatedResponse,
                      Mutant mutant) throws IOException {

        if (closed) {
            throw new IOException("HarMutantWriter is already closed");
        }
        if (flushed) {
            throw new IOException("HarMutantWriter is already flushed; no further writes are allowed");
        }

        ObjectNode entry = objectMapper.createObjectNode();

        // ---- Viewer-friendly timestamps: millisecond precision ----
        entry.put("startedDateTime", ISO_MILLIS_UTC.format(Instant.now()));
        entry.put("time", 0);

        // ---- Request (never null) ----
        entry.set("request", toCompatibleRequest(exchange));

        // ---- Response (patched) ----
        ObjectNode resp = toCompatibleResponse(mutatedResponse);
        entry.set("response", resp);

        // ---- Timings ----
        ObjectNode timings = objectMapper.createObjectNode();
        timings.put("send", 0);
        timings.put("wait", 0);
        timings.put("receive", 0);
        entry.set("timings", timings);

        // cache block is commonly present
        entry.set("cache", objectMapper.createObjectNode());

        // ---- Traceability metadata ----
        entry.put("_hm_original_id", exchange.getId());
        entry.put("_hm_mutator", mutant.getMutatorClassName());
        entry.put("_hm_operator", mutant.getOperatorClassName());
        entry.put("_hm_original_json_path", mutant.getOriginalJsonPath());

        entries.add(entry);
    }

    private ObjectNode toCompatibleRequest(HttpExchange exchange) throws IOException {
        StandardHttpRequest req = exchange.getRequest();

        ObjectNode harReq;
        if (req != null) {
            try {
                JsonNode n = converter.fromStandardRequest(req);
                harReq = (n instanceof ObjectNode) ? (ObjectNode) n : objectMapper.createObjectNode();
            } catch (ConversionException e) {
                throw new IOException("Failed to convert request", e);
            }
        } else {
            harReq = objectMapper.createObjectNode();
        }

        if (!harReq.hasNonNull("method")) {
            harReq.put("method", UNKNOWN_METHOD);
        }
        if (!harReq.hasNonNull("url")) {
            // If you can derive real URL from exchange/request, set it here.
            harReq.put("url", UNKNOWN_URL);
        }
        if (!harReq.hasNonNull("httpVersion")) {
            harReq.put("httpVersion", DEFAULT_HTTP_VERSION);
        }
        if (!harReq.has("cookies") || !harReq.get("cookies").isArray()) {
            harReq.set("cookies", objectMapper.createArrayNode());
        }
        if (!harReq.has("headers") || !harReq.get("headers").isArray()) {
            harReq.set("headers", objectMapper.createArrayNode());
        }
        if (!harReq.has("queryString") || !harReq.get("queryString").isArray()) {
            harReq.set("queryString", objectMapper.createArrayNode());
        }
        if (!harReq.has("headersSize")) {
            harReq.put("headersSize", -1);
        }
        if (!harReq.has("bodySize")) {
            harReq.put("bodySize", -1);
        }

        return harReq;
    }

    private ObjectNode toCompatibleResponse(StandardHttpResponse mutatedResponse) throws IOException {
        ObjectNode harResp;
        try {
            JsonNode n = converter.fromStandardResponse(mutatedResponse);
            harResp = (n instanceof ObjectNode) ? (ObjectNode) n : objectMapper.createObjectNode();
        } catch (ConversionException e) {
            throw new IOException("Failed to convert mutated response", e);
        }

        int status = harResp.has("status") ? harResp.get("status").asInt() : 0;
        if (!harResp.hasNonNull("status")) {
            harResp.put("status", status);
        }

        if (!harResp.hasNonNull("statusText")) {
            harResp.put("statusText", defaultStatusText(status));
        }
        if (!harResp.hasNonNull("httpVersion")) {
            harResp.put("httpVersion", DEFAULT_HTTP_VERSION);
        }
        if (!harResp.has("cookies") || !harResp.get("cookies").isArray()) {
            harResp.set("cookies", objectMapper.createArrayNode());
        }
        if (!harResp.has("headers") || !harResp.get("headers").isArray()) {
            harResp.set("headers", objectMapper.createArrayNode());
        }
        if (!harResp.hasNonNull("redirectURL")) {
            harResp.put("redirectURL", "");
        }
        if (!harResp.has("headersSize")) {
            harResp.put("headersSize", -1);
        }

        // Ensure content exists and compute size if missing
        int contentSize = -1;
        if (harResp.has("content") && harResp.get("content") instanceof ObjectNode) {
            ObjectNode content = (ObjectNode) harResp.get("content");

            if (!content.hasNonNull("mimeType")) {
                content.put("mimeType", "application/octet-stream");
            }

            if (!content.has("size")) {
                if (content.hasNonNull("text")) {
                    // Approximate: chars; if you want bytes, use UTF-8 byte length.
                    contentSize = content.get("text").asText().length();
                    content.put("size", contentSize);
                } else {
                    content.put("size", 0);
                    contentSize = 0;
                }
            } else {
                contentSize = content.get("size").asInt(-1);
            }
        } else {
            ObjectNode content = objectMapper.createObjectNode();
            content.put("size", 0);
            content.put("mimeType", "application/octet-stream");
            harResp.set("content", content);
            contentSize = 0;
        }

        // Align bodySize with content.size when possible (improves viewer compatibility)
        if (!harResp.has("bodySize")) {
            harResp.put("bodySize", contentSize >= 0 ? contentSize : -1);
        } else if (harResp.get("bodySize").asInt(-1) < 0 && contentSize >= 0) {
            ((ObjectNode) harResp).put("bodySize", contentSize);
        }

        return harResp;
    }

    private static String defaultStatusText(int status) {
        switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 415: return "Unsupported Media Type";
            case 422: return "Unprocessable Entity";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default:
                if (status >= 200 && status < 300) return "OK";
                if (status >= 300 && status < 400) return "Redirect";
                if (status >= 400 && status < 500) return "Client Error";
                if (status >= 500 && status < 600) return "Server Error";
                return "";
        }
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("HarMutantWriter is already closed");
        }
        if (flushed) {
            return;
        }
        objectMapper.writeValue(out, root);
        out.flush();
        flushed = true;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        IOException first = null;

        if (!flushed) {
            try {
                flush();
            } catch (IOException e) {
                first = e;
            }
        }

        try {
            out.close();
        } catch (IOException e) {
            if (first == null) first = e;
            else first.addSuppressed(e);
        } finally {
            closed = true;
        }

        if (first != null) {
            throw first;
        }
    }
}
