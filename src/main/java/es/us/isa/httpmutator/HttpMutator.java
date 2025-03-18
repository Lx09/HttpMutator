package es.us.isa.httpmutator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import es.us.isa.httpmutator.body.BodyMutator;
import es.us.isa.httpmutator.headers.HeaderMutator;
import es.us.isa.httpmutator.sc.StatusCodeMutator;
import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

public class HttpMutator {
    private static final Logger logger = LogManager.getLogger(HttpMutator.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StatusCodeMutator statusCodeMutator;
    private HeaderMutator headerMutator;
    private BodyMutator bodyMutator;

    public HttpMutator() {
        resetMutators();
    }

    private void resetMutators() {
        statusCodeMutator = Boolean.parseBoolean(readProperty("operator.sc.enabled")) ? new StatusCodeMutator() : null;
        headerMutator = Boolean.parseBoolean(readProperty("operator.header.enabled")) ? new HeaderMutator() : null;
        bodyMutator = Boolean.parseBoolean(readProperty("operator.body.enabled")) ? new BodyMutator() : null;
    }

    public List<String> getAllMutants(String response, double possiblity) {
        JsonNode responseNode;
        try {
            responseNode = objectMapper.readTree(response);
        } catch (IOException e) {
            logger.warn("Error parsing response: " + e.getMessage());
            return null;
        }
        List<JsonNode> nodeMutants = getAllMutants(responseNode, possiblity);
        return nodeMutants.stream().map(n -> {
                try {
                    return objectMapper.writeValueAsString(n);
                } catch (JsonProcessingException e) {
                    logger.warn("Some mutant could not be transformed to a string.");
                    return null;
                }
            })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
    }

    public List<JsonNode> getAllMutants(JsonNode node, double possiblity) {
        if (!isValidResponse(node)) {
            logger.warn("Response must inculde status code, headers, and body");
            return null;
        }

        List<JsonNode> mutants = new ArrayList<>();

        if (statusCodeMutator != null) {
            int statusCode = node.get("Status Code").asInt();
            List<Integer> statusCodeMutants = statusCodeMutator.getAllMutants(statusCode, possiblity);
            for (Integer statusCodeMutant : statusCodeMutants) {
                JsonNode copied = node.deepCopy();
                ((ObjectNode) copied).put("Status Code", statusCodeMutant);
                mutants.add(copied);
            }
        }

        if (headerMutator != null) {
            JsonNode headers = node.get("Headers");
            List<JsonNode> headerMutants = headerMutator.getAllMutants(headers, possiblity);
            for (JsonNode headerMutant : headerMutants) {
                JsonNode copied = node.deepCopy();
                ((ObjectNode) copied).set("Headers", headerMutant);
                mutants.add(copied);
            }
        }

        if (bodyMutator != null) {
            JsonNode body = node.get("Body");
            List<JsonNode> bodyMutants = bodyMutator.getAllMutants(body, possiblity);
            for (JsonNode bodyMutant : bodyMutants) {
                JsonNode copied = node.deepCopy();
                ((ObjectNode) copied).set("Body", bodyMutant);
                mutants.add(copied);
            }
        }
        return mutants;
    }

    private boolean isValidResponse(JsonNode node) {
        return node.isObject() && node.has("Status Code") && node.get("Status Code").isInt() && node.has("Headers") && node.get("Headers").isObject() && node.has("Body");
    }

    public List<String> getAllMutants(String jsonString) {
        return getAllMutants(jsonString, 1.0);
    }
}
