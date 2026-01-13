# Output and Reporting

- [Output and Reporting](#output-and-reporting)
  - [Response model](#response-model)
  - [Optional `_hm_*` metadata (traceability)](#optional-_hm_-metadata-traceability)
  - [Outputs](#outputs)
    - [JSONL](#jsonl)
    - [HAR](#har)
    - [Zstd-sharded JSONL](#zstd-sharded-jsonl)
  - [Minimal example using `mutate(...)`](#minimal-example-using-mutate)
  - [Reporting](#reporting)
    - [CsvReporter](#csvreporter)
  - [Common pitfalls](#common-pitfalls)


HttpMutator separates mutation generation from mutation consumption. It produces outputs so users can handle large datasets, run offline analysis, and integrate with external pipelines. Reporting summarizes what was generated and provides traceability without requiring internal knowledge.

## Response model

HttpMutator operates on an original response and produces mutated responses where exactly one part changes. It uses `StandardHttpResponse` as a normalized model that is independent of input source (HAR, JSONL, or in-memory objects).

The serialized shape is a single JSON object with three top-level fields:

```json
{
  "Status Code": 200,
  "Headers": {"content-type": "application/json"},
  "Body": {"id": 1, "name": "book"}
}
```

Assumptions:
- `Headers` is a JSON object.
- `Body` is a JSON value (object, array, string, number, or null).
- This is a normalized data record, not a live HTTP object.

## Optional `_hm_*` metadata (traceability)

Metadata is optional and writer-dependent. It exists for traceability and is not required for mutation correctness.

Fields that may appear:
- `_hm_original_id`: identifier of the original response.
- `_hm_original_json_path`: location that was mutated.
- `_hm_mutator`: mutator class name.
- `_hm_operator`: operator class name.

Behavior by output type:
- JSONL output can include these fields when metadata is enabled.
- HAR output includes these fields for each entry.
- Zstd-sharded JSONL includes only `_hm_original_id`.

Zstd-sharded JSONL keeps only `_hm_original_id` because it is the join key back to the original corpus while keeping each record small.

## Outputs

HttpMutator supports multiple output formats: JSONL, HAR, and Zstd-sharded JSONL. Outputs are produced by writer components; the public extension point is `MutantWriter`.

The output layer is extensible by implementing `MutantWriter` and registering it via `HttpMutator.addWriter(...)` or `HttpMutator.withWriters(...)`.

The following table summarizes output choices:

| Goal | Recommended format | Why |
| --- | --- | --- |
| Manual inspection of a small sample | HAR | Many tools can open it directly. |
| Large-scale offline analysis / pipeline processing | JSONL | Easy to stream and process line-by-line. |
| Archival of very large corpora with minimal storage/I-O | Zstd-sharded JSONL | Compressed, sharded, and built for scale. |

### JSONL

JSONL is designed for scalable, line-delimited pipelines and offline analysis.

How it works: each mutated response is written as one JSON object per line, which makes the output streamable and easy to batch-process.

When to use it:
- Large corpora that require streaming or batch processing.
- Workflows that need simple line-based ingestion.

Trade-offs:
- Metadata increases size if enabled.

Extending: provide a custom `MutantWriter` when a different line-oriented format is required.

### HAR

HAR is designed for compatibility with existing HAR-based tooling.

How it works: mutated responses are emitted as HAR entries so downstream tools can consume the results without conversion.

When to use it:
- Workflows that already rely on HAR viewers or parsers.
- Compatibility with existing tooling is more important than scale.

Trade-offs:
- Higher memory usage and less suitable for very large datasets.

Extending: implement a writer that adapts outputs to a different tool-specific format.

### Zstd-sharded JSONL

Zstd-sharded JSONL is optimized for very large mutation corpora where storage and I-O become bottlenecks.

How it works: it combines Zstd compression with sharding so output remains compact and files stay manageable at scale.

When to use it:
- Archival of very large corpora.
- Batch processing where inspection is secondary to throughput and storage efficiency.

Trade-offs:
- Direct inspection (grep, quick opens) is less convenient.
- Metadata is intentionally minimal.

Extending: implement a writer that applies a different compression or sharding policy.

## Minimal example using `mutate(...)`

`mutate(...)` takes a normalized response and returns a list of mutated responses selected by the configured strategy.

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

## Reporting

Reporting provides summaries of mutation activity during generation and supports export without parsing full outputs. Reporting is independent of output formats.

Reporters implement `MutantReporter` and are registered via `HttpMutator.addReporter(...)` or `HttpMutator.withReporters(...)`.

### CsvReporter

`CsvReporter` provides a CSV summary of mutation activity.

Output: a CSV written to the path provided by the caller.

Use cases:
- Compact summaries for quick review.
- Export for downstream analysis tools that accept CSV.

The following example enables `CsvReporter` and generates mutants via `mutate(...)`.

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import es.us.isa.httpmutator.core.HttpMutator;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;
import es.us.isa.httpmutator.core.reporter.CsvReporter;
import es.us.isa.httpmutator.core.strategy.RandomSingleStrategy;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportingExample {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        StandardHttpResponse response = StandardHttpResponse.of(
                200,
                headers,
                mapper.readTree("{\"id\":1,\"name\":\"book\"}")
        );

        try (HttpMutator mutator = new HttpMutator(1234L)
                .withMutationStrategy(new RandomSingleStrategy())
                .addReporter(new CsvReporter(Paths.get("report.csv")))) {
            List<StandardHttpResponse> mutants = mutator.mutate(response);
            for (StandardHttpResponse mutated : mutants) {
                System.out.println(mutated.toJsonString());
            }
        }
    }
}
```

## Common pitfalls

- If field names do not match, the JSONL reader rejects the line. Ensure producers use `"Status Code"`, `"Headers"`, and `"Body"`.
- Top-level boolean bodies are not supported; `Body` must be object, array, string, number, or null.
- Header names are treated case-insensitively in mutation logic.
- Metadata may overwrite existing keys; if the input already contains `_hm_*`, writers overwrite them.
