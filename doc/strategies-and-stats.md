# Strategies, Random Seeds, and Statistics

This guide covers three coordination components: deterministic randomness (`RandomUtils`), mutation selection (`MutationStrategy`), and mutation accounting (`MutationStatistics`).

## 1. Control Randomness with `RandomUtils`

Set a global seed to reproduce experiments, or clear it to return to non-deterministic runs.

```java
import es.us.isa.httpmutator.core.util.RandomUtils;

// Reproduce results
RandomUtils.setSeed(12345L);
// ... run your mutator ...

// Back to non-deterministic mode
RandomUtils.clearSeed();
```

All operators and strategies that use `RandomUtils.getRandom()` or related helpers will honor this seed.

## 2. Choose Mutants with `MutationStrategy`

Implementations decide which mutants from each `MutantGroup` you want to replay.

```java
HttpMutator mutator = new HttpMutator();
MutationStrategy pickOne = new RandomSingleStrategy();
mutator.mutate(response, group -> {
    List<Mutant> replayList = pickOne.selectMutants(group);
    replayList.forEach(mutant -> replay(mutant.getMutatedNode()));
});
```

Built-in strategies:

- `AllOperatorsStrategy` – returns every mutant in the group (exhaustive runs or offline scoring).
- `RandomSingleStrategy` – picks exactly one mutant per group (lightweight online checks).

Create your own by implementing `MutationStrategy` and applying any selection rule you need (priority, coverage feedback, weighting, etc.).

## 3. Track Results with `MutationStatistics`

`MutationStatistics` is a lightweight counter of mutator/operator combinations; it avoids storing full payloads. Use it per test ID, export CSVs, then reset when done.

```java
MutationStatistics stats = MutationStatistics.getInstance();
String testId = "checkout-flow";

mutator.mutate(response, group -> {
    List<Mutant> chosen = new RandomSingleStrategy().selectMutants(group);
    stats.recordBatch(testId, chosen);
    // ...replay chosen mutants and decide which are killed...
});

// JSONL streaming with strategy
try (Reader in = Files.newBufferedReader(Path.of("baseline.jsonl"));
     Writer out = Files.newBufferedWriter(Path.of("mutants.jsonl"))) {
    mutator.mutateJsonlToJsonl(in, out, new AllOperatorsStrategy());
}

// Export counts
stats.exportGlobalCsv(Path.of("target/mutation-global.csv"));
stats.exportDetailedCsv(Path.of("target/mutation-by-test.csv"));

// Clear all counts before another run
stats.clear();
```

Statistics you can obtain:

- Global combination counts: `stats.getGlobalCombinationCounts()` returns `mutator-operator -> count`.
- Per-test counts: `stats.getCombinationCounts(testId)`.
- Unique combinations (`getUniqueCombinationCount`) and total mutant count (`getTotalMutantCount`).
