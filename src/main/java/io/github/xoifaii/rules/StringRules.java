package io.github.xoifaii.rules;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

/// Rules on strings. Length rules count UTF-16 units, as String.length does. A reason never echoes the
/// string, since it may be a secret; it reports lengths and patterns instead. A regex is RE2 syntax,
/// compiled once when the rule is made, and matches in linear time so no input can make a rule run long.
public final class StringRules {

    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private StringRules() {}

    public static Rule<String> notEmpty() {
        return value -> {
            if (value.isEmpty()) {
                return Verdict.fail("must not be empty");
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> notBlank() {
        return value -> {
            if (value.isBlank()) {
                return Verdict.fail("must not be blank");
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> length(int exact) {
        requireNotNegative("length", exact);

        return value -> {
            if (value.length() != exact) {
                return Verdict.fail("length must be " + exact + ", got " + value.length());
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> lengthBetween(int min, int max) {
        requireNotNegative("min", min);
        if (max < min) {
            throw new IllegalArgumentException("max must be at least min " + min + ", got " + max);
        }

        return value -> {
            var length = value.length();
            if (length < min || length > max) {
                return Verdict.fail("length must be from " + min + " to " + max + ", got " + length);
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> minLength(int min) {
        requireNotNegative("min", min);

        return value -> {
            if (value.length() < min) {
                return Verdict.fail("length must be at least " + min + ", got " + value.length());
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> maxLength(int max) {
        requireNotNegative("max", max);

        return value -> {
            if (value.length() > max) {
                return Verdict.fail("length must be at most " + max + ", got " + value.length());
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> matches(String regex) {
        var pattern = compile(regex);

        return value -> {
            if (!pattern.matches(value)) {
                return Verdict.fail("must match " + regex);
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> containsMatch(String regex) {
        var pattern = compile(regex);

        return value -> {
            if (!pattern.matcher(value).find()) {
                return Verdict.fail("must contain a match for " + regex);
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> startsWith(String prefix) {
        return value -> {
            if (!value.startsWith(prefix)) {
                return Verdict.fail("must start with \"" + prefix + "\"");
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> endsWith(String suffix) {
        return value -> {
            if (!value.endsWith(suffix)) {
                return Verdict.fail("must end with \"" + suffix + "\"");
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> contains(String part) {
        return value -> {
            if (!value.contains(part)) {
                return Verdict.fail("must contain \"" + part + "\"");
            }

            return Verdict.pass();
        };
    }

    public static Rule<String> uuid() {
        return value -> {
            if (!UUID.matches(value)) {
                return Verdict.fail("must be a UUID");
            }

            return Verdict.pass();
        };
    }

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    "regex must be valid RE2 syntax, got " + regex + ": " + e.getMessage(), e);
        }
    }

    private static void requireNotNegative(String what, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(what + " must not be negative, got " + value);
        }
    }
}
