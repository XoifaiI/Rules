package io.github.xoifaii.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NumberRulesTest {

    @ParameterizedTest
    @CsvSource({"-1, false", "0, true", "150, true", "151, false"})
    void between_holds_at_both_ends(int value, boolean passes) {
        assertThat(NumberRules.between(0, 150).check(value).passed()).isEqualTo(passes);
    }

    @Test
    void a_long_bound_compares_a_long_exactly_where_a_double_would_round() {
        var rule = NumberRules.max(Long.MAX_VALUE - 1);

        assertThat(rule.check(Long.MAX_VALUE - 1)).isEqualTo(Verdict.pass());
        assertThat(rule.check(Long.MAX_VALUE))
                .isEqualTo(Verdict.fail("must be at most 9223372036854775806, got 9223372036854775807"));
    }

    @Test
    void a_long_bound_still_compares_a_fraction_as_a_fraction() {
        assertThat(NumberRules.min(1).check(0.5)).isEqualTo(Verdict.fail("must be at least 1, got 0.5"));
        assertThat(NumberRules.min(1).check(1.5)).isEqualTo(Verdict.pass());
        assertThat(NumberRules.max(1).check(new BigDecimal("1.0001")))
                .isEqualTo(Verdict.fail("must be at most 1, got 1.0001"));
        assertThat(NumberRules.max(1).check(BigInteger.TWO)).isEqualTo(Verdict.fail("must be at most 1, got 2"));
    }

    @Test
    void a_double_bound_compares_everything_as_a_double() {
        var rule = NumberRules.between(0.0, 1.0);

        assertThat(rule.check(1)).isEqualTo(Verdict.pass());
        assertThat(rule.check(1.0001f)).isEqualTo(Verdict.fail("must be from 0.0 to 1.0, got 1.0001"));
    }

    @Test
    void nan_fails_every_bound_and_is_refused_as_a_bound() {
        assertThat(NumberRules.between(0, 10).check(Double.NaN))
                .isEqualTo(Verdict.fail("must be from 0 to 10, got NaN"));
        assertThat(NumberRules.min(0.0).check(Double.NaN)).isEqualTo(Verdict.fail("must be at least 0.0, got NaN"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NumberRules.max(Double.NaN))
                .withMessage("max must be a number, got NaN");
    }

    @Test
    void sign_rules_treat_zero_as_neither_positive_nor_negative() {
        assertThat(NumberRules.positive().check(0)).isEqualTo(Verdict.fail("must be positive, got 0"));
        assertThat(NumberRules.negative().check(0)).isEqualTo(Verdict.fail("must be negative, got 0"));
        assertThat(NumberRules.notNegative().check(0)).isEqualTo(Verdict.pass());
        assertThat(NumberRules.notNegative().check(-0.5)).isEqualTo(Verdict.fail("must not be negative, got -0.5"));
    }

    @Test
    void finite_refuses_infinities_and_nan() {
        assertThat(NumberRules.finite().check(Double.POSITIVE_INFINITY))
                .isEqualTo(Verdict.fail("must be finite, got Infinity"));
        assertThat(NumberRules.finite().check(Long.MAX_VALUE)).isEqualTo(Verdict.pass());
    }

    @Test
    void whole_accepts_integral_values_of_any_type_and_refuses_fractions() {
        assertThat(NumberRules.whole().check(3.0)).isEqualTo(Verdict.pass());
        assertThat(NumberRules.whole().check(new BigDecimal("3.000"))).isEqualTo(Verdict.pass());
        assertThat(NumberRules.whole().check(3.5)).isEqualTo(Verdict.fail("must be a whole number, got 3.5"));
        assertThat(NumberRules.whole().check(Double.NaN)).isEqualTo(Verdict.fail("must be a whole number, got NaN"));
    }

    @Test
    void a_max_below_min_is_misuse() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NumberRules.between(10, 1))
                .withMessage("max must be at least min 10, got 1");
    }
}
