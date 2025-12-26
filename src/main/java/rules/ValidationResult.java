package rules;

import java.util.Objects;
import java.util.Optional;

public final class ValidationResult {

  private static final ValidationResult VALID = new ValidationResult(true, null, false);
  private static final ValidationResult UNAVAILABLE = new ValidationResult(false, "Validation unavailable", false);

  private final boolean valid;
  private final String message;
  private final boolean systemError;

  private ValidationResult(boolean valid, String message, boolean systemError) {
    this.valid = valid;
    this.message = message;
    this.systemError = systemError;
  }

  public static ValidationResult valid() {
    return VALID;
  }

  public static ValidationResult invalid(String message) {
    Objects.requireNonNull(message, "message cannot be null");
    return new ValidationResult(false, message, false);
  }

  /**
   * Creates a system-level error result. System errors (cycle detection,
   * depth limits, tracked object limits) should always be propagated
   * without wrapping, regardless of detailedErrors setting.
   */
  public static ValidationResult systemError(String message) {
    Objects.requireNonNull(message, "message cannot be null");
    return new ValidationResult(false, message, true);
  }

  public static ValidationResult unavailable() {
    return UNAVAILABLE;
  }

  public static ValidationResult of(boolean valid, String invalidMessage) {
    return valid ? VALID : invalid(invalidMessage);
  }

  public boolean isValid() {
    return valid;
  }

  public boolean isInvalid() {
    return !valid;
  }

  /**
   * Returns true if this is a system-level error that should be propagated
   * without modification (cycle detection, depth limits, etc.).
   */
  public boolean isSystemError() {
    return systemError;
  }

  public Optional<String> message() {
    return Optional.ofNullable(message);
  }

  public String messageOrDefault(String defaultMessage) {
    return message != null ? message : defaultMessage;
  }

  public void throwIfInvalid() {
    if (!valid) {
      throw new ValidationException(
          message != null ? message : "Validation failed");
    }
  }

  public ValidationResult and(ValidationResult other) {
    if (!valid) {
      return this;
    }
    return other != null ? other : UNAVAILABLE;
  }

  public ValidationResult or(ValidationResult other) {
    if (valid) {
      return this;
    }
    return other != null ? other : this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ValidationResult other)) {
      return false;
    }
    return valid == other.valid
        && systemError == other.systemError
        && Objects.equals(message, other.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valid, message, systemError);
  }

  @Override
  public String toString() {
    if (valid) {
      return "Valid";
    }
    String prefix = systemError ? "SystemError: " : "Invalid: ";
    return prefix + (message != null ? message : "unknown");
  }
}