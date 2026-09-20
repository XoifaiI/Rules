package io.github.xoifaii.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StringRulesTest {

    @Test
    void empty_and_blank_are_told_apart() {
        assertThat(StringRules.notEmpty().check("")).isEqualTo(Verdict.fail("must not be empty"));
        assertThat(StringRules.notEmpty().check(" ")).isEqualTo(Verdict.pass());
        assertThat(StringRules.notBlank().check(" ")).isEqualTo(Verdict.fail("must not be blank"));
        assertThat(StringRules.notBlank().check("a")).isEqualTo(Verdict.pass());
    }

    @ParameterizedTest
    @CsvSource({"ab, false", "abc, true", "abcdef, true", "abcdefg, false"})
    void length_between_holds_at_both_ends(String value, boolean passes) {
        var rule = StringRules.lengthBetween(3, 6);

        assertThat(rule.check(value).passed()).isEqualTo(passes);
    }

    @Test
    void length_reasons_report_the_bound_and_the_length_and_never_the_string() {
        assertThat(StringRules.length(4).check("hunter2")).isEqualTo(Verdict.fail("length must be 4, got 7"));
        assertThat(StringRules.minLength(8).check("hunter2"))
                .isEqualTo(Verdict.fail("length must be at least 8, got 7"));
        assertThat(StringRules.maxLength(3).check("hunter2"))
                .isEqualTo(Verdict.fail("length must be at most 3, got 7"));
        assertThat(StringRules.lengthBetween(1, 3).check("hunter2"))
                .isEqualTo(Verdict.fail("length must be from 1 to 3, got 7"));
    }

    @Test
    void matches_needs_the_whole_string_and_contains_match_needs_a_part() {
        assertThat(StringRules.matches("[a-z]+").check("abc")).isEqualTo(Verdict.pass());
        assertThat(StringRules.matches("[a-z]+").check("abc1")).isEqualTo(Verdict.fail("must match [a-z]+"));
        assertThat(StringRules.containsMatch("[a-z]+").check("123abc")).isEqualTo(Verdict.pass());
        assertThat(StringRules.containsMatch("[a-z]+").check("123"))
                .isEqualTo(Verdict.fail("must contain a match for [a-z]+"));
    }

    @Test
    void an_inline_flag_makes_a_match_case_insensitive() {
        assertThat(StringRules.matches("(?i)[a-z]+").check("AbC")).isEqualTo(Verdict.pass());
    }

    @Test
    void a_regex_that_does_not_parse_is_misuse_at_the_door() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StringRules.matches("(a"))
                .withMessageStartingWith("regex must be valid RE2 syntax, got (a: ");
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void a_nested_quantifier_on_a_long_input_answers_in_linear_time() {
        var evil = StringRules.matches("(a+)+");
        var input = "a".repeat(100_000) + "!";

        assertThat(evil.check(input)).isEqualTo(Verdict.fail("must match (a+)+"));
    }

    @Test
    void prefix_suffix_and_substring_quote_what_was_expected() {
        assertThat(StringRules.startsWith("id-").check("x")).isEqualTo(Verdict.fail("must start with \"id-\""));
        assertThat(StringRules.endsWith(".png").check("x")).isEqualTo(Verdict.fail("must end with \".png\""));
        assertThat(StringRules.contains("@").check("x")).isEqualTo(Verdict.fail("must contain \"@\""));
        assertThat(StringRules.contains("@").check("a@b")).isEqualTo(Verdict.pass());
    }

    @ParameterizedTest
    @CsvSource({
        "123e4567-e89b-42d3-a456-426614174000, true",
        "123E4567-E89B-42D3-A456-426614174000, true",
        "123e4567e89b42d3a456426614174000, false",
        "{123e4567-e89b-42d3-a456-426614174000}, false",
        "not-a-uuid, false"
    })
    void uuid_accepts_the_canonical_hyphenated_form_only(String value, boolean passes) {
        assertThat(StringRules.uuid().check(value).passed()).isEqualTo(passes);
    }

    @Test
    void a_negative_length_bound_is_misuse() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StringRules.maxLength(-1))
                .withMessage("max must not be negative, got -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StringRules.lengthBetween(5, 2))
                .withMessage("max must be at least min 5, got 2");
    }
}
