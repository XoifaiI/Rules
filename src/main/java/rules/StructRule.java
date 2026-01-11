package rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StructRule<K, V> {

  private final Map<K, Rule<V>> fields;
  private final boolean strict;

  private StructRule(Map<K, Rule<V>> fields, boolean strict) {
    this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    this.strict = strict;
  }

  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  public Rule<Map<K, V>> toRule() {
    return new Rule<Map<K, V>>() {
      @Override
      public ValidationResult validate(Map<K, V> value) {
        if (value == null) {
          return ValidationResult.invalid("Value is null");
        }

        Map<K, V> snapshot = createSecureSnapshot(value);

        if (strict) {
          Set<K> allowed = fields.keySet();
          for (K key : snapshot.keySet()) {
            if (key == null) {
              return ValidationResult.invalid("Null key not allowed");
            }
            if (!allowed.contains(key)) {
              return ValidationResult.invalid("Unexpected field");
            }
          }
        }

        for (Map.Entry<K, Rule<V>> entry : fields.entrySet()) {
          K key = entry.getKey();
          Rule<V> rule = entry.getValue();

          V fieldValue = snapshot.get(key);
          if (!snapshot.containsKey(key)) {
            ValidationResult result = rule.validate(null);
            if (result == null || result.isInvalid()) {
              return ValidationResult.invalid("Required field missing");
            }
            continue;
          }

          ValidationResult result = rule.validate(fieldValue);
          if (result == null || result.isInvalid()) {
            return ValidationResult.invalid("Field validation failed");
          }
        }

        return ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(
          Map<K, V> value, ValidationState state) {
        if (value == null) {
          return ValidationResult.invalid("Value is null");
        }

        Map<K, V> snapshot = createSecureSnapshot(value);

        ValidationResult sizeCheck = state.checkCollectionSize(snapshot.size());
        if (sizeCheck.isInvalid()) {
          return sizeCheck;
        }

        ValidationResult scopeCheck = state.enterScope(value);
        if (scopeCheck.isInvalid()) {
          return scopeCheck;
        }

        try {
          if (strict) {
            Set<K> allowed = fields.keySet();
            for (K key : snapshot.keySet()) {
              if (key == null) {
                return ValidationResult.invalid(
                    state.context().errorMessage(
                        "Null key not allowed",
                        "Null key not allowed"));
              }
              if (!allowed.contains(key)) {
                return ValidationResult.invalid(
                    state.context().errorMessage(
                        "Unexpected field: " + key,
                        "Unexpected field"));
              }
            }
          }

          for (Map.Entry<K, Rule<V>> entry : fields.entrySet()) {
            state.checkTimeout();

            K key = entry.getKey();
            Rule<V> rule = entry.getValue();

            if (!snapshot.containsKey(key)) {
              ValidationResult result = rule.validateWithState(null, state);
              if (result == null || result.isInvalid()) {
                if (result != null && result.isSystemError()) {
                  return result;
                }
                return ValidationResult.invalid(
                    state.context().errorMessage(
                        "Missing field: " + key,
                        "Required field missing"));
              }
              continue;
            }

            V fieldValue = snapshot.get(key);
            ValidationResult result = rule.validateWithState(fieldValue, state);
            if (result == null || result.isInvalid()) {
              if (result != null && result.isSystemError()) {
                return result;
              }
              return ValidationResult.invalid(
                  state.context().errorMessage(
                      "Field '" + key + "': "
                          + (result != null
                              ? result.messageOrDefault("invalid")
                              : "invalid"),
                      "Field validation failed"));
            }
          }

          return ValidationResult.valid();
        } finally {
          state.exitScope(value);
        }
      }
    };
  }

  @SuppressWarnings("unchecked")
  private Map<K, V> createSecureSnapshot(Map<K, V> value) {
    if (value instanceof SecureHashMap) {
      return value;
    }

    if (!value.isEmpty()) {
      K sampleKey = value.keySet().iterator().next();
      if (sampleKey instanceof String) {
        return (Map<K, V>) SecureHashMap.copyOf((Map<String, ?>) value);
      }
    }

    return new LinkedHashMap<>(value);
  }

  public static final class Builder<K, V> {

    private final Map<K, Rule<V>> fields = new LinkedHashMap<>();
    private boolean strict = false;

    private Builder() {
    }

    public Builder<K, V> field(K key, Rule<V> rule) {
      Objects.requireNonNull(key, "key cannot be null");
      Objects.requireNonNull(rule, "rule cannot be null");
      if (fields.containsKey(key)) {
        throw new IllegalArgumentException("duplicate field: " + key);
      }
      fields.put(key, rule);
      return this;
    }

    public Builder<K, V> optionalField(K key, Rule<V> rule) {
      Objects.requireNonNull(key, "key cannot be null");
      Objects.requireNonNull(rule, "rule cannot be null");
      if (fields.containsKey(key)) {
        throw new IllegalArgumentException("duplicate field: " + key);
      }
      fields.put(key, ObjectRules.optional(rule));
      return this;
    }

    public Builder<K, V> strict() {
      this.strict = true;
      return this;
    }

    public StructRule<K, V> build() {
      return new StructRule<>(fields, strict);
    }

    public Rule<Map<K, V>> toRule() {
      return build().toRule();
    }
  }
}