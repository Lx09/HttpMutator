package es.us.isa.httpmutator.headers;

import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import es.us.isa.httpmutator.AbstractOperator;
import es.us.isa.httpmutator.headers.charset.CharsetMutator;
import es.us.isa.httpmutator.headers.location.LocationMutator;
import es.us.isa.httpmutator.headers.mediaType.MediaTypeMutator;
import es.us.isa.httpmutator.util.OperatorNames;

public class HeaderMutator {

    private static final String CONTENT_TYPE_HEADER = "content-type";
    private static final String LOCATION_HEADER = "location";
    private static final Set<String> MEDIA_TYPE_PREFIXES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "application", "audio", "image", "message", "model", "multipart", "text", "video")));

    private final Random rand = new Random();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CharsetMutator charsetMutator;
    private MediaTypeMutator mediaTypeMutator;
    private LocationMutator locationMutator;

    public HeaderMutator() {
        resetMutators();
    }

    public List<String> getAllMutants(String jsonString) {
        return getAllMutants(jsonString, 1.0);
    }

    private void resetMutators() {
        charsetMutator = Boolean.parseBoolean(readProperty("operator.header.charset.enabled")) ? new CharsetMutator() : null;
        mediaTypeMutator = Boolean.parseBoolean(readProperty("operator.header.mediaType.enabled")) ? new MediaTypeMutator() : null;
        locationMutator = Boolean.parseBoolean(readProperty("operator.header.location.enabled")) ? new LocationMutator() : null;
    }

    /**
     * Generates mutated versions of HTTP headers in the JSON input
     *
     * @param jsonString Input JSON containing headers
     * @param probability Mutation probability for each operator
     * @return List of mutated JSON strings
     */
    public List<String> getAllMutants(String jsonString, double probability) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonString);
            List<JsonNode> nodeMutants = getAllMutants(rootNode, probability);
            return serializeMutants(nodeMutants);
        } catch (IOException e) {
            return Collections.singletonList(jsonString);
        }
    }

    // Main mutation generation logic
    public List<JsonNode> getAllMutants(JsonNode node, double probability) {
        List<JsonNode> mutants = new ArrayList<>();
        adjustMutatorsBasedOnPresence(node);
        generateContentTypeMutants(node, probability, mutants);
        generateLocationMutants(node, probability, mutants);
        return mutants;
    }

    // Adjust operators based on header presence
    private void adjustMutatorsBasedOnPresence(JsonNode node) {
        // Handle Content-Type header
        if (hasHeader(node, CONTENT_TYPE_HEADER)) {
            String contentType = getHeaderValue(node, CONTENT_TYPE_HEADER).toLowerCase();
            boolean hasMediaType = MEDIA_TYPE_PREFIXES.stream().anyMatch(contentType::startsWith);
            boolean hasCharset = contentType.contains("charset=");

            if (mediaTypeMutator != null && !hasMediaType) {
                mediaTypeMutator.getOperators().remove(OperatorNames.NULL);
            }
            if (charsetMutator != null && !hasCharset) {
                charsetMutator.getOperators().remove(OperatorNames.NULL);
            }
        } else {
            if (mediaTypeMutator != null) {
                mediaTypeMutator.getOperators().remove(OperatorNames.NULL);
            }
            if (charsetMutator != null) {
                charsetMutator.getOperators().remove(OperatorNames.NULL);
            }
        }

        // Handle Location header
        if (locationMutator != null && !hasHeader(node, LOCATION_HEADER)) {
            locationMutator.getOperators().remove(OperatorNames.NULL);
        }
    }

    // Generate mutants for Content-Type header
    private void generateContentTypeMutants(JsonNode node, double probability, List<JsonNode> mutants) {
        if (!hasHeader(node, CONTENT_TYPE_HEADER)) {
            return;
        }

        String contentType = getHeaderValue(node, CONTENT_TYPE_HEADER);
        ContentTypeComponents components = new ContentTypeComponents(contentType);

        // Generate media type mutants
        if (mediaTypeMutator != null) {
            for (AbstractOperator operator : mediaTypeMutator.getOperators().values()) {
                if (shouldSkipMutation(probability)) {
                    continue;
                }
                mutateComponent(node, mutants, components, operator, true);
            }
        }

        // Generate charset mutants
        if (charsetMutator != null) {
            for (AbstractOperator operator : charsetMutator.getOperators().values()) {
                if (shouldSkipMutation(probability)) {
                    continue;
                }
                mutateComponent(node, mutants, components, operator, false);
            }
        }
    }

    // Generate mutants for Location header
    private void generateLocationMutants(JsonNode node, double probability, List<JsonNode> mutants) {
        if (!hasHeader(node, LOCATION_HEADER) || locationMutator == null) {
            return;
        }

        String location = getHeaderValue(node, LOCATION_HEADER);
        for (AbstractOperator operator : locationMutator.getOperators().values()) {
            if (shouldSkipMutation(probability)) {
                continue;
            }

            Object mutated = operator.mutate(location);
            ObjectNode copiedNode = ((ObjectNode) node).deepCopy();
            updateHeaderField(copiedNode, LOCATION_HEADER, mutated);
            mutants.add(copiedNode);
        }
    }

    // Helper method for component mutation
    private void mutateComponent(JsonNode node, List<JsonNode> mutants,
            ContentTypeComponents components,
            AbstractOperator operator,
            boolean isMediaType) {
        String originalValue = isMediaType ? components.mediaType : components.charsetValue;
        Object mutatedValue = operator.mutate(originalValue);

        ContentTypeComponents newComponents = new ContentTypeComponents(components);
        if (isMediaType) {
            newComponents.mediaType = mutatedValue != null && !mutatedValue.toString().equals("null") ? mutatedValue.toString() : null;
        } else {
            newComponents.charsetValue = mutatedValue != null && !mutatedValue.toString().equals("null") ? mutatedValue.toString() : null;
        }

        ObjectNode copiedNode = ((ObjectNode) node).deepCopy();
        updateHeaderField(copiedNode, CONTENT_TYPE_HEADER, newComponents.buildHeaderValue());
        mutants.add(copiedNode);
    }

    // Utility class for Content-Type decomposition
    private static class ContentTypeComponents {

        String mediaType;
        String charsetValue;
        final Map<String, String> otherParams = new LinkedHashMap<>();

        ContentTypeComponents(String headerValue) {
            String[] parts = headerValue.split(";");

            // Extract media type
            this.mediaType = parts.length > 0 ? parseMediaType(parts[0].trim()) : null;

            // Process remaining parameters
            for (int i = 0; i < parts.length; i++) {
                String[] kv = parts[i].trim().split("=", 2);
                if (kv.length == 2) {
                    if (kv[0].equalsIgnoreCase("charset")) {
                        this.charsetValue = kv[1].trim();
                    } else {
                        this.otherParams.put(kv[0].trim(), kv[1].trim());
                    }
                }
            }
        }

        // Copy constructor
        ContentTypeComponents(ContentTypeComponents other) {
            this.mediaType = other.mediaType;
            this.charsetValue = other.charsetValue;
            this.otherParams.putAll(other.otherParams);
        }

        String buildHeaderValue() {
            StringBuilder sb = new StringBuilder();
            if (mediaType != null) {
                sb.append(mediaType);
            }

            if (charsetValue != null) {
                if (mediaType != null) {
                    sb.append("; ");
                }
                sb.append("charset=").append(charsetValue);
            }

            for (Map.Entry<String, String> entry : otherParams.entrySet()) {
                sb.append("; ").append(entry.getKey()).append("=").append(entry.getValue());
            }
            return sb.toString();
        }

        private String parseMediaType(String candidate) {
            return MEDIA_TYPE_PREFIXES.stream().anyMatch(candidate::startsWith) ? candidate : null;
        }
    }

    // Utility methods
    private boolean shouldSkipMutation(double probability) {
        return rand.nextFloat() >= probability;
    }

    private void updateHeaderField(ObjectNode node, String headerName, Object value) {
        String actualFieldName = getHeaderFieldName(node, headerName);
        if (value == null || value.toString().equals("null") || value.toString().isEmpty()) {
            node.remove(actualFieldName);
        } else {
            node.put(actualFieldName, value.toString());
        }
    }

    private String getHeaderFieldName(JsonNode node, String headerName) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (field.equalsIgnoreCase(headerName)) {
                return field;
            }
        }
        return headerName; // For new headers
    }

    private boolean hasHeader(JsonNode node, String headerName) {
        return node.fieldNames().hasNext()
                && node.fieldNames().next().equalsIgnoreCase(headerName);
    }

    private String getHeaderValue(JsonNode node, String headerName) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (field.equalsIgnoreCase(headerName)) {
                return node.get(field).asText();
            }
        }
        return "";
    }

    private List<String> serializeMutants(List<JsonNode> mutants) {
        return mutants.stream()
                .map(node -> {
                    try {
                        return objectMapper.writeValueAsString(node);
                    } catch (JsonProcessingException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
