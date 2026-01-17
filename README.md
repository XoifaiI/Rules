# Rules

A zero dependency Java validation library that actually works in production. Ships with a patched fork of RE2J for regex so you don't have to worry about ReDoS attacks blowing up your servers.

## What This Actually Does

Most validation libraries are either too simple to be useful or so bloated they become a liability. Rules sits in the middle which is where you want to be. You get type safe validation rules that compose together cleanly and a security first design that won't let bad actors crash your system with crafted inputs.

The core idea is straightforward. You define rules, chain them together, and validate data. When something fails you get back a result telling you what went wrong. No exceptions flying around unless you explicitly want them.

```java
Rule<String> username = Rules.all(
    StringRules.notBlank(),
    StringRules.lengthBetween(3, 20),
    StringRules.matches("^[a-zA-Z0-9_]+$")
);

ValidationResult result = username.validate("jack_dev");
if (result.isValid()) {
    // good to go
}
```

## Why Bother

Standard Java validation tends to fall apart when you need actual security guarantees. This library was built with adversarial inputs in mind so it handles things that others don't:

- **HashDoS Protection** which means your HashMaps won't become linked lists when someone sends crafted keys. The SecureHashMap uses SipHash-2-4 with random keys so collision attacks don't work.

- **ReDoS Prevention** since the bundled RE2J fork guarantees linear time matching. Regular Java regex can hang forever on evil patterns but this won't. The fork includes patches for vulnerabilities found in the original Google implementation.

- **Timing Attack Resistance** with constant time comparison functions for secrets. Comparing passwords or tokens the normal way leaks information through timing differences.

- **Cycle Detection** so self referential data structures don't cause stack overflows during validation.

- **Timeout Support** which lets you cap how long validation can run. Useful when validating untrusted data that might be designed to waste resources.

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.xoifaii</groupId>
    <artifactId>rules</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Manual

Clone the repo and compile:

```bash
# Windows
build.bat

# Or manually
mkdir bin
javac -d bin src/main/java/re2j/*.java src/main/java/rules/*.java src/main/java/testing/*.java
```

Add the bin directory to your classpath and you're done.

## Documentation

Full API documentation lives in the [Wiki](../../wiki). That covers everything from basic usage to advanced patterns like struct validation and custom rule composition.

## Core Concepts

### Rules

A rule is just a function that takes a value and returns valid or invalid. The interface is minimal:

```java
@FunctionalInterface
public interface Rule<T> {
    ValidationResult validate(T value);
}
```

You can write rules inline as lambdas or use the built in ones. Either way they compose the same.

### Results

ValidationResult tells you what happened. It's either valid or invalid with a message explaining why. There's also a system error state for when validation itself fails due to resource limits or cycles.

```java
ValidationResult result = rule.validate(input);

if (result.isValid()) {
    proceed();
} else {
    log(result.messageOrDefault("validation failed"));
}
```

### Composition

Rules chain together with `and`, `or`, and `negate`. The `Rules` class has `all`, `any`, and `none` for combining multiple rules at once.

```java
Rule<String> secure = Rules.all(
    StringRules.minLength(12),
    StringRules.matches("[A-Z]"),
    StringRules.matches("[a-z]"),
    StringRules.matches("[0-9]"),
    StringRules.matches("[^a-zA-Z0-9]")
);
```

### Stateful Validation

When you need protection against malicious inputs use ValidationContext. It tracks depth, detects cycles, enforces size limits, and supports timeouts.

```java
ValidationContext ctx = ValidationContext.builder()
    .maxDepth(32)
    .maxCollectionSize(10_000)
    .cycleDetection(true)
    .timeout(Duration.ofSeconds(5))
    .build();

ValidationResult result = ctx.validateTyped(data, rule);
```

## Available Rules

### Strings
`notNull`, `notEmpty`, `notBlank`, `length`, `lengthBetween`, `minLength`, `maxLength`, `matches`, `matchesExactly`, `matchesPattern`, `uuidV4`, `startsWith`, `endsWith`, `contains`

### Numbers
`finite`, `notNaN`, `isNaN`, `isInfinite`, `integer`, `positive`, `negative`, `nonNegative`, `nonPositive`, `between`, `betweenExclusive`, `min`, `max`, `closeTo`

### Objects
`notNull`, `isNull`, `instanceOf`, `optional`, `oneOf`, `validEnum`, `equalTo`, `notEqualTo`, `secureEquals`, `secureEqualsBytes`

### Collections
`notEmpty`, `size`, `sizeBetween`, `minSize`, `maxSize`, `allMatch`, `anyMatch`, `noneMatch`, `contains`, `mapNotEmpty`, `mapSize`, `allKeys`, `allValues`, `containsKey`

### Arrays
`notNull`, `notEmpty`, `length`, `lengthBetween`, `minLength`, `maxLength`, `allElements`, `anyElement`, `noElements`, `noNullElements`, `contains`, `secureEquals`, `validRange`, `isArrayOf`, `isRectangular`

Primitive array variants exist for `byte[]`, `int[]`, `long[]`, and `double[]` with type specific checks like `bytesInRange`, `intsNonNegative`, `doublesFinite`, and `doublesNotNaN`.

### Structs

For validating map shaped data with known fields:

```java
Rule<Map<String, Object>> userRule = StructRule.<String, Object>builder()
    .field("name", v -> StringRules.notBlank().validate((String) v))
    .field("age", v -> NumberRules.between(0, 150).validate((Number) v))
    .optionalField("email", v -> StringRules.contains("@").validate((String) v))
    .strict()
    .toRule();
```

## Security Features

### SecureHashMap

Drop in replacement for HashMap that resists hash collision attacks. Uses SipHash-2-4 with per instance random keys.

```java
Map<String, Object> safe = new SecureHashMap<>();
safe.put("key", value);
```

### Constant Time Comparison

For comparing secrets without leaking timing information:

```java
Rule<String> tokenRule = ObjectRules.secureEquals(expectedToken);
Rule<byte[]> keyRule = ArrayRules.secureEquals(expectedKey);
```

### Patched RE2J

The included RE2J fork has fixes for vulnerabilities in the original Google implementation. It guarantees O(n) matching time which means no regex can cause exponential blowup regardless of the pattern or input.

## Running Tests

```bash
# All tests
test.bat

# Just rules tests
test.bat rules

# Just regex tests
test.bat re2j
```

## Project Structure

```
src/main/java/
  re2j/       # Patched RE2J regex engine
  rules/      # Validation library
  testing/    # Test framework
```

## License

MIT
