# Extending HTTPMutator

This guide explains how to introduce new mutation operators, adjust configuration, and plug additional response formats into the framework without forcing users to read the accompanying research paper. Follow the checklist below whenever you need to extend the library.

## 1. Adding a New Mutation Operator

1. **Name it** – add a constant to `src/main/java/es/us/isa/httpmutator/core/util/OperatorNames.java`. Use descriptive camelCase names (e.g., `TRUNCATE`, `injectFault`).
2. **Expose configuration knobs** – append properties to `src/main/resources/json-mutation.properties`. Common fields include:
   ```properties
   operator.value.string.weight.injectFault=0.2
   operator.value.string.injectFault.maxLength=32
   ```
3. **Implement the operator class** – extend `AbstractOperator`, read your configuration in the constructor (call `readProperty`), and implement `doMutate`. Override `isApplicable` when the operator only works for specific shapes.
4. **Register the operator** – update the corresponding mutator (e.g., `StringMutator`) to `operators.put(OperatorNames.INJECT_FAULT, new StringInjectFaultOperator());`.
5. **Reload properties** – restart your harness or call `PropertyManager.resetProperties()` if you need the new settings during runtime tests.

## 2. Updating Probabilities and Weights

- `prob` fields in each mutator constructor read from `operator.<scope>.prob`. Lower values make the mutator fire less often when traversing the JSON tree.
- Individual operator weights (`operator.value.string.weight.replace`, etc.) determine which operator is chosen when multiple ones are available for the same node.
- Structural mutators have additional knobs (e.g., `operator.object.addedElements.min/max`, `operator.array.mutations.max`) that dictate how many nested changes are applied per visit.

Tweak these numbers to align HTTPMutator with your fuzzing/test budget. For deterministic test suites, keep probabilities at `1.0` and adjust weights to bias toward interesting operators.

## 3. Plugging in New Response Formats

The engine works on `StandardHttpResponse`. When you need to mutate a proprietary response type:

1. Implement `BidirectionalConverter<T>` where `T` is your response class.
2. Convert incoming responses with `toStandardResponse` before passing them to `EnhancedHttpMutator`.
3. Optionally implement `fromStandardResponse` so you can feed mutated payloads back into the original abstraction (e.g., a mock HTTP response object).

Use `RestAssuredBidirectionalConverter` and `WebScarabBidirectionalConverter` as templates.

## 4. Contributing Utilities

If you need helper classes (new statistics exporters, alternative randomness providers, etc.), keep them under `core/util` or `core/stats` depending on their purpose. Make sure they do not introduce external dependencies that are not already declared in `pom.xml`.

## 5. Checklist Before Opening a PR

- [ ] Unit or integration tests cover the new operator/converter (consider placing them under `src/test/java/...`).
- [ ] `json-mutation.properties` documents every configuration key you introduced.
- [ ] README links or references are updated if the new feature affects onboarding.
- [ ] TODO placeholders below can be filled with your organisation-specific process notes:
  - `TODO(team): describe review workflow for new operators`
  - `TODO(team): capture internal QA sign-off steps`
