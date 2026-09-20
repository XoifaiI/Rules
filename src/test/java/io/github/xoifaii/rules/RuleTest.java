package io.github.xoifaii.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuleTest {

    private static final Rule<String> SHORT = StringRules.maxLength(3);
    private static final Rule<String> LOWER = StringRules.matches("[a-z]+");

    @Test
    void and_answers_the_first_failure_and_does_not_ask_the_second_rule() {
        var asked = new boolean[1];
        Rule<String> spy = value -> {
            asked[0] = true;
            return Verdict.pass();
        };

        var verdict = SHORT.and(spy).check("toolong");

        assertThat(verdict).isEqualTo(Verdict.fail("length must be at most 3, got 7"));
        assertThat(asked[0]).isFalse();
    }

    @Test
    void and_passes_only_when_both_pass() {
        assertThat(SHORT.and(LOWER).check("abc")).isEqualTo(Verdict.pass());
        assertThat(SHORT.and(LOWER).check("ABC")).isEqualTo(Verdict.fail("must match [a-z]+"));
    }

    @Test
    void or_passes_when_either_passes_and_keeps_the_first_failure_when_neither_does() {
        assertThat(SHORT.or(LOWER).check("abcdef")).isEqualTo(Verdict.pass());
        assertThat(SHORT.or(LOWER).check("ABCDEF")).isEqualTo(Verdict.fail("length must be at most 3, got 6"));
    }

    @Test
    void not_inverts_the_verdict_with_the_given_reason() {
        var rule = LOWER.not("must not be all lower case");

        assertThat(rule.check("abc")).isEqualTo(Verdict.fail("must not be all lower case"));
        assertThat(rule.check("Abc")).isEqualTo(Verdict.pass());
    }

    @Test
    void on_checks_a_part_of_a_larger_value() {
        record Player(String name, int level) {}
        Rule<Player> rule = StringRules.notBlank().on(Player::name);

        assertThat(rule.check(new Player(" ", 3))).isEqualTo(Verdict.fail("must not be blank"));
        assertThat(rule.check(new Player("jack", 3))).isEqualTo(Verdict.pass());
    }
}
