# HTTPMutator

HTTPMutator is a Java library that mutates HTTP responses (status code, headers, JSON body) so you can fuzz REST APIs, stress-test clients, or measure mutation scores. The engine walks normalized responses, applies weighted operators, and streams `MutantGroup` objects so you can replay or discard mutants as they are produced. `HttpMutator` is the public entry point; it wraps the internal `HttpMutatorEngine`.

## What HTTPMutator Does

- **Streaming engine** – `EnhancedHttpMutator` processes status codes, headers, and bodies without keeping every mutant in memory. Each JSON path (or header component) yields its own `MutantGroup`.
- **Rich operator suite** – 38 operators span status-code swaps, header rewrites, structural JSON changes, and scalar replacements. Enable, disable, or re-weight them via `json-mutation.properties`.
- **Flexible integration** – `StandardHttpResponse` is the canonical input; built-in `BidirectionalConverter` implementations normalize REST Assured objects, WebScarab transcripts, or raw JSON.
- **Statistics friendly** – `MutationStatistics` records which operators fired per test; `MutationStrategy` implementations (`RandomSingleStrategy`, `AllOperatorsStrategy`, or your own) decide how many mutants to replay.
- **Reproducible runs** – `RandomUtils.setSeed(...)` pins a global seed so you can rerun the same sequence of mutants.

## Repository Layout

```
README.md                          ← this overview
doc/                               ← focused guides (operators, extensions, examples)
pom.xml                            ← Maven configuration
 src/main/java/es/us/isa/httpmutator/core
  ├─ HttpMutator.java              ← public facade
  ├─ HttpMutatorEngine.java        ← internal engine
  ├─ body / headers / sc           ← all operators and mutators
  ├─ converter                     ← bidirectional converters (e.g., REST Assured, WebScarab)
  └─ util / stats / strategy       ← helpers, statistics, selection strategies
src/main/resources/json-mutation.properties
                                   ← operator weights/probabilities
HTTP_Mutator.pdf                   ← background paper (for reference only)
```

## Supported HTTP Response Formats & Converters

All components expect a `StandardHttpResponse` (status code, headers map, Jackson `JsonNode` body). Use the converters below to get there:

| Source format | Converter | Notes |
| --- | --- | --- |
| Already-normalized JSON | `StandardHttpResponse.fromJson(String)` or `.fromJsonNode(JsonNode)` | Ensure the JSON object contains `\"Status Code\"`, `\"Headers\"`, and `\"Body\"`. |
| REST Assured `io.restassured.response.Response` | `RestAssuredBidirectionalConverter` | Parses headers/body and can rebuild `Response` instances for replay. |
| WebScarab/plain HTTP text | `WebScarabBidirectionalConverter` | Reads HTTP wire format (status line + headers + body) and recreates it from a `StandardHttpResponse`. |

Implement `BidirectionalConverter<T>` if you need additional adapters (e.g., custom client SDKs).

## Quick Start

1. **Build & test**
   ```bash
   mvn clean package
   mvn test
   ```
   Requires Maven 3.8+ and JDK 8+ (the compiler target is 1.8).

2. **Normalize and mutate a response**
   ```java
   HttpMutator mutator = new HttpMutator();
   // Convert whichever format you have into the canonical StandardHttpResponse
   RestAssuredBidirectionalConverter converter = new RestAssuredBidirectionalConverter();
   Response restAssuredResponse = RestAssured.given()
       .baseUri("http://localhost:8080")
       .get("/item/1");
   StandardHttpResponse response = converter.toStandardResponse(restAssuredResponse);

   // Reproduce runs by fixing the seed
   RandomUtils.setSeed(12345L);

   MutationStrategy selector = new RandomSingleStrategy();
   mutator.mutate(response, group ->
       selector.selectMutants(group).forEach(mutant ->
           System.out.printf("%s via %s -> %s%n",
               mutant.getOriginalJsonPath(),
               mutant.getOperatorClassName(),
               mutant.getMutatedNodeAsString())
       )
   );
   ```
   Lower the probability argument (e.g., `0.6`) if you want fewer mutations per traversal.

3. **Track statistics or stream to JSONL**
   ```java
   MutationStatistics stats = MutationStatistics.getInstance();
   mutator.mutate(response, group -> stats.recordBatch("sample-test", group.getMutants()));
   stats.exportGlobalCsv(Path.of("target/mutation-global.csv"));
   stats.clear(); // reset counters before another experiment

   // Stream JSONL → JSONL with a strategy
   try (Reader in = Files.newBufferedReader(Path.of(\"baseline.jsonl\"));\n        Writer out = Files.newBufferedWriter(Path.of(\"mutants.jsonl\"))) {\n       mutator.mutateJsonlToJsonl(in, out, new RandomSingleStrategy());\n   }\n    ```
   ```

## Working With Mutations

- `json-mutation.properties` controls which components are enabled (`operator.sc.enabled`, `operator.header.enabled`, `operator.body.enabled`), how often each mutator fires (`operator.value.string.prob`, etc.), and the ranges/deltas used by replacement operators.
- Docs for specific topics:
  - [`doc/mutation-operators.md`](doc/mutation-operators.md) – operator catalog (apply conditions and effects).
  - [`doc/strategies-and-stats.md`](doc/strategies-and-stats.md) – seeds, selection strategies, and counters.
  - [`doc/extending-httpmutator.md`](doc/extending-httpmutator.md) – adding operators, tweaking configuration, wiring new converters.
  - [`doc/end-to-end-example.md`](doc/end-to-end-example.md) – complete scenario: start a REST API, capture a response, mutate it, and compute scores.

Bring your own scoring logic by combining `MutantGroup`s with existing regression tests, contract checkers, or fuzzing harnesses. Use the TODO placeholders in the docs to capture organisation-specific rules so contributors know what remains to be filled in.

## Flow at a Glance

```
Source response (REST Assured / WebScarab / JSON)
          │
          │  BidirectionalConverter (RestAssuredBidirectionalConverter, WebScarabBidirectionalConverter, or custom)
          ▼
    StandardHttpResponse
          │
  │  HttpMutator → HttpMutatorEngine (uses operator weights + RandomUtils seed)
          ▼
      MutantGroup stream
          │
          │  MutationStrategy (selects mutants to replay)
          ▼
      Replayed mutants → MutationStatistics (counts/export/reset)
```
