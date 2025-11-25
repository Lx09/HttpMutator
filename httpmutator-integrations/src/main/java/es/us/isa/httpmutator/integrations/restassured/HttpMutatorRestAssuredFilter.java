package es.us.isa.httpmutator.integrations.restassured;

import es.us.isa.httpmutator.core.HttpMutator;
import es.us.isa.httpmutator.core.converter.ConversionException;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;
import es.us.isa.httpmutator.core.strategy.AllOperatorsStrategy;
import es.us.isa.httpmutator.core.strategy.MutationStrategy;
import es.us.isa.httpmutator.core.util.RandomUtils;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class HttpMutatorRestAssuredFilter implements Filter {
    private final HttpMutator httpMutator;
    private final MutationStrategy mutationStrategy;
    private final Path reportDir;

    private final List<RecordedInteraction> recordedInteractions = new ArrayList<>();
    private Response lastResponse;

    public HttpMutatorRestAssuredFilter(
            long randomSeed,
            MutationStrategy mutationStrategy,
            Path reportDir
    ) {
        this.httpMutator = new HttpMutator();
        RandomUtils.setSeed(randomSeed);
        this.mutationStrategy = mutationStrategy;
        this.reportDir = reportDir;
    }

    public HttpMutatorRestAssuredFilter() {
        this(42L, new AllOperatorsStrategy(), defaultReportDir());
    }

    private static Path defaultReportDir() {
        return Paths.get("target", "httpmutator-restassured");
    }

    private static final class RecordedInteraction {

        private final StandardHttpResponse originalResponse;
        private final Consumer<ValidatableResponse> assertFuncs;

        RecordedInteraction(StandardHttpResponse originalResponse, Consumer<ValidatableResponse> assertFuncs) {
            this.originalResponse = originalResponse;
            this.assertFuncs = assertFuncs;
        }

        StandardHttpResponse getOriginalResponse() {
            return originalResponse;
        }

        Consumer<StandardHttpResponse> getAssertFuncs() {
            return assertFuncs;
        }
    }


    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {

        Response response = ctx.next(requestSpec, responseSpec);

        this.lastResponse = response;

        return response;
    }

    public void addAssertionsForLastRequest(Consumer<ValidatableResponse> assertions) throws ConversionException {
        if (lastResponse == null) {
            throw new IllegalStateException(
                    "No response captured yet. Make sure a RestAssured request was executed before calling addAssertionsForLastRequest"
            );
        }
        if (assertions == null) {
            throw new IllegalStateException("Assertions must not be null");
        }

        StandardHttpResponse standardHttpResponse = RestAssuredBidirectionalConverter.INSTANCE.toStandardResponse(lastResponse);
        recordedInteractions.add(new RecordedInteraction(standardHttpResponse, assertions));
        lastResponse = null;
    }

    public List<MutationResult> runAllMutations() {
        if (httpMutator == null) {
            throw new IllegalStateException("HttpMutatorFilter not initialized with a HTTPMutator");
        }

        List<MutationResult> results = new ArrayList<>();
        for (int i = 0; i < recordedInteractions.size(); i++) {
            RecordedInteraction interaction = recordedInteractions.get(i);
            String label = "request-" + i;
            MutationResult r = runMutationForRecord(label, interaction.originalResponse, interaction.assertFuncs);
        }
    }

    private MutationResult runMutationForRecord(String label, StandardHttpResponse std, Consumer<ValidatableResponse> assertFunc) {
        AtomicInteger total = new AtomicInteger();
        AtomicInteger killed = new AtomicInteger();
        httpMutator.mutate(std, mutantGroup -> {
            mutationStrategy.selectMutants(mutantGroup).forEach(mutant -> {
                StandardHttpResponse stdMResp = StandardHttpResponse.fromJsonNode(mutant.getMutatedNode());
                ValidatableResponse valMResp;
                try {
                    Response resp = RestAssuredBidirectionalConverter.INSTANCE.fromStandardResponse(stdMResp);
                    valMResp = resp.then();
                } catch (ConversionException e) {
                    throw new RuntimeException(e);
                }

                total.getAndIncrement();

                try {
                    assertFunc.accept(valMResp);
                } catch (AssertionError e) {
                    killed.getAndIncrement();
                }
            });
        });

        return new MutationResult(label, total.get(), killed.get());
    }
}
