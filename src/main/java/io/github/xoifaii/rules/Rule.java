package io.github.xoifaii.rules;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/// One check on a value, answering a [Verdict]. A rule never receives null unless it was wrapped by
/// [Rules#optional], so no rule checks for it. Rules join with and, or and not, and move to another
/// type with on.
@FunctionalInterface
public interface Rule<T extends @Nullable Object> {

    Verdict check(T value);

    default Rule<T> and(Rule<? super T> other) {
        return value -> {
            var verdict = check(value);
            if (verdict instanceof Verdict.Failed) {
                return verdict;
            }

            return other.check(value);
        };
    }

    default Rule<T> or(Rule<? super T> other) {
        return value -> {
            var verdict = check(value);
            if (verdict instanceof Verdict.Passed) {
                return verdict;
            }
            if (other.check(value) instanceof Verdict.Passed) {
                return Verdict.pass();
            }

            return verdict;
        };
    }

    default Rule<T> not(String reason) {
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }

        return value -> {
            if (check(value) instanceof Verdict.Passed) {
                return Verdict.fail(reason);
            }

            return Verdict.pass();
        };
    }

    default <R> Rule<R> on(Function<? super R, ? extends T> part) {
        return value -> check(part.apply(value));
    }
}
