package io.github.xoifaii.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RulesTest {

    private static final Rule<String> NOT_BLANK = StringRules.notBlank();
    private static final Rule<String> SHORT = StringRules.maxLength(5);

    @Test
    void all_passes_when_every_rule_passes_and_answers_the_first_failure_otherwise() {
        Rule<String> rule = Rules.all(NOT_BLANK, SHORT);

        assertThat(rule.check("jack")).isEqualTo(Verdict.pass());
        assertThat(rule.check("   ")).isEqualTo(Verdict.fail("must not be blank"));
        assertThat(rule.check("toolong")).isEqualTo(Verdict.fail("length must be at most 5, got 7"));
    }

    @Test
    void all_of_no_rules_passes_everything() {
        assertThat(Rules.<String>all().check("anything")).isEqualTo(Verdict.pass());
    }

    @Test
    void any_passes_when_one_rule_passes_and_answers_the_first_failure_when_none_does() {
        Rule<String> rule = Rules.any(StringRules.startsWith("a"), StringRules.endsWith("z"));

        assertThat(rule.check("xyz")).isEqualTo(Verdict.pass());
        assertThat(rule.check("mmm")).isEqualTo(Verdict.fail("must start with \"a\""));
    }

    @Test
    void any_of_no_rules_is_misuse() {
        assertThatIllegalArgumentException()
                .isThrownBy(Rules::<String>any)
                .withMessage("any needs at least one rule, got none");
    }

    @Test
    void none_fails_naming_the_rule_that_passed() {
        Rule<String> rule = Rules.none(StringRules.contains("<"), StringRules.contains(">"));

        assertThat(rule.check("plain")).isEqualTo(Verdict.pass());
        assertThat(rule.check("a > b")).isEqualTo(Verdict.fail("must not pass rule 1, but did"));
    }

    @Test
    void when_applies_the_rule_only_while_the_condition_holds() {
        Rule<String> rule = Rules.when(StringRules.startsWith("id-"), StringRules.length(7));

        assertThat(rule.check("id-1234")).isEqualTo(Verdict.pass());
        assertThat(rule.check("id-1")).isEqualTo(Verdict.fail("length must be 7, got 4"));
        assertThat(rule.check("free text of any length")).isEqualTo(Verdict.pass());
    }

    @Test
    void optional_passes_null_and_checks_everything_else() {
        var rule = Rules.optional(NOT_BLANK);

        assertThat(rule.check(null)).isEqualTo(Verdict.pass());
        assertThat(rule.check(" ")).isEqualTo(Verdict.fail("must not be blank"));
    }

    @Test
    void equal_to_and_one_of_say_what_arrived() {
        assertThat(Rules.equalTo(3).check(4)).isEqualTo(Verdict.fail("must equal 3, got 4"));
        assertThat(Rules.oneOf("a", "b").check("a")).isEqualTo(Verdict.pass());
        assertThat(Rules.oneOf("a").check("c")).isEqualTo(Verdict.fail("must be one of [a], got c"));
    }

    @Test
    void of_type_refuses_another_type_and_hands_the_right_type_to_the_rule() {
        var rule = Rules.ofType(String.class, NOT_BLANK);

        assertThat(rule.check(42)).isEqualTo(Verdict.fail("must be a String, got Integer"));
        assertThat(rule.check(" ")).isEqualTo(Verdict.fail("must not be blank"));
        assertThat(rule.check("ok")).isEqualTo(Verdict.pass());
    }

    @Test
    void secure_equals_never_echoes_the_value() {
        assertThat(Rules.secureEquals("hunter2").check("hunter2")).isEqualTo(Verdict.pass());
        assertThat(Rules.secureEquals("hunter2").check("hunter3")).isEqualTo(Verdict.fail("does not match"));
    }

    @Test
    void secure_equals_on_bytes_keeps_its_own_copy_of_the_expected_value() {
        var expected = "key".getBytes(StandardCharsets.UTF_8);
        var rule = Rules.secureEquals(expected);
        expected[0] = 'x';

        assertThat(rule.check("key".getBytes(StandardCharsets.UTF_8))).isEqualTo(Verdict.pass());
        assertThat(rule.check(expected)).isEqualTo(Verdict.fail("does not match"));
    }
}
