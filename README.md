<h1 align="left">
  <img src="docs/logo.jpeg" alt="HttpMutator" width="220"/><br/>
  HttpMutator
</h1>

HttpMutator mutates HTTP responses (status code, headers, JSON body) to fuzz REST clients and measure mutation scores. It walks normalized responses, applies weighted operators, and streams `MutantGroup` batches so tests can decide which mutants to execute.

## Features
- **Streaming mutation engine** – `HttpMutator` emits mutants per JSON path/header/status without storing everything in memory.
- **Converters included** – `StandardHttpResponse` is the canonical model with adapters for REST Assured (`RestAssuredBidirectionalConverter`) and WebScarab/plain HTTP text.
- **Selection strategies** – Use `RandomSingleStrategy`, `AllOperatorsStrategy`, or custom `MutationStrategy` implementations to pick mutants.
- **Statistics ready** – `MutationStatistics` counts operator usage and exports CSV summaries.
- **JSONL pipeline** – `mutateJsonlToJsonl(...)` streams huge response corpora with optional mutation metadata.

## Installation

Maven (core library):
```xml
<dependency>
  <groupId>es.us.isa.httpmutator</groupId>
  <artifactId>httpmutator-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

Maven (REST Assured integration):
```xml
<dependency>
  <groupId>es.us.isa.httpmutator</groupId>
  <artifactId>httpmutator-integrations</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<!-- RestAssured is marked provided; add it if your project does not already depend on it -->
<dependency>
  <groupId>io.rest-assured</groupId>
  <artifactId>rest-assured</artifactId>
  <version>5.4.0</version>
</dependency>
```

Or build from source:
```bash
mvn clean package
```

## Quick Start

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.httpmutator.core.HttpMutator;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;
import es.us.isa.httpmutator.core.strategy.MutationStrategy;
import es.us.isa.httpmutator.core.strategy.RandomSingleStrategy;
import es.us.isa.httpmutator.core.util.RandomUtils;

import java.util.Map;

public class BasicExample {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StandardHttpResponse response = StandardHttpResponse.of(
                200,
                Map.of("content-type", "application/json"),
                mapper.readTree("{\"id\":1,\"name\":\"book\"}")
        );

        HttpMutator mutator = new HttpMutator(1234L); // seeds RandomUtils
        MutationStrategy selector = new RandomSingleStrategy();

        mutator.mutate(response, group ->
                selector.selectMutants(group).forEach(mutant ->
                        System.out.printf("%s via %s -> %s%n",
                                mutant.getOriginalJsonPath(),
                                mutant.getOperatorClassName(),
                                mutant.getMutatedNodeAsString())
                )
        );

        // Reset randomness if you want non-deterministic runs later
        RandomUtils.clearSeed();
    }
}
```

## Core Concepts
- `StandardHttpResponse` – canonical response (`"Status Code"`, `"Headers"`, `"Body"`).
- `Mutant` / `MutantGroup` – one mutated response and the batch of mutants for a given path.
- `MutationStrategy` – chooses which mutants to execute (`RandomSingleStrategy`, `AllOperatorsStrategy`, or custom).
- `BidirectionalConverter` – adapters to/from external response types.
- `json-mutation.properties` – operator toggles, weights, and ranges.

See the docs for deeper dives:
- [`docs/restassured-integration.md`](docs/restassured-integration.md)
- [`docs/extending-httpmutator.md`](docs/extending-httpmutator.md)
- [`docs/output-and-reporting.md`](docs/output-and-reporting.md)

## Limitations / Notes
- The RestAssured filter is single-threaded and keeps in-memory interaction logs; use one filter instance per test run.
- Mutation probabilities and ranges are driven entirely by `json-mutation.properties`; hot changes apply only after re-instantiating mutators.
- `mutateJsonlToJsonl` expects each line to contain `"Status Code"`, `"Headers"`, and `"Body"`; non-object bodies must be plain JSON values.
