# Mutation Operators & Selection Strategies

HTTPMutator ships 38 operator classes that mutate HTTP status codes, headers, and JSON bodies. Operators are exposed through each `*Mutator` via `getOperators()`, and their weights are configured in `src/main/resources/json-mutation.properties`. This reference summarises when each operator fires, where it lives, and what effect it has. Use it together with `OperatorNames` to enable, disable, or re-wire behaviour.

## 1. Status Code Operators

| Operator (package) | Apply condition | Effect |
| --- | --- | --- |
| `StatusCodeReplacementWith20XOperator` (`core/sc/operator`) | Input contains a `"Status Code"` integer | Replaces the status code with another 20x code (`200`, `201`, or `204`) different from the original. |
| `StatusCodeReplacementWith40XOperator` | Same as above | Moves the status code into the 4xx bucket (`400`, `401`, `403`, `404`, `405`, `409`, `422`). |
| `StatusCodeReplacementWith50XOperator` | Same as above | Swaps the status code with a 5xx alternative (`500`, `502`, `503`). |

All three are registered inside `StatusCodeMutator` and can be individually weighted via `operator.sc.weight.*` properties.

## 2. Header Operators

`HeaderMutator` delegates to dedicated mutators once the relevant header exists. Null operators remove the header/parameter entirely and are automatically excluded when the header is missing.

| Operator | Apply condition | Effect |
| --- | --- | --- |
| `MediaTypeReplacementOperator` (`headers/mediaType`) | `Content-Type` header present with a media-type token | Replaces the media type with one of `application/json`, `application/xml`, `text/plain`, `text/html`, `text/css`, `text/javascript`, or `application/x-www-form-urlencoded`. |
| `NullOperator` (MediaType) | `Content-Type` header present | Removes the media type portion while leaving other parameters (e.g., charset) intact. |
| `CharsetReplacementOperator` (`headers/charset`) | `Content-Type` header has `charset=` parameter | Rewrites the charset using canonical variants (UTF-8/16/32, ISO-8859-15, Latin-9). |
| `NullOperator` (Charset) | Charset parameter present | Drops the `charset=` parameter entirely. |
| `LocationMutationOperator` (`headers/location`) | `Location` header present | Appends a random suffix to the path, keeping scheme, host, query, and fragment untouched. |
| `NullOperator` (Location) | `Location` header present | Removes the header. |

Weights live under `operator.header.<component>.weight.*` settings.

## 3. Body Structure Operators (Objects & Arrays)

### ObjectMutator

| Operator | Apply condition | Effect |
| --- | --- | --- |
| `ObjectAddElementOperator` | Target node is an object | Adds 1–4 randomly typed properties (`randomLong*`, `randomObject*`, etc.). |
| `ObjectRemoveElementOperator` | Object has ≥1 property | Removes 1–4 random properties. |
| `ObjectRemoveObjectTypeElementOperator` | Object contains nested objects | Removes randomly chosen object-valued properties. |
| `NullOperator` | Object node (except the very first root-level pass) | Replaces object with `null`. |
| `ChangeTypeOperator` | Object node | Converts node into a different type (array, string, number, etc.). |

### ArrayMutator

| Operator | Apply condition | Effect |
| --- | --- | --- |
| `ArrayAddElementOperator` | Target node is an array | Appends randomly typed elements (numbers, booleans, strings, objects, arrays). |
| `ArrayRemoveElementOperator` | Array size above removal threshold | Removes 1–4 random positions. |
| `ArrayDisorderElementsOperator` | Array has ≥2 elements | Removes an element and reinserts it at another index (shuffle). |
| `ArrayEmptyOperator` | Array non-empty | Clears all elements. |
| `NullOperator` | Array node (except first-level pass) | Replaces array with `null`. |
| `ChangeTypeOperator` | Array node | Converts array to another JSON type. |

Structural weights are declared under `operator.object.*` and `operator.array.*` keys.

## 4. Scalar Value Operators

### Strings (`body/value/string0`)

| Operator | Apply condition | Effect |
| --- | --- | --- |
| `StringReplacementOperator` | String node | Replaces the entire string with a random value respecting `length.min/max` and inclusion flags. |
| `StringAddSpecialCharactersMutationOperator` | String node (empty allowed) | Inserts tokens such as `/*`, `*,`, or `/` to stress parsers. |
| `StringMutationOperator` *(disabled by default)* | String node | Adds/removes/replaces a single character. Uncomment the operator registration in `StringMutator` to enable it. |
| `StringBoundaryOperator` | String node | Swaps with min-length, max-length, uppercase, lowercase, or empty boundary cases. |
| `NullOperator`, `ChangeTypeOperator` | String node | Converts to `null` or different type (number, array, object, etc.). |

### Longs & Doubles

| Type | Operator | Notes |
| --- | --- | --- |
| Long | `LongReplacementOperator` | Generates a random integer between `operator.value.long.min/max`. |
| Long | `LongMutationOperator` *(disabled by default)* | Adds or subtracts `operator.value.long.delta`. |
| Double | `DoubleReplacementOperator` | Generates a random double in the configured range. |
| Double | `DoubleMutationOperator` *(disabled by default)* | Adds/subtracts `operator.value.double.delta`. |
| Long/Double | `NullOperator`, `ChangeTypeOperator` | Convert to `null` or a different JSON type. |

### Booleans & Nulls

| Type | Operator | Effect |
| --- | --- | --- |
| Boolean | `BooleanMutationOperator` | Flips the boolean value. |
| Boolean | `NullOperator`, `ChangeTypeOperator` | Convert to `null` or another type. |
| Null | `ChangeTypeOperator` | Turns `null` into another type (number, string, object, array). |

All scalar weights follow `operator.value.<type>.weight.*` conventions.

For how to pick which mutants to replay (strategies) and how to collect results, see `doc/strategies-and-stats.md`.
