package io.github.xoifaii.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A rule on a map with named fields, the shape a JSON object arrives in. A required field must be
/// present with a value; an optional one is checked only when present. Strict refuses any field that
/// was not named. A failure carries the field name as its path, so nested structs read as a.b.c.
public final class Struct implements Rule<Map<?, ?>> {

    private final List<Field> fields;
    private final Set<String> names;
    private final boolean strict;

    private Struct(List<Field> fields, boolean strict) {
        var seen = new HashSet<String>();
        for (Field field : fields) {
            if (!seen.add(field.name())) {
                throw new IllegalArgumentException("field names must be unique, got " + field.name() + " twice");
            }
        }
        this.fields = fields;
        this.names = Set.copyOf(seen);
        this.strict = strict;
    }

    public static Struct of(Field... fields) {
        return new Struct(List.of(fields), false);
    }

    public static Rule<Object> nested(Struct struct) {
        return value -> {
            if (!(value instanceof Map<?, ?> map)) {
                return Verdict.fail("must be an object, got " + value.getClass().getSimpleName());
            }

            return struct.check(map);
        };
    }

    public Struct strict() {
        return new Struct(fields, true);
    }

    @Override
    public Verdict check(Map<?, ?> value) {
        if (strict) {
            for (Object key : value.keySet()) {
                if (!names.contains(key)) {
                    return Verdict.fail("unexpected field").at(String.valueOf(key));
                }
            }
        }

        for (Field field : fields) {
            var item = value.get(field.name());
            if (item == null) {
                if (field.required()) {
                    return Verdict.fail("is required").at(field.name());
                }
                continue;
            }

            var verdict = field.rule().check(item);
            if (verdict instanceof Verdict.Failed) {
                return verdict.at(field.name());
            }
        }

        return Verdict.pass();
    }

    public record Field(String name, Rule<Object> rule, boolean required) {

        public Field {
            if (name.isBlank()) {
                throw new IllegalArgumentException("field name must not be blank");
            }
        }

        public static Field required(String name, Rule<Object> rule) {
            return new Field(name, rule, true);
        }

        public static Field optional(String name, Rule<Object> rule) {
            return new Field(name, rule, false);
        }
    }
}
