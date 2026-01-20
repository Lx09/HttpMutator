<h1 align="left">
  <img src="docs/logo.jpeg" alt="HttpMutator" width="220"/><br/>
  HttpMutator
</h1>

HttpMutator is a black-box mutation testing tool for web APIs that generates faulty yet realistic variants of HTTP responses to assess the fault-detection capability of API testing tools and test oracles.

Unlike traditional mutation testing that injects faults into source code, HttpMutator **mutates observable HTTP response elements — status codes, headers, and JSON payloads** — to simulate the effects of functional bugs in the underlying implementation. The resulting mutated responses can be replayed against existing test suites or API testing tools, making the approach applicable even **without access to the source code**.

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
## Quickstart

Minimal mutation example:
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

        HttpMutator mutator = new HttpMutator()
                .withMutationStrategy(new RandomSingleStrategy());

        List<StandardHttpResponse> mutants = mutator.mutate(response);
        for (StandardHttpResponse mutated : mutants) {
            System.out.println(mutated.toJsonString());
        }
    }
}
```

## Command-line (CLI) usage
HttpMutator ships with a simple CLI for offline/batch mutation of recorded HTTP traffic (JSONL or HAR). It reads exchanges from a file, mutates the responses, and writes mutants to output files.

Getting started:
```bash
mvn -pl httpmutator-core -am package
java -jar httpmutator-core/target/httpmutator.jar --help
```

Minimal example (default JSONL output):
```bash
java -jar httpmutator-core/target/httpmutator.jar \
  -i httpmutator-core/src/test/resources/httpmutatorInput.jsonl \
  -o hm-output \
  -s random
```

Common flags: `-i/--input`, `-f/--format`, `-o/--output`, `-s/--strategy`, `--writeHar`, `--writeJsonl`, `--reporter csv`.

For the full list of CLI options, see [docs/cli.md](docs/cli.md).

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
