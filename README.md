# Rules

A small validation library for Java 26. A rule is one check on a value; rules join with `and`, `or`,
`all`, `any` and `none`; a struct rule describes the shape of a map. A rule answers a `Verdict`, which
is either `Passed` or `Failed` with a path and a reason, so the caller can say exactly which field or
element refused and why.

```java
Rule<String> username = Rules.all(
        StringRules.notBlank(),
        StringRules.lengthBetween(3, 20),
        StringRules.matches("[a-zA-Z0-9_]+"));

switch (username.check("jack dev")) {
    case Verdict.Passed passed -> proceed();
    case Verdict.Failed failed -> reject(failed.message());   // "must match [a-zA-Z0-9_]+"
}
```

## What it does

- **Reasons say what was expected and what arrived.** `length must be from 3 to 20, got 2`. A reason
  never echoes a string, since it may be a secret; it reports lengths and patterns.
- **Paths for nested data.** A struct failure reads `address.city: must not be blank`; a list failure
  reads `scores[2]: must be positive, got -1`.
- **Null is settled at the door.** Rules take non null values, and the package is `@NullMarked`. The one
  place null is allowed is `Rules.optional(rule)`, which passes null and checks everything else.
- **Regex in linear time.** `StringRules.matches` and `containsMatch` use RE2 syntax through
  [RE2J](https://github.com/google/re2j), so no input can make a pattern run long. A pattern that does
  not parse is refused when the rule is made.
- **Bounded work on untrusted input.** Put a size rule before an element rule:
  `Rules.all(CollectionRules.maxSize(10_000), CollectionRules.each(item))`.
- **Numbers compared without rounding.** A bound given as a `long` compares integral values exactly and
  floating values as floating; a bound given as a `double` compares everything as a double. NaN fails
  every bound.
- **Secrets compared in constant time.** `Rules.secureEquals` for strings and byte arrays.

## Structs

```java
Struct address = Struct.of(
        Field.required("city", Rules.ofType(String.class, StringRules.notBlank())),
        Field.optional("postcode", Rules.ofType(String.class, StringRules.length(7))));

Struct player = Struct.of(
        Field.required("name", Rules.ofType(String.class, StringRules.lengthBetween(3, 20))),
        Field.required("level", Rules.ofType(Number.class, NumberRules.between(1, 100))),
        Field.optional("address", Struct.nested(address)))
    .strict();   // refuses fields that were not named
```

A required field must be present with a value. An optional field is checked only when present. A value
of the wrong type fails with `level: must be a Number, got String`.

## Rules

| class | rules |
|---|---|
| `Rules` | `all`, `any`, `none`, `when`, `optional`, `equalTo`, `oneOf`, `ofType`, `secureEquals` |
| `Rule` | `and`, `or`, `not`, `on` (check a part of a larger value) |
| `StringRules` | `notEmpty`, `notBlank`, `length`, `lengthBetween`, `minLength`, `maxLength`, `matches`, `containsMatch`, `startsWith`, `endsWith`, `contains`, `uuid` |
| `NumberRules` | `between`, `min`, `max`, `positive`, `notNegative`, `negative`, `finite`, `whole` |
| `CollectionRules` | `notEmpty`, `size`, `sizeBetween`, `minSize`, `maxSize`, `each`, `any`, `none` |
| `MapRules` | `notEmpty`, `maxSize`, `keys`, `values` |
| `Struct` | `of`, `strict`, `nested`, with `Field.required` and `Field.optional` |

A bad argument to a rule factory, a negative length or a max below its min, is misuse and throws
`IllegalArgumentException` where the rule is made. A refusal at check time is never an exception.

## Build

```
./gradlew check
```

Gradle 9 with the Kotlin DSL on a JDK 26 toolchain. `check` compiles with `-Xlint:all -Werror`, Error
Prone and NullAway at error, verifies formatting with palantir-java-format, and runs the tests under
JUnit 6 with AssertJ.

## Installation

```kotlin
implementation("io.github.xoifaii:rules:2.0.0")
```

The module name is `io.github.xoifaii.rules`. RE2J is an implementation detail and never appears in a
public signature.

## License

MIT
