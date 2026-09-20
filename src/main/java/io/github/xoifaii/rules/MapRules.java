package io.github.xoifaii.rules;

import java.util.Map;

/// Rules on maps. A key or value rule reports the failing key in the path. A size rule placed before
/// them bounds the work done on untrusted input.
public final class MapRules {

    private MapRules() {}

    public static Rule<Map<?, ?>> notEmpty() {
        return value -> {
            if (value.isEmpty()) {
                return Verdict.fail("must not be empty");
            }

            return Verdict.pass();
        };
    }

    public static Rule<Map<?, ?>> maxSize(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max must not be negative, got " + max);
        }

        return value -> {
            if (value.size() > max) {
                return Verdict.fail("size must be at most " + max + ", got " + value.size());
            }

            return Verdict.pass();
        };
    }

    public static <K> Rule<Map<? extends K, ?>> keys(Rule<? super K> key) {
        return value -> {
            for (K item : value.keySet()) {
                var verdict = key.check(item);
                if (verdict instanceof Verdict.Failed) {
                    return verdict.at("key " + item);
                }
            }

            return Verdict.pass();
        };
    }

    public static <V> Rule<Map<?, ? extends V>> values(Rule<? super V> value) {
        return map -> {
            for (Map.Entry<?, ? extends V> entry : map.entrySet()) {
                var verdict = value.check(entry.getValue());
                if (verdict instanceof Verdict.Failed) {
                    return verdict.at(String.valueOf(entry.getKey()));
                }
            }

            return Verdict.pass();
        };
    }
}
