package rules;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public final class ObjectRules {

  private static final String NULL_MSG = "Value is null";
  private static final int SECURE_COMPARE_MAX_LEN = 1024;

  private static final Rule<Object> NOT_NULL = value -> value != null
      ? ValidationResult.valid()
      : ValidationResult.invalid(NULL_MSG);

  private static final Rule<Object> IS_NULL = value -> value == null
      ? ValidationResult.valid()
      : ValidationResult.invalid("Value is not null");

  private ObjectRules() {
  }

  public static Rule<Object> notNull() {
    return NOT_NULL;
  }

  public static Rule<Object> isNull() {
    return IS_NULL;
  }

  public static <T> Rule<Object> instanceOf(Class<T> type) {
    Objects.requireNonNull(type, "type cannot be null");
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return type.isInstance(value)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value is not of expected type");
    };
  }

  public static <T> Rule<T> optional(Rule<T> rule) {
    Objects.requireNonNull(rule, "rule cannot be null");
    return new Rule<T>() {
      @Override
      public ValidationResult validate(T value) {
        return value == null
            ? ValidationResult.valid()
            : rule.validate(value);
      }

      @Override
      public ValidationResult validateWithState(T value, ValidationState state) {
        return value == null
            ? ValidationResult.valid()
            : rule.validateWithState(value, state);
      }
    };
  }

  @SafeVarargs
  public static <T> Rule<T> oneOf(T... allowed) {
    Objects.requireNonNull(allowed, "allowed cannot be null");
    for (int i = 0; i < allowed.length; i++) {
      if (allowed[i] == null) {
        throw new IllegalArgumentException(
            "allowed value at index " + i + " is null");
      }
    }
    Set<T> allowedSet = Set.of(Arrays.copyOf(allowed, allowed.length));
    return value -> allowedSet.contains(value)
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value is not one of the allowed values");
  }

  public static <T extends Enum<T>> Rule<T> validEnum(Class<T> enumClass) {
    Objects.requireNonNull(enumClass, "enumClass cannot be null");
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      return value.getDeclaringClass() == enumClass
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value is not a valid enum constant");
    };
  }

  public static <T> Rule<T> equalTo(T expected) {
    return value -> Objects.equals(expected, value)
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value does not equal expected");
  }

  public static <T> Rule<T> notEqualTo(T unexpected) {
    return value -> !Objects.equals(value, unexpected)
        ? ValidationResult.valid()
        : ValidationResult.invalid("Value equals unexpected value");
  }

  public static Rule<String> secureEquals(String expected) {
    Objects.requireNonNull(expected, "expected cannot be null");
    if (expected.length() > SECURE_COMPARE_MAX_LEN) {
      throw new IllegalArgumentException(
          "expected exceeds maximum secure comparison length");
    }
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      if (value.length() > SECURE_COMPARE_MAX_LEN) {
        return ValidationResult.invalid("Value exceeds maximum length");
      }
      return constantTimeEquals(expected, value)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value does not match");
    };
  }

  public static Rule<byte[]> secureEqualsBytes(byte[] expected) {
    Objects.requireNonNull(expected, "expected cannot be null");
    if (expected.length > SECURE_COMPARE_MAX_LEN) {
      throw new IllegalArgumentException(
          "expected exceeds maximum secure comparison length");
    }
    byte[] copy = expected.clone();
    return value -> {
      if (value == null) {
        return ValidationResult.invalid(NULL_MSG);
      }
      if (value.length > SECURE_COMPARE_MAX_LEN) {
        return ValidationResult.invalid("Value exceeds maximum length");
      }
      return constantTimeEquals(copy, value)
          ? ValidationResult.valid()
          : ValidationResult.invalid("Value does not match");
    };
  }

  private static boolean constantTimeEquals(String a, String b) {
    byte[] aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return java.security.MessageDigest.isEqual(aBytes, bBytes);
  }

  private static boolean constantTimeEquals(byte[] a, byte[] b) {
    return java.security.MessageDigest.isEqual(a, b);
  }
}