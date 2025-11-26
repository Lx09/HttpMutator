# Extending HttpMutator

HttpMutator is built around small, pluggable pieces: operators mutate values, mutators assemble operators, strategies pick which mutants to execute, and converters normalize external responses. This guide shows how to add your own pieces using the real extension points in the codebase.

## Add a Mutation Operator
Implement `AbstractOperator` and register it in an existing mutator (or a custom one). Override `doMutate` and optionally `isApplicable`.

```java
import es.us.isa.httpmutator.core.AbstractOperator;

// Example: replace any string with a fixed marker
public final class RedactStringOperator extends AbstractOperator {
    public RedactStringOperator() { setWeight(0.5f); }

    @Override
    protected Object doMutate(Object element) {
        return "[REDACTED]";
    }

    @Override
    public boolean isApplicable(Object element) {
        return element instanceof String;
    }
}
```

To make the operator take effect, add it to a mutator before running mutations:
```java
import es.us.isa.httpmutator.core.body.value.string0.StringMutator;

StringMutator mutator = new StringMutator();
mutator.getOperators().put("redact", new RedactStringOperator());
```

## Create a Mutation Strategy
`MutationStrategy` decides which mutants from a `MutantGroup` are executed.

```java
import es.us.isa.httpmutator.core.model.Mutant;
import es.us.isa.httpmutator.core.model.MutantGroup;
import es.us.isa.httpmutator.core.strategy.MutationStrategy;

public final class FirstOnlyStrategy implements MutationStrategy {
    @Override
    public List<Mutant> selectMutants(MutantGroup group) {
        return group.getMutants().isEmpty()
                ? List.of()
                : List.of(group.getMutants().get(0));
    }
}
```

Pass it into `HttpMutator.mutate(...)` or `mutateJsonlToJsonl(...)` to control output volume.

## Add a Converter
Adapters implement `BidirectionalConverter<T>` to map between `StandardHttpResponse` and your client/server type.

```java
import es.us.isa.httpmutator.core.converter.BidirectionalConverter;
import es.us.isa.httpmutator.core.converter.ConversionException;
import es.us.isa.httpmutator.core.model.StandardHttpResponse;

public final class MyClientConverter implements BidirectionalConverter<MyClientResponse> {
    @Override
    public StandardHttpResponse toStandardResponse(MyClientResponse original) throws ConversionException {
        // populate status, headers, and body JsonNode
    }

    @Override
    public MyClientResponse fromStandardResponse(StandardHttpResponse standard) throws ConversionException {
        // rebuild your client response
    }

    @Override
    public String getName() { return "MyClient"; }

    @Override
    public boolean supports(Class<?> responseType) {
        return MyClientResponse.class.isAssignableFrom(responseType);
    }
}
```

## Tune or Swap Mutators
- Mutator probabilities, operator weights, and numeric ranges live in `json-mutation.properties`. Call `HttpMutatorEngine.setProperty(...)`/`resetProperties()` (or re-instantiate `HttpMutator`) to apply changes.
- Body structure mutators derive from `AbstractObjectOrArrayMutator`; you can subclass `ObjectMutator`/`ArrayMutator` or adjust their `operators` maps before mutation begins.

## Lifecycle Expectations
- Operators are chosen according to their `weight` when a mutator decides to mutate a value.
- `MutantGroup` objects are emitted path-by-path (e.g., `Body/user/email`, `Headers/content-type/mediaType`); strategies should be prepared to handle empty groups.
- The JSONL pipeline (`mutateJsonlToJsonl`) calls your strategy for every `MutantGroup` and writes only the chosen mutants, so custom strategies directly control output size.
