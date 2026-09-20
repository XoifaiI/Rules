package io.github.xoifaii.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class VerdictTest {

    @Test
    void a_pass_has_no_path_and_stays_a_pass_at_any_path() {
        assertThat(Verdict.pass().at("name")).isEqualTo(Verdict.pass());
        assertThat(Verdict.pass().passed()).isTrue();
    }

    @Test
    void a_failure_at_a_field_puts_the_field_before_the_reason() {
        var verdict = Verdict.fail("must not be blank").at("name");

        assertThat(verdict).isEqualTo(new Verdict.Failed("name", "must not be blank"));
        assertThat(new Verdict.Failed("name", "must not be blank").message()).isEqualTo("name: must not be blank");
        assertThat(new Verdict.Failed("", "must not be blank").message()).isEqualTo("must not be blank");
    }

    @Test
    void nested_fields_join_with_a_dot_and_indexes_join_without_one() {
        var verdict =
                Verdict.fail("must be positive, got -1").at("[2]").at("scores").at("player");

        assertThat(verdict).isEqualTo(new Verdict.Failed("player.scores[2]", "must be positive, got -1"));
    }

    @Test
    void a_blank_reason_is_misuse() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Verdict.fail(" "))
                .withMessage("reason must not be blank");
    }
}
