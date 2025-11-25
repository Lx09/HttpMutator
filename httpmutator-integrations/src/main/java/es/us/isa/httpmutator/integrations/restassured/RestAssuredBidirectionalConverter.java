// ========================================
// REST Assured Response Converter
// ========================================
package es.us.isa.httpmutator.integrations.restassured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.httpmutator.core.converter.BidirectionalConverter;
import es.us.isa.httpmutator.core.converter.ConversionException;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;
import io.restassured.builder.ResponseBuilder;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestAssuredBidirectionalConverter implements BidirectionalConverter<Response> {

    private final ObjectMapper objectMapper;

    public RestAssuredBidirectionalConverter() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "RestAssuredResponse";
    }

    @Override
    public boolean supports(Class<?> responseType) {
        return Response.class.isAssignableFrom(responseType);
    }

    @Override
    public StandardHttpResponse toStandardResponse(Response response) throws ConversionException {
        try {
            // Directly build StandardHttpResponse
            return StandardHttpResponse.of(response.getStatusCode(), extractHeaders(response), parseResponseBody(response));
        } catch (Exception e) {
            throw new ConversionException("Failed to convert REST Assured Response", e);
        }
    }

    private Map<String, Object> extractHeaders(Response response) {
        Map<String, Object> headers = new HashMap<>();
        response.getHeaders().forEach(header ->
                headers.put(header.getName(), header.getValue())
        );
        return headers;
    }

    private JsonNode parseResponseBody(Response response) {
        try {
            return objectMapper.readTree(response.getBody().asString());
        } catch (Exception e) {
            // If not JSON, wrap as text node
            return objectMapper.valueToTree(response.getBody().asString());
        }
    }

    @Override
    public Response fromStandardResponse(StandardHttpResponse std) throws ConversionException {
        // Create a mock Response using REST Assured's internal classes
        try {
            /* ---------- 1. Headers ---------- */
            List<Header> headerList = new ArrayList<>();
            String contentType = null;

            if (std.getHeaders() != null) {
                for (Map.Entry<String, Object> entry : std.getHeaders().entrySet()) {
                    String headerName = entry.getKey();
                    String headerValue = String.valueOf(entry.getValue());

                    // Check for Content-Type header (case-insensitive)
                    if ("content-type".equalsIgnoreCase(headerName)) {
                        contentType = headerValue;
                    }

                    headerList.add(new Header(headerName, headerValue));
                }
            }
            Headers headers = new Headers(headerList);

            /* ---------- 2. Body ---------- */
            // JsonNode ➜ String（Rest‑Assured only need string）
            String bodyAsString = std.getBody() == null ? "" : std.getBody().toString();

            /* ---------- 3. Build Response ---------- */
            ResponseBuilder builder = new ResponseBuilder();
            builder.setStatusCode(std.getStatusCode());
            builder.setStatusLine("HTTP/1.1 " + std.getStatusCode()); // optional, used in many cases
            builder.setHeaders(headers);
            builder.setBody(bodyAsString);

            // Explicitly set Content-Type if found
            if (contentType != null) {
                builder.setContentType(contentType);
            }

            return builder.build();

        } catch (Exception e) {
            throw new ConversionException("Failed to convert StandardHttpResponse to Response", e);
        }
    }

}