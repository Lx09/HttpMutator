package es.us.isa.httpmutator.core;

import com.fasterxml.jackson.databind.JsonNode;
import es.us.isa.httpmutator.core.model.HttpExchange;
import es.us.isa.httpmutator.core.model.Mutant;
import es.us.isa.httpmutator.core.model.MutantGroup;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;
import es.us.isa.httpmutator.core.reader.HttpExchangeReader;
import es.us.isa.httpmutator.core.reporter.MutantReporter;
import es.us.isa.httpmutator.core.strategy.MutationStrategy;
import es.us.isa.httpmutator.core.util.RandomUtils;
import es.us.isa.httpmutator.core.writer.MutantWriter;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * High-level orchestration class for running HttpMutator.
 */
public class HttpMutator {

    private final HttpMutatorEngine engine;

    /** Strategy for selecting which mutants to keep. */
    private MutationStrategy strategy;

    /** Optional writers that emit mutated responses. */
    private final List<MutantWriter> writers = new ArrayList<>();

    /** Optional reporters that collect statistics / metrics. */
    private final List<MutantReporter> reporters = new ArrayList<>();

    /** Random seed used by mutation utilities. */
    private long randomSeed;

    public HttpMutator() {
        this(42L);
    }

    public HttpMutator(long randomSeed) {
        this.engine = new HttpMutatorEngine();
        this.randomSeed = randomSeed;
        RandomUtils.setSeed(randomSeed);
    }

    public HttpMutator withMutationStrategy(MutationStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
        return this;
    }

    public HttpMutator withWriters(List<MutantWriter> writers) {
        this.writers.clear();
        if (writers != null) {
            this.writers.addAll(writers);
        }
        return this;
    }

    public HttpMutator addWriter(MutantWriter writer) {
        if (writer != null) {
            this.writers.add(writer);
        }
        return this;
    }

    public HttpMutator withReporters(List<MutantReporter> reporters) {
        this.reporters.clear();
        if (reporters != null) {
            this.reporters.addAll(reporters);
        }
        return this;
    }

    public HttpMutator addReporter(MutantReporter reporter) {
        if (reporter != null) {
            this.reporters.add(reporter);
        }
        return this;
    }

    public HttpMutator withRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
        RandomUtils.setSeed(randomSeed);
        return this;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public List<MutantWriter> getWriters() {
        return Collections.unmodifiableList(writers);
    }

    public List<MutantReporter> getReporters() {
        return Collections.unmodifiableList(reporters);
    }

    // ===================== core: engine + strategy =====================

    private void ensureStrategyConfigured() {
        if (strategy == null) {
            throw new IllegalStateException("MutationStrategy must be configured via withMutationStrategy(...)");
        }
    }

    /**
     * Single place that calls engine + strategy and returns each selected mutant.
     */
    private void forEachSelectedMutant(JsonNode responseNode, Consumer<MutantGroup> groupConsumer) {
        Objects.requireNonNull(responseNode, "responseNode must not be null");
        ensureStrategyConfigured();
        engine.getAllMutants(responseNode, groupConsumer::accept);
    }

    // ===================== Streaming API (reader + writers + reporters) =====================

    public void mutateStream(HttpExchangeReader exchangeReader, Reader in) throws IOException {
        Objects.requireNonNull(exchangeReader, "exchangeReader must not be null");
        Objects.requireNonNull(in, "in must not be null");
        ensureStrategyConfigured();

        exchangeReader.read(in, this::processExchangeStream);

        notifyFinished();
    }

    private void processExchangeStream(HttpExchange exchange) {
        StandardHttpResponse original = exchange.getResponse();
        JsonNode responseNode = original.toJsonNode();

        forEachSelectedMutant(responseNode, group -> {
            for (Mutant mutant : strategy.selectMutants(group)) {
                JsonNode mutatedNode = mutant.getMutatedNode();
                StandardHttpResponse mutated = StandardHttpResponse.fromJsonNode(mutatedNode);

                // writers
                for (MutantWriter writer : writers) {
                    try {
                        writer.write(exchange, mutated, mutant);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write mutated response", e);
                    }
                }

                // reporters
                for (MutantReporter reporter : reporters) {
                    reporter.onMutant(exchange, mutated, mutant);
                }
            }
        });
    }

    private void notifyFinished() throws IOException {
        for (MutantReporter reporter : reporters) {
            reporter.onFinished();
        }
    }

    // ===================== In-memory: StandardHttpResponse → List =====================

    public List<StandardHttpResponse> mutate(StandardHttpResponse original) {
        Objects.requireNonNull(original, "original must not be null");
        ensureStrategyConfigured();

        List<StandardHttpResponse> results = new ArrayList<>();
        HttpExchange exchange = new HttpExchange(null, original, "in-memory");
        JsonNode responseNode = original.toJsonNode();

        forEachSelectedMutant(responseNode, group -> {
            for (Mutant mutant : strategy.selectMutants(group)) {
                StandardHttpResponse mutated =
                        StandardHttpResponse.fromJsonNode(mutant.getMutatedNode());
                results.add(mutated);

                for (MutantReporter reporter : reporters) {
                    reporter.onMutant(exchange, mutated, mutant);
                }
            }
        });

        return results;
    }

    // ===================== In-memory: JsonNode → List<JsonNode> =====================

    public List<JsonNode> mutate(JsonNode canonicalResponseNode) {
        Objects.requireNonNull(canonicalResponseNode, "canonicalResponseNode must not be null");
        ensureStrategyConfigured();

        StandardHttpResponse original =
                StandardHttpResponse.fromJsonNode(canonicalResponseNode);
        HttpExchange exchange = new HttpExchange(null, original, "in-memory");

        List<JsonNode> results = new ArrayList<>();

        forEachSelectedMutant(canonicalResponseNode, group -> {
            for (Mutant mutant : strategy.selectMutants(group)) {
                JsonNode mutatedNode = mutant.getMutatedNode();
                results.add(mutatedNode);

                StandardHttpResponse mutated =
                        StandardHttpResponse.fromJsonNode(mutatedNode);

                for (MutantReporter reporter : reporters) {
                    reporter.onMutant(exchange, mutated, mutant);
                }
            }
        });

        return results;
    }

    // ===================== In-memory streaming: StandardHttpResponse =====================

    public void mutate(StandardHttpResponse original,
                       Consumer<StandardHttpResponse> consumer) {

        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");
        ensureStrategyConfigured();

        HttpExchange exchange = new HttpExchange(null, original, "in-memory");
        JsonNode responseNode = original.toJsonNode();

        forEachSelectedMutant(responseNode, group -> {
            for (Mutant mutant : strategy.selectMutants(group)) {
                StandardHttpResponse mutated =
                        StandardHttpResponse.fromJsonNode(mutant.getMutatedNode());

                consumer.accept(mutated);

                for (MutantReporter reporter : reporters) {
                    reporter.onMutant(exchange, mutated, mutant);
                }
            }
        });
    }

    // ===================== In-memory streaming: JsonNode =====================

    public void mutate(JsonNode canonicalResponseNode,
                       Consumer<JsonNode> consumer) {

        Objects.requireNonNull(canonicalResponseNode, "canonicalResponseNode must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");
        ensureStrategyConfigured();

        StandardHttpResponse original =
                StandardHttpResponse.fromJsonNode(canonicalResponseNode);
        HttpExchange exchange = new HttpExchange(null, original, "in-memory");

        forEachSelectedMutant(canonicalResponseNode, group -> {
            for (Mutant mutant : strategy.selectMutants(group)) {
                JsonNode mutatedNode = mutant.getMutatedNode();

                consumer.accept(mutatedNode);

                StandardHttpResponse mutated =
                        StandardHttpResponse.fromJsonNode(mutatedNode);

                for (MutantReporter reporter : reporters) {
                    reporter.onMutant(exchange, mutated, mutant);
                }
            }
        });
    }
}