package es.us.isa.httpmutator;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.us.isa.httpmutator.stats.OperatorUsageStats;

public class HttpMutatorTest {
    private JsonNode jsonNode = null;
    private String jsonString = null;
    private ObjectMapper objectMapper;

    private final HttpMutator httpMutator = new HttpMutator();

     @Before
    public void setUp() {
        objectMapper = new ObjectMapper();

        // Read JSON file and create JSON node and JSON string
        try {
            jsonNode = objectMapper.readTree(new String(Files.readAllBytes(Paths.get("src/test/resources/httpResponse.json"))));
            jsonString = objectMapper.writeValueAsString(jsonNode);
        } catch (IOException e) {
            System.out.println("Unable to read JSON");
            e.printStackTrace();
            fail();
        }

    }

    @Test
    public void testGetAllMutants() {
        String response1 = "{\n" + //
                "  \"Status Code\": 200,\n" + //
                "  \"Headers\": {\n" + //
                "    \"Content-Type\": \"application/json\",\n" + //
                "    \"Cache-Control\": \"max-age=3600\"\n" + //
                "  },\n" + //
                "  \"Body\": {\n" + //
                "    \"data\": {\n" + //
                "      \"id\": 123,\n" + //
                "      \"name\": \"Example Resource\",\n" + //
                "      \"status\": \"active\"\n" + //
                "    }\n" + //
                "  }\n" + //
                "}";
        System.out.println("Test case with valid response" + response1);
        for (String mutant : httpMutator.getAllMutants(response1)) {
            System.out.println("mutant: " + mutant);
        }
    }

    @Test
    public void testReadWorldCase() {
        System.out.println("Original JSON: " + jsonString);
        List<String> mutants = httpMutator.getAllMutants(jsonString);
        System.out.println("Number of Mutants: " + mutants.size());
        System.out.println();
        System.out.println("Output Operator Usage...");
        System.out.println(OperatorUsageStats.getInstance().toString());

        // sum of all operator usages
        int sum = OperatorUsageStats.getInstance().getStats().values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("Sum of all operator usages: " + sum);
        assertEquals(sum, mutants.size());
    }
}
