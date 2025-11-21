# End-to-End Example

Use this walkthrough as a template for integrating HTTPMutator into an automated test or fuzzing loop. Replace the placeholder package names and TODO sections with your actual service and scoring rules.

## 1. Start a Minimal REST API

```java
// src/test/java/example/DemoApplication.java
@RestController
public class DemoApplication {
    @GetMapping("/items/{id}")
    public ResponseEntity<Map<String, Object>> getItem(@PathVariable int id) {
        return ResponseEntity.ok(Map.of(
            "id", id,
            "status", "READY",
            "flags", List.of("NEW")
        ));
    }
}
```

Launch it with your preferred framework (`mvn spring-boot:run`, `gradle bootRun`, etc.).

## 2. Capture a Real Response

```java
Response baseline = RestAssured.given()
    .baseUri("http://localhost:8080")
    .get("/items/7");

RestAssuredBidirectionalConverter converter = new RestAssuredBidirectionalConverter();
StandardHttpResponse std = converter.toStandardResponse(baseline);

// Pin a seed so you can replay exactly the same mutants
RandomUtils.setSeed(20240901L);
```

If you already have JSON snapshots, skip REST Assured and call `StandardHttpResponse.fromJson(...)` instead.

## 3. Generate Mutants

```java
HttpMutator mutator = new HttpMutator();
MutationStrategy selector = new RandomSingleStrategy();
MutationStatistics stats = MutationStatistics.getInstance();
String testId = "items-endpoint"; // any stable identifier

mutator.mutate(std, group -> {
    List<Mutant> picked = selector.selectMutants(group);
    stats.recordBatch(testId, picked);

    picked.forEach(mutant -> {
        StandardHttpResponse mutatedStd = StandardHttpResponse.fromJsonNode(mutant.getMutatedNode());
        Response mutatedResponse = converter.fromStandardResponse(mutatedStd);
        boolean killed = replayAndCheck(mutatedResponse); // TODO(team): implement assertion logic
        recordScore(mutant, killed); // TODO(team): persist results in your scoring dashboard
    });
});
```

`MutationStatistics` can later export coverage data:

```java
stats.exportGlobalCsv(Path.of("target/mutation-global.csv"));
stats.exportDetailedCsv(Path.of("target/mutation-by-test.csv"));
stats.clear(); // reset counters before another run
```

## 4. Interpret Results

- `replayAndCheck` should run your regression tests or contract checks and return `true` when the mutant is detected (killed).
- `recordScore` is intentionally left blank so you can funnel metrics into your existing reporting pipeline.
- Consider combining survivors (`killed == false`) with bug trackers or alerting systems.

## 5. TODO Placeholders

- `TODO(team): document how to provision baseline traffic in CI`
- `TODO(team): describe mutation score formula used by your org`
- `TODO(team): outline rollback or triage steps for surviving mutants`
