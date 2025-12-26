package rules;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface Rule<T> {

  ValidationResult validate(T value);

  default ValidationResult validateWithState(T value, ValidationState state) {
    return validate(value);
  }

  default boolean test(T value) {
    try {
      ValidationResult result = validate(value);
      return result != null && result.isValid();
    } catch (Exception e) {
      return false;
    }
  }

  default void enforce(T value) {
    ValidationResult result = validate(value);
    if (result == null || result.isInvalid()) {
      throw new ValidationException(
          result != null ? result.messageOrDefault("Validation failed")
              : "Validation failed");
    }
  }

  default ValidationResult safeValidate(T value) {
    try {
      ValidationResult result = validate(value);
      return result != null ? result : ValidationResult.invalid("Validation returned null");
    } catch (Throwable t) {
      return ValidationResult.invalid("Validation failed: " + t.getMessage());
    }
  }

  default Rule<T> and(Rule<? super T> other) {
    Objects.requireNonNull(other, "other cannot be null");
    Rule<T> self = this;
    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        ValidationResult result = self.validate(value);
        if (result == null || result.isInvalid()) {
          return result;
        }
        return other.validate(value);
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        state.checkTimeout();
        ValidationResult result = self.validateWithState(value, state);
        if (result == null || result.isInvalid()) {
          return result;
        }
        return other.validateWithState(value, state);
      }
    };
  }

  default Rule<T> or(Rule<? super T> other) {
    Objects.requireNonNull(other, "other cannot be null");
    Rule<T> self = this;
    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        ValidationResult result = self.validate(value);
        if (result != null && result.isValid()) {
          return result;
        }
        return other.validate(value);
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        state.checkTimeout();
        ValidationResult result = self.validateWithState(value, state);
        if (result != null && result.isValid()) {
          return result;
        }
        return other.validateWithState(value, state);
      }
    };
  }

  default Rule<T> negate(String message) {
    Objects.requireNonNull(message, "message cannot be null");
    Rule<T> self = this;
    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        ValidationResult result = self.validate(value);
        return (result != null && result.isValid())
            ? ValidationResult.invalid(message)
            : ValidationResult.valid();
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        state.checkTimeout();
        ValidationResult result = self.validateWithState(value, state);
        return (result != null && result.isValid())
            ? ValidationResult.invalid(message)
            : ValidationResult.valid();
      }
    };
  }

  default <R> Rule<R> compose(Function<? super R, ? extends T> mapper) {
    Objects.requireNonNull(mapper, "mapper cannot be null");
    Rule<T> self = this;
    return new Rule<R>() {
      @Override
      public ValidationResult validate(R value) {
        T mapped = mapper.apply(value);
        return self.validate(mapped);
      }

      @Override
      public ValidationResult validateWithState(R value, ValidationState state) {
        state.checkTimeout();
        T mapped = mapper.apply(value);
        return self.validateWithState(mapped, state);
      }
    };
  }

  static <T> Rule<T> alwaysValid() {
    return value -> ValidationResult.valid();
  }

  static <T> Rule<T> alwaysInvalid(String message) {
    Objects.requireNonNull(message, "message cannot be null");
    return value -> ValidationResult.invalid(message);
  }
}
