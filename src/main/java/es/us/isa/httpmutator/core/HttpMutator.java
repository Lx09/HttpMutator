package es.us.isa.httpmutator.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.httpmutator.core.model.Mutant;
import es.us.isa.httpmutator.core.model.MutantGroup;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;
import es.us.isa.httpmutator.core.strategy.MutationStrategy;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

public class HttpMutator {
    private final HttpMutatorEngine engine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpMutator() {
        this.engine = new HttpMutatorEngine();
        // set random seed
        // RandomUtils.setSeed(42L);
    }

    /**
     * Main Interface：take StandardHttpResponse as input
     */
    public void mutate(StandardHttpResponse response, Consumer<MutantGroup> consumer) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        JsonNode node = response.toJsonNode();
        engine.getAllMutants(node, consumer);
    }

    /**
     * Mutate a JsonNode representing a full HTTP response.
     * Node must contain "Status Code", "Headers", and "Body".
     */
    public void mutate(String responseJson, Consumer<MutantGroup> consumer) {
        Objects.requireNonNull(responseJson, "responseJson must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        engine.getAllMutants(responseJson, consumer);
    }

    /**
     * Mutate a JsonNode representing a full HTTP response.
     * Node must contain "Status Code", "Headers", and "Body".
     */
    public void mutate(JsonNode node, Consumer<MutantGroup> consumer) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        // For raw JsonNode we assume: mutate all 3 components
        engine.getAllMutants(node, consumer);
    }

    /**
     * Read JSONL from reader, each non-blank line is one response JSON, and stream mutants.
     */
    public void mutateJsonl(Reader reader, Consumer<MutantGroup> consumer) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        try (BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader)) {

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                mutate(line, consumer);
            }
        }
    }

    /**
     * Reads responses from a JSONL stream, applies mutation (optionally filtered by
     * a MutationStrategy), and writes each mutated response back as JSONL.
     *
     * <p>This method is fully streaming and can handle very large inputs without
     * accumulating mutants in memory. Mutants are written in batches using an
     * internal buffer to significantly reduce IO overhead when the number of
     * mutants is large (hundreds of thousands or more).
     *
     * @param reader   JSONL input, where each non-blank line is a full HTTP response JSON
     * @param writer   JSONL output, where each line is a mutated HTTP response JSON
     * @param strategy optional mutation strategy for selecting which mutants to output
     * @throws IOException if an underlying IO operation fails
     */
    public void mutateJsonlToJsonl(Reader reader, Writer writer, MutationStrategy strategy) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        Objects.requireNonNull(writer, "writer must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        final StringBuilder buffer = new StringBuilder(1_048_576); // 1 MB buffer
        final int flushThreshold = 1_048_576;

        try {
            // Stream input JSONL → produce mutant groups → write selected mutants
            mutateJsonl(reader, mutantGroup -> {
                // Apply the user-defined (or default) strategy to select mutants
                for (Mutant mutant : strategy.selectMutants(mutantGroup)) {
                    JsonNode mutatedJson = mutant.getMutatedNode();
                    try {
                        // Serialize each mutated response into the buffer
                        buffer.append(objectMapper.writeValueAsString(mutatedJson)).append('\n');

                        // Flush the buffer when exceeding the threshold
                        if (buffer.length() >= flushThreshold) {
                            writer.write(buffer.toString());
                            buffer.setLength(0);
                        }
                    } catch (IOException e) {
                        // Checked exception cannot propagate from inside lambda
                        throw new UncheckedIOException(e);
                    }
                }
            });

            // Flush remaining buffered output at the end
            if (buffer.length() > 0) {
                writer.write(buffer.toString());
            }

        } catch (UncheckedIOException e) {
            // Convert back into a checked IOException to preserve method signature
            throw e.getCause();
        }
    }

    /**
     * Convenience method for mutating an input JSONL file and writing the resulting
     * mutants to an output JSONL file. Both input and output are processed in a
     * fully streaming fashion, allowing very large mutation sets to be handled
     * without accumulating them in memory.
     *
     * @param inputJsonl  path to the input JSONL file where each line is a single response
     * @param outputJsonl path to the output JSONL file where each line will be a mutated response
     * @param strategy    optional mutation strategy used to select which mutants to output
     * @throws IOException if an underlying IO operation fails
     */
    public void mutateJsonlToJsonl(Path inputJsonl, Path outputJsonl, MutationStrategy strategy) throws IOException {

        Objects.requireNonNull(inputJsonl, "inputJsonl must not be null");
        Objects.requireNonNull(outputJsonl, "outputJsonl must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        try (Reader reader = Files.newBufferedReader(inputJsonl); Writer writer = Files.newBufferedWriter(outputJsonl)) {

            mutateJsonlToJsonl(reader, writer, strategy);
        }
    }
}
