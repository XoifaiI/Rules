package io.github.xoifaii.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructTest {

    private static final Struct ADDRESS = Struct.of(
            Struct.Field.required("city", Rules.ofType(String.class, StringRules.notBlank())),
            Struct.Field.optional("postcode", Rules.ofType(String.class, StringRules.length(7))));

    private static final Struct PLAYER = Struct.of(
            Struct.Field.required("name", Rules.ofType(String.class, StringRules.lengthBetween(3, 20))),
            Struct.Field.required("level", Rules.ofType(Number.class, NumberRules.between(1, 100))),
            Struct.Field.optional("address", Struct.nested(ADDRESS)));

    @Test
    void a_struct_with_every_required_field_in_range_passes() {
        assertThat(PLAYER.check(Map.of("name", "jack", "level", 7))).isEqualTo(Verdict.pass());
    }

    @Test
    void a_missing_required_field_is_named() {
        assertThat(PLAYER.check(Map.of("name", "jack"))).isEqualTo(new Verdict.Failed("level", "is required"));
    }

    @Test
    void a_null_value_counts_as_missing() {
        var value = new HashMap<String, Object>();
        value.put("name", "jack");
        value.put("level", null);

        assertThat(PLAYER.check(value)).isEqualTo(new Verdict.Failed("level", "is required"));
    }

    @Test
    void a_field_of_the_wrong_type_says_which_type_arrived() {
        assertThat(PLAYER.check(Map.of("name", "jack", "level", "seven")))
                .isEqualTo(new Verdict.Failed("level", "must be a Number, got String"));
    }

    @Test
    void an_absent_optional_field_passes_and_a_present_one_is_checked() {
        assertThat(ADDRESS.check(Map.of("city", "Leeds"))).isEqualTo(Verdict.pass());
        assertThat(ADDRESS.check(Map.of("city", "Leeds", "postcode", "LS1")))
                .isEqualTo(new Verdict.Failed("postcode", "length must be 7, got 3"));
    }

    @Test
    void a_nested_struct_failure_reads_as_a_dotted_path() {
        var value = Map.of("name", "jack", "level", 7, "address", Map.of("city", " "));

        assertThat(PLAYER.check(value)).isEqualTo(new Verdict.Failed("address.city", "must not be blank"));
    }

    @Test
    void a_nested_struct_refuses_a_value_that_is_not_an_object() {
        var value = Map.of("name", "jack", "level", 7, "address", "Leeds");

        assertThat(PLAYER.check(value)).isEqualTo(new Verdict.Failed("address", "must be an object, got String"));
    }

    @Test
    void strict_refuses_a_field_that_was_not_named_and_lenient_ignores_it() {
        var value = Map.of("name", "jack", "level", 7, "nickname", "j");

        assertThat(PLAYER.check(value)).isEqualTo(Verdict.pass());
        assertThat(PLAYER.strict().check(value)).isEqualTo(new Verdict.Failed("nickname", "unexpected field"));
    }

    @Test
    void a_struct_can_join_other_rules_on_the_same_map() {
        Rule<Map<String, Object>> rule = Rules.all(MapRules.maxSize(2), PLAYER);

        assertThat(rule.check(Map.of("name", "jack", "level", 7))).isEqualTo(Verdict.pass());
        assertThat(rule.check(Map.of("name", "jack", "level", 7, "x", 1)))
                .isEqualTo(Verdict.fail("size must be at most 2, got 3"));
    }

    @Test
    void a_repeated_field_name_is_misuse() {
        var name = Struct.Field.required("name", Rules.ofType(String.class, StringRules.notBlank()));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Struct.of(name, name))
                .withMessage("field names must be unique, got name twice");
    }
}
