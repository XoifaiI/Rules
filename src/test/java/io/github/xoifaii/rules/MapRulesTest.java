package io.github.xoifaii.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MapRulesTest {

    @Test
    void size_rules_report_the_bound_and_the_size() {
        assertThat(MapRules.notEmpty().check(Map.of())).isEqualTo(Verdict.fail("must not be empty"));
        assertThat(MapRules.maxSize(1).check(Map.of("a", 1, "b", 2)))
                .isEqualTo(Verdict.fail("size must be at most 1, got 2"));
        assertThat(MapRules.maxSize(2).check(Map.of("a", 1, "b", 2))).isEqualTo(Verdict.pass());
    }

    @Test
    void a_failing_key_is_named_as_a_key_in_the_path() {
        var rule = MapRules.keys(StringRules.maxLength(2));

        assertThat(rule.check(Map.of("ab", 1))).isEqualTo(Verdict.pass());
        assertThat(rule.check(Map.of("abc", 1)))
                .isEqualTo(new Verdict.Failed("key abc", "length must be at most 2, got 3"));
    }

    @Test
    void a_failing_value_is_named_by_its_key_in_the_path() {
        var rule = MapRules.values(NumberRules.positive());

        assertThat(rule.check(Map.of("a", 1))).isEqualTo(Verdict.pass());
        assertThat(rule.check(Map.of("a", -1))).isEqualTo(new Verdict.Failed("a", "must be positive, got -1"));
    }
}
