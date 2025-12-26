package rules;

public final class NumberRules {

  private static final String NULL_MSG = "Value is null";
  private static final String NAN_MSG = "Value is NaN";

  private static final Rule<Number> FINITE = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    double d = value.doubleValue();
    if (Double.isNaN(d)) {
      return ValidationResult.invalid(NAN_MSG);
    }
    if (Double.isInfinite(d)) {
      return ValidationResult.invalid("Value is infinite");
    }
    return ValidationResult.valid();
  };

  private static final Rule<Number> NOT_NAN = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return Double.isNaN(value.doubleValue())
        ? ValidationResult.invalid(NAN_MSG)
        : ValidationResult.valid();
  };

  private static final Rule<Number> IS_NAN = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return Double.isNaN(value.doubleValue())
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is not NaN");
  };

  private static final Rule<Number> IS_INFINITE = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return Double.isInfinite(value.doubleValue())
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is not infinite");
  };

  private static final Rule<Number> INTEGER = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    double d = value.doubleValue();
    if (Double.isNaN(d) || Double.isInfinite(d)) {
      return ValidationResult.invalid("Value must be finite");
    }
    return d == Math.floor(d)
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is not an integer");
  };

  private static final Rule<Number> POSITIVE = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return value.doubleValue() > 0
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is not positive");
  };

  private static final Rule<Number> NEGATIVE = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return value.doubleValue() < 0
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is not negative");
  };

  private static final Rule<Number> NON_NEGATIVE = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return value.doubleValue() >= 0
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is negative");
  };

  private static final Rule<Number> NON_POSITIVE = value -> {
    if (value == null) {
      return ValidationResult.invalid(NULL_MSG);
    }
    return value.doubleValue() <= 0
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is positive");
  };

  private NumberRules() {
  }

  public static Rule<Number> finite() {
    return FINITE;
  }

  public static Rule<Number> notNaN() {
    return NOT_NAN;
  }

  public static Rule<Number> isNaN() {
    return IS_NAN;
  }

  public static Rule<Number> isInfinite() {
    return IS_INFINITE;
  }

  public static Rule<Number> integer() {
    return INTEGER;
  }

  public static Rule<Number> positive() {
    return POSITIVE;
  }

  public static Rule<Number> negative() {
    return NEGATIVE;
  }

  public static Rule<Number> nonNegative() {
    return NON_NEGATIVE;
  }

  public static Rule<Number> nonPositive() {
    return NON_POSITIVE;
  }

  public static Rule<Number> between(double min, double max) {
    if (Double.isNaN(min) || Double.isNaN(max)) {
      throw new IllegalArgumentException("bounds cannot be NaN");
    }
    if (min > max) {
      throw new IllegalArgumentException("min cannot exceed max");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      double d = value.doubleValue();
      if (Double.isNaN(d)) {
        return ValidationResult.invalid(NAN_MSG);
      }
      return (d >= min && d <= max)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value is out of range");
    };
  }

  public static Rule<Number> betweenExclusive(double min, double max) {
    if (Double.isNaN(min) || Double.isNaN(max)) {
      throw new IllegalArgumentException("bounds cannot be NaN");
    }
    if (min >= max) {
      throw new IllegalArgumentException("min must be less than max");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      double d = value.doubleValue();
      if (Double.isNaN(d)) {
        return ValidationResult.invalid(NAN_MSG);
      }
      return (d > min && d < max)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value is out of range");
    };
  }

  public static Rule<Number> min(double min) {
    if (Double.isNaN(min)) {
      throw new IllegalArgumentException("min cannot be NaN");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.doubleValue() >= min
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value is below minimum");
    };
  }

  public static Rule<Number> max(double max) {
    if (Double.isNaN(max)) {
      throw new IllegalArgumentException("max cannot be NaN");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.doubleValue() <= max
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value exceeds maximum");
    };
  }

  public static Rule<Number> closeTo(double target, double epsilon) {
    if (Double.isNaN(target)) {
      throw new IllegalArgumentException("target cannot be NaN");
    }
    if (Double.isInfinite(target)) {
      throw new IllegalArgumentException("target cannot be infinite");
    }
    if (Double.isNaN(epsilon) || epsilon < 0) {
      throw new IllegalArgumentException("epsilon must be non-negative");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      double d = value.doubleValue();
      if (Double.isNaN(d)) {
        return ValidationResult.invalid(NAN_MSG);
      }
      return Math.abs(target - d) <= epsilon
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value is not close enough to target");
    };
  }
}
