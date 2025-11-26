# Output and Reporting

HttpMutator produces complete mutated HTTP responses and lightweight counters you can export to CSV. All outputs are grounded in the `StandardHttpResponse` shape.

## Response Shape
- Every mutated payload is a JSON object with `"Status Code"`, `"Headers"`, and `"Body"`.
- `Mutant.getOriginalJsonPath()` tells you which path was mutated (e.g., `Body/items/0/name`, `Headers/content-type/charset`, `Status Code`).
- `Mutant.getMutatorClassName()` and `Mutant.getOperatorClassName()` identify the mutator/operator used.

Example mutant payload:
```json
{
  "Status Code": 404,
  "Headers": {"content-type": "application/json"},
  "Body": {"id": 1, "name": "book"}
}
```

## Streaming JSONL Pipelines
`HttpMutator.mutateJsonlToJsonl(...)` reads JSONL responses and writes selected mutants as JSONL without buffering everything in memory.

```java
import es.us.isa.httpmutator.core.HttpMutator;
import es.us.isa.httpmutator.core.strategy.RandomSingleStrategy;

HttpMutator mutator = new HttpMutator();
try (Reader in = Files.newBufferedReader(Path.of("responses.jsonl"));
     Writer out = Files.newBufferedWriter(Path.of("mutants.jsonl"))) {
    mutator.mutateJsonlToJsonl(in, out, new RandomSingleStrategy(), true);
}
```

- Each input line must contain the three canonical fields.
- `includeMeta=true` adds helper fields to each output line:
  - `_hm_original_id` – copied from `id` in the original line if present.
  - `_hm_original_json_path` – path mutated.
  - `_hm_mutator` / `_hm_operator` – class names of the mutator and operator.
- If the mutated node is not an object, it is wrapped under `{"mutated": ...}` to keep JSONL well-formed.

## Mutation Statistics
Use `MutationStatistics` to count operator usage across tests or per test ID.

```java
import es.us.isa.httpmutator.core.stats.MutationStatistics;

MutationStatistics stats = MutationStatistics.getInstance();

mutator.mutate(response, group -> stats.recordBatch("test-login", group.getMutants()));

stats.exportGlobalCsv(Path.of("target/mutation-global.csv"));
stats.exportDetailedCsv(Path.of("target/mutation-detailed.csv"));
stats.clear(); // reset between suites
```

Exports:
- **Global CSV** (`mutatorName,operatorName,count`) via `exportGlobalCsv`.
- **Detailed CSV** (`testId,mutatorName,operatorName,count`) via `exportDetailedCsv`.

## Using MutantGroup Streams
- `HttpMutator.mutate(...)` delivers a `MutantGroup` per path/header/status. Apply your strategy and stop storing unused mutants to avoid memory pressure.
- Combine `MutantGroup.getIdentifier()` with `Mutant.getOriginalJsonPath()` to map killed mutants back to the original response or test step.
- `AllOperatorsStrategy` replays every mutant; `RandomSingleStrategy` picks one per group. Custom strategies can thin out large corpora before writing JSONL or executing assertions.
