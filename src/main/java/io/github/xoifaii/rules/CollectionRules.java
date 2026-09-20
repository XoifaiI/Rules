package io.github.xoifaii.rules;

import java.util.Collection;

/// Rules on collections. An element rule reports the failing index in the path, as in items[3]. A size
/// rule placed before an element rule bounds the work done on untrusted input, since each visits every
/// element.
public final class CollectionRules {

    private CollectionRules() {}

    public static Rule<Collection<?>> notEmpty() {
        return value -> {
            if (value.isEmpty()) {
                return Verdict.fail("must not be empty");
            }

            return Verdict.pass();
        };
    }

    public static Rule<Collection<?>> size(int exact) {
        requireNotNegative("size", exact);

        return value -> {
            if (value.size() != exact) {
                return Verdict.fail("size must be " + exact + ", got " + value.size());
            }

            return Verdict.pass();
        };
    }

    public static Rule<Collection<?>> sizeBetween(int min, int max) {
        requireNotNegative("min", min);
        if (max < min) {
            throw new IllegalArgumentException("max must be at least min " + min + ", got " + max);
        }

        return value -> {
            var size = value.size();
            if (size < min || size > max) {
                return Verdict.fail("size must be from " + min + " to " + max + ", got " + size);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Collection<?>> minSize(int min) {
        requireNotNegative("min", min);

        return value -> {
            if (value.size() < min) {
                return Verdict.fail("size must be at least " + min + ", got " + value.size());
            }

            return Verdict.pass();
        };
    }

    public static Rule<Collection<?>> maxSize(int max) {
        requireNotNegative("max", max);

        return value -> {
            if (value.size() > max) {
                return Verdict.fail("size must be at most " + max + ", got " + value.size());
            }

            return Verdict.pass();
        };
    }

    public static <T> Rule<Collection<? extends T>> each(Rule<? super T> element) {
        return value -> {
            var index = 0;
            for (T item : value) {
                var verdict = element.check(item);
                if (verdict instanceof Verdict.Failed) {
                    return verdict.at("[" + index + "]");
                }
                index++;
            }

            return Verdict.pass();
        };
    }

    public static <T> Rule<Collection<? extends T>> any(Rule<? super T> element) {
        return value -> {
            for (T item : value) {
                if (element.check(item) instanceof Verdict.Passed) {
                    return Verdict.pass();
                }
            }

            return Verdict.fail("no element passed");
        };
    }

    public static <T> Rule<Collection<? extends T>> none(Rule<? super T> element) {
        return value -> {
            var index = 0;
            for (T item : value) {
                if (element.check(item) instanceof Verdict.Passed) {
                    return Verdict.fail("must not pass, but did").at("[" + index + "]");
                }
                index++;
            }

            return Verdict.pass();
        };
    }

    private static void requireNotNegative(String what, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(what + " must not be negative, got " + value);
        }
    }
}
