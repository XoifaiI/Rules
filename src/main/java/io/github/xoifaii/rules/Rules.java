package io.github.xoifaii.rules;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Rules that hold for any type, and the combinators that join rules. Static only. Secret comparison
/// never echoes the value in its reason and runs in constant time over the expected length.
public final class Rules {

    private Rules() {}

    @SafeVarargs
    @SuppressWarnings("varargs") // the array is copied into an immutable list and never written
    public static <T> Rule<T> all(Rule<? super T>... rules) {
        List<Rule<? super T>> list = List.of(rules);
        return value -> {
            for (Rule<? super T> rule : list) {
                var verdict = rule.check(value);
                if (verdict instanceof Verdict.Failed) {
                    return verdict;
                }
            }

            return Verdict.pass();
        };
    }

    @SafeVarargs
    @SuppressWarnings("varargs") // the array is copied into an immutable list and never written
    public static <T> Rule<T> any(Rule<? super T>... rules) {
        if (rules.length == 0) {
            throw new IllegalArgumentException("any needs at least one rule, got none");
        }
        List<Rule<? super T>> list = List.of(rules);

        return value -> {
            var first = list.get(0).check(value);
            if (first instanceof Verdict.Passed) {
                return first;
            }
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).check(value) instanceof Verdict.Passed) {
                    return Verdict.pass();
                }
            }

            return first;
        };
    }

    @SafeVarargs
    @SuppressWarnings("varargs") // the array is copied into an immutable list and never written
    public static <T> Rule<T> none(Rule<? super T>... rules) {
        List<Rule<? super T>> list = List.of(rules);
        return value -> {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).check(value) instanceof Verdict.Passed) {
                    return Verdict.fail("must not pass rule " + i + ", but did");
                }
            }

            return Verdict.pass();
        };
    }

    public static <T> Rule<T> when(Rule<? super T> condition, Rule<? super T> then) {
        return value -> {
            if (condition.check(value) instanceof Verdict.Failed) {
                return Verdict.pass();
            }

            return then.check(value);
        };
    }

    public static <T> Rule<@Nullable T> optional(Rule<? super T> rule) {
        return value -> {
            if (value == null) {
                return Verdict.pass();
            }

            return rule.check(value);
        };
    }

    public static <T> Rule<T> equalTo(T expected) {
        return value -> {
            if (Objects.equals(expected, value)) {
                return Verdict.pass();
            }

            return Verdict.fail("must equal " + expected + ", got " + value);
        };
    }

    @SafeVarargs
    @SuppressWarnings("varargs") // the array is copied into an immutable set and never written
    public static <T> Rule<T> oneOf(T... allowed) {
        Set<T> set = Set.of(allowed);
        return value -> {
            if (set.contains(value)) {
                return Verdict.pass();
            }

            return Verdict.fail("must be one of " + set + ", got " + value);
        };
    }

    public static <S> Rule<Object> ofType(Class<S> type, Rule<? super S> rule) {
        return value -> {
            if (!type.isInstance(value)) {
                return Verdict.fail("must be a " + type.getSimpleName() + ", got "
                        + value.getClass().getSimpleName());
            }

            return rule.check(type.cast(value));
        };
    }

    public static Rule<String> secureEquals(String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        return value -> {
            if (MessageDigest.isEqual(expectedBytes, value.getBytes(StandardCharsets.UTF_8))) {
                return Verdict.pass();
            }

            return Verdict.fail("does not match");
        };
    }

    public static Rule<byte[]> secureEquals(byte[] expected) {
        byte[] copy = expected.clone();
        return value -> {
            if (MessageDigest.isEqual(copy, value)) {
                return Verdict.pass();
            }

            return Verdict.fail("does not match");
        };
    }
}
