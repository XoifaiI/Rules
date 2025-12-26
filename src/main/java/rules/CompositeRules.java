package rules;

import java.util.List;
import java.util.Objects;

public final class CompositeRules {

  private CompositeRules() {
  }

  @SafeVarargs
  public static <T> Rule<T> all(Rule<? super T>... rules) {
    Objects.requireNonNull(rules, "rules cannot be null");
    if (rules.length == 0) {
      return Rule.alwaysValid();
    }
    for (int i = 0; i < rules.length; i++) {
      if (rules[i] == null) {
        throw new IllegalArgumentException("rule at index " + i + " is null");
      }
    }
    List<Rule<? super T>> ruleList = List.of(rules);

    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        for (Rule<? super T> rule : ruleList) {
          ValidationResult result = rule.validate(value);
          if (result == null || result.isInvalid()) {
            return result != null
                ? result
                : ValidationResult.invalid("Validation failed");
          }
        }
        return ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        for (Rule<? super T> rule : ruleList) {
          state.checkTimeout();
          ValidationResult result = rule.validateWithState(value, state);
          if (result == null || result.isInvalid()) {
            return result != null
                ? result
                : ValidationResult.invalid("Validation failed");
          }
        }
        return ValidationResult.valid();
      }
    };
  }

  @SafeVarargs
  public static <T> Rule<T> any(Rule<? super T>... rules) {
    Objects.requireNonNull(rules, "rules cannot be null");
    if (rules.length == 0) {
      return Rule.alwaysInvalid("No rules to match");
    }
    for (int i = 0; i < rules.length; i++) {
      if (rules[i] == null) {
        throw new IllegalArgumentException("rule at index " + i + " is null");
      }
    }
    List<Rule<? super T>> ruleList = List.of(rules);

    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        ValidationResult firstFailure = null;
        for (Rule<? super T> rule : ruleList) {
          ValidationResult result = rule.validate(value);
          if (result != null && result.isValid()) {
            return ValidationResult.valid();
          }
          if (firstFailure == null && result != null) {
            firstFailure = result;
          }
        }
        return firstFailure != null
            ? firstFailure
            : ValidationResult.invalid("No rules matched");
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        ValidationResult firstFailure = null;
        for (Rule<? super T> rule : ruleList) {
          state.checkTimeout();
          ValidationResult result = rule.validateWithState(value, state);
          if (result != null && result.isValid()) {
            return ValidationResult.valid();
          }
          if (firstFailure == null && result != null) {
            firstFailure = result;
          }
        }
        return firstFailure != null
            ? firstFailure
            : ValidationResult.invalid("No rules matched");
      }
    };
  }

  @SafeVarargs
  public static <T> Rule<T> none(Rule<? super T>... rules) {
    Objects.requireNonNull(rules, "rules cannot be null");
    if (rules.length == 0) {
      return Rule.alwaysValid();
    }
    for (int i = 0; i < rules.length; i++) {
      if (rules[i] == null) {
        throw new IllegalArgumentException("rule at index " + i + " is null");
      }
    }
    List<Rule<? super T>> ruleList = List.of(rules);

    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        for (Rule<? super T> rule : ruleList) {
          ValidationResult result = rule.validate(value);
          if (result != null && result.isValid()) {
            return ValidationResult.invalid("Unexpected match");
          }
        }
        return ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        for (Rule<? super T> rule : ruleList) {
          state.checkTimeout();
          ValidationResult result = rule.validateWithState(value, state);
          if (result != null && result.isValid()) {
            return ValidationResult.invalid("Unexpected match");
          }
        }
        return ValidationResult.valid();
      }
    };
  }

  public static <T> Rule<T> when(Rule<T> condition, Rule<T> thenRule) {
    Objects.requireNonNull(condition, "condition cannot be null");
    Objects.requireNonNull(thenRule, "thenRule cannot be null");
    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        ValidationResult condResult = condition.validate(value);
        if (condResult != null && condResult.isValid()) {
          return thenRule.validate(value);
        }
        return ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        state.checkTimeout();
        ValidationResult condResult = condition.validateWithState(value, state);
        if (condResult != null && condResult.isValid()) {
          return thenRule.validateWithState(value, state);
        }
        return ValidationResult.valid();
      }
    };
  }

  public static <T> Rule<T> whenElse(
      Rule<T> condition, Rule<T> thenRule, Rule<T> elseRule) {
    Objects.requireNonNull(condition, "condition cannot be null");
    Objects.requireNonNull(thenRule, "thenRule cannot be null");
    Objects.requireNonNull(elseRule, "elseRule cannot be null");
    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        ValidationResult condResult = condition.validate(value);
        if (condResult != null && condResult.isValid()) {
          return thenRule.validate(value);
        }
        return elseRule.validate(value);
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        state.checkTimeout();
        ValidationResult condResult = condition.validateWithState(value, state);
        if (condResult != null && condResult.isValid()) {
          return thenRule.validateWithState(value, state);
        }
        return elseRule.validateWithState(value, state);
      }
    };
  }
}
