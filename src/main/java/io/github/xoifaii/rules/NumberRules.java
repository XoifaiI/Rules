package io.github.xoifaii.rules;

import java.math.BigDecimal;
import java.math.BigInteger;

/// Rules on numbers of any boxed type. A bound given as a long compares exactly against integral values
/// and as a double only against floating values, so a long is never rounded through a double. A bound
/// given as a double compares everything as a double. NaN fails every bound.
public final class NumberRules {

    private NumberRules() {}

    public static Rule<Number> between(long min, long max) {
        if (max < min) {
            throw new IllegalArgumentException("max must be at least min " + min + ", got " + max);
        }

        return value -> {
            if (compare(value, min) < 0 || compare(value, max) > 0) {
                return Verdict.fail("must be from " + min + " to " + max + ", got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> between(double min, double max) {
        requireNumber("min", min);
        requireNumber("max", max);
        if (max < min) {
            throw new IllegalArgumentException("max must be at least min " + min + ", got " + max);
        }

        return value -> {
            var d = value.doubleValue();
            if (!(d >= min && d <= max)) {
                return Verdict.fail("must be from " + min + " to " + max + ", got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> min(long min) {
        return value -> {
            if (compare(value, min) < 0) {
                return Verdict.fail("must be at least " + min + ", got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> min(double min) {
        requireNumber("min", min);

        return value -> {
            if (!(value.doubleValue() >= min)) {
                return Verdict.fail("must be at least " + min + ", got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> max(long max) {
        return value -> {
            if (compare(value, max) > 0) {
                return Verdict.fail("must be at most " + max + ", got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> max(double max) {
        requireNumber("max", max);

        return value -> {
            if (!(value.doubleValue() <= max)) {
                return Verdict.fail("must be at most " + max + ", got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> positive() {
        return value -> {
            if (compare(value, 0) <= 0) {
                return Verdict.fail("must be positive, got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> notNegative() {
        return value -> {
            if (compare(value, 0) < 0) {
                return Verdict.fail("must not be negative, got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> negative() {
        return value -> {
            if (compare(value, 0) >= 0) {
                return Verdict.fail("must be negative, got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> finite() {
        return value -> {
            if (!Double.isFinite(value.doubleValue())) {
                return Verdict.fail("must be finite, got " + value);
            }

            return Verdict.pass();
        };
    }

    public static Rule<Number> whole() {
        return value -> {
            if (!isWhole(value)) {
                return Verdict.fail("must be a whole number, got " + value);
            }

            return Verdict.pass();
        };
    }

    private static int compare(Number value, long bound) {
        return switch (value) {
            case Double d -> Double.compare(d, (double) bound);
            case Float f -> Double.compare(f, (double) bound);
            case BigDecimal b -> b.compareTo(BigDecimal.valueOf(bound));
            case BigInteger b -> b.compareTo(BigInteger.valueOf(bound));
            default -> Long.compare(value.longValue(), bound);
        };
    }

    private static boolean isWhole(Number value) {
        return switch (value) {
            case Double d -> Double.isFinite(d) && d == Math.rint(d);
            case Float f -> Float.isFinite(f) && f == Math.rint(f);
            case BigDecimal b -> b.stripTrailingZeros().scale() <= 0;
            default -> true;
        };
    }

    private static void requireNumber(String what, double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException(what + " must be a number, got NaN");
        }
    }
}
