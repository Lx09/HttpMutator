<h1 align="left">
  <img src="docs/logo.jpeg" alt="HttpMutator" width="220"/><br/>
  HttpMutator
</h1>

HttpMutator is a black-box mutation testing tool for web APIs that generates faulty yet realistic variants of HTTP responses to assess the fault-detection capability of API testing tools and test oracles.

Unlike traditional mutation testing that injects faults into source code, HttpMutator mutates observable response elements — status codes, headers, and JSON payloads — to simulate the effects of functional bugs in the underlying implementation. The resulting mutated responses can be replayed against existing test suites or API testing tools, making the approach applicable to both closed- and open-source APIs.

Use cases:
- **Assessing API testing tools in black-box settings** by replaying mutated HTTP responses to measure how effectively tools detect incorrect outputs beyond crashes and specification violations.

- **Evaluating and comparing test oracles** (e.g., regression oracles, specification-based checks, invariant-based assertions) using the same set of mutated responses and computing mutation scores.

- **Integrating response mutation into existing workflows** that already record and exchange HTTP traffic (e.g., HAR artifacts), enabling offline evaluation pipelines without access to API source code.

## Install

### Build from source
Java Development Kit (JDK) 8 or later and Apache Maven are required.  

```bash
mvn clean install -DskipTests
```

This builds all modules and installs `1.0-SNAPSHOT` artifacts to your local Maven repository.

### Use from another Maven project (local SNAPSHOT)
Core library:
```xml
<dependency>
  <groupId>es.us.isa.httpmutator</groupId>
  <artifactId>httpmutator-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

REST Assured integration:
```xml
<dependency>
  <groupId>es.us.isa.httpmutator</groupId>
  <artifactId>httpmutator-integrations</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
<!-- rest-assured is marked provided in the module -->
<dependency>
  <groupId>io.rest-assured</groupId>
  <artifactId>rest-assured</artifactId>
  <version>5.4.0</version>
</dependency>
```

## Quickstart

Minimal in-memory mutation:
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.httpmutator.core.HttpMutator;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;
import es.us.isa.httpmutator.core.strategy.RandomSingleStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicExample {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        StandardHttpResponse response = StandardHttpResponse.of(
                200,
                headers,
                mapper.readTree("{\"id\":1,\"name\":\"book\"}")
        );

        HttpMutator mutator = new HttpMutator(1234L)
                .withMutationStrategy(new RandomSingleStrategy());

        List<StandardHttpResponse> mutants = mutator.mutate(response);
        for (StandardHttpResponse mutated : mutants) {
            System.out.println(mutated.toJsonString());
        }
    }
}
```

## How it works 

1. Normalize each response into a consistent internal format (status code, headers, response body (JSON)).

2. Propose mutations for the status code, headers, and JSON payload (each mutation changes one observable part).

3. Organize mutations by location (e.g., “this header” or “this JSON field”) so they can be sampled independently.

4. Select mutations using a strategy (random sampling or exhaustive, depending on cost).

5. Materialize mutated responses by applying the selected mutations to the original response.

6. Export mutants to files (e.g., JSONL/HAR) or pass them directly to in-memory consumers.

7. Optionally collect summaries (counts, mutation operator usage) via reporters.

## API surface
Key entry points:
- `HttpMutator`: main facade for in-memory and streaming mutation.
- `HttpMutatorEngine`: core mutation logic (used internally, but stable).
- `MutationStrategy` (e.g., `RandomSingleStrategy`, `AllOperatorsStrategy`) for sampling mutations grouped by location.
- `JsonlExchangeReader` / `HarExchangeReader` for streaming input.
- `JsonlMutantWriter` / `HarMutantWriter` for outputs.
- `HttpMutatorRestAssuredFilter` for REST Assured integration.

## Configuration
HttpMutator is configurable: you can enable/disable mutation categories and tune value ranges used by certain operators.
Defaults are loaded from `httpmutator-core/src/main/resources/json-mutation.properties` via `PropertyManager`.

Common adjustments:
- Enable/disable mutation categories: status code, headers, and JSON body.
- Tune numeric and string ranges used by value-level operators (e.g., min/max length, min/max numeric values).
- Enable/disable specific header-related mutations (e.g., media type, charset).

Programmatic override:
```java
import es.us.isa.httpmutator.core.util.PropertyManager;

PropertyManager.setProperty("operator.body.enabled", "true");
PropertyManager.setProperty("operator.value.string.length.max", "256");
```
Reset to defaults:

```java
import es.us.isa.httpmutator.core.util.PropertyManager;

PropertyManager.resetProperties();
```

## Outputs

Supported outputs:
- JSONL files containing mutated responses (`JsonlMutantWriter`).
- HAR files containing mutated responses (`HarMutantWriter`).
- Optional compressed JSONL shards for large corpora (`ShardedZstdJsonlMutantWriter`).

To make each mutated response traceable, writers can attach a few extra fields
(prefixed with `_hm_`). These fields are **not part of the original API response**;
they are bookkeeping information added by HttpMutator:

- `_hm_original_id`: identifier of the original response (so you can group mutants back to their source).
- `_hm_original_json_path`: location in the response that was mutated.
- `_hm_mutator`: the component that performed the mutation (e.g., status-code / header / body mutator).
- `_hm_operator`: the specific mutation operator applied (e.g., “ArrayDisorderElementsOperator”, “ArrayAddElementOperator”, etc.).

For format details, see `docs/output-and-reporting.md`.

## Documentation (Where to go next)
Detailed guides live in `docs/`:
- `docs/mutation-operators.md`
- `docs/extending-httpmutator.md`
- `docs/restassured-integration.md`
- `docs/output-and-reporting.md`

TODO (docs not present in repo):
- `docs/extending-mutation-operators.md`
- `docs/input-output-formats.md`
- `docs/rest-assured-integration.md` (current file is `docs/restassured-integration.md`)
