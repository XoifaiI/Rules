package io.github.xoifaii.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionRulesTest {

    @Test
    void size_rules_report_the_bound_and_the_size() {
        assertThat(CollectionRules.notEmpty().check(List.of())).isEqualTo(Verdict.fail("must not be empty"));
        assertThat(CollectionRules.size(2).check(Set.of(1))).isEqualTo(Verdict.fail("size must be 2, got 1"));
        assertThat(CollectionRules.minSize(2).check(List.of(1)))
                .isEqualTo(Verdict.fail("size must be at least 2, got 1"));
        assertThat(CollectionRules.maxSize(1).check(List.of(1, 2)))
                .isEqualTo(Verdict.fail("size must be at most 1, got 2"));
        assertThat(CollectionRules.sizeBetween(1, 2).check(List.of(1, 2, 3)))
                .isEqualTo(Verdict.fail("size must be from 1 to 2, got 3"));
        assertThat(CollectionRules.sizeBetween(1, 2).check(List.of(1, 2))).isEqualTo(Verdict.pass());
    }

    @Test
    void each_names_the_index_of_the_first_element_that_failed() {
        var rule = CollectionRules.each(NumberRules.positive());

        assertThat(rule.check(List.of(1, 2, 3))).isEqualTo(Verdict.pass());
        assertThat(rule.check(List.of(1, -2, -3))).isEqualTo(new Verdict.Failed("[1]", "must be positive, got -2"));
    }

    @Test
    void any_needs_one_passing_element_and_none_needs_no_passing_element() {
        var any = CollectionRules.any(StringRules.startsWith("x"));
        var none = CollectionRules.none(StringRules.startsWith("x"));

        assertThat(any.check(List.of("a", "xb"))).isEqualTo(Verdict.pass());
        assertThat(any.check(List.of("a", "b"))).isEqualTo(Verdict.fail("no element passed"));
        assertThat(none.check(List.of("a", "b"))).isEqualTo(Verdict.pass());
        assertThat(none.check(List.of("a", "xb"))).isEqualTo(new Verdict.Failed("[1]", "must not pass, but did"));
    }

    @Test
    void a_size_rule_before_each_bounds_the_work_on_untrusted_input() {
        Rule<List<Integer>> rule = Rules.all(CollectionRules.maxSize(2), CollectionRules.each(NumberRules.positive()));

        assertThat(rule.check(List.of(1, 2))).isEqualTo(Verdict.pass());
        assertThat(rule.check(List.of(-1, -2, -3))).isEqualTo(Verdict.fail("size must be at most 2, got 3"));
    }

    @Test
    void a_negative_size_bound_is_misuse() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CollectionRules.size(-1))
                .withMessage("size must not be negative, got -1");
    }
}
