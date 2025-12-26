package rules;

import java.time.Duration;
import java.util.Objects;

public final class ValidationContext {

  public static final ValidationContext DEFAULT = builder().build();

  private static final int MIN_DEPTH = 1;
  private static final int MIN_COLLECTION_SIZE = 1;
  private static final int MIN_TRACKED_OBJECTS = 1;

  private final int maxDepth;
  private final int maxCollectionSize;
  private final int maxTrackedObjects;
  private final boolean cycleDetection;
  private final boolean detailedErrors;
  private final boolean failSafe;
  private final boolean failSafeReturnsValid;
  private final long timeoutNanos;

  private ValidationContext(Builder builder) {
    this.maxDepth = builder.maxDepth;
    this.maxCollectionSize = builder.maxCollectionSize;
    this.maxTrackedObjects = builder.maxTrackedObjects;
    this.cycleDetection = builder.cycleDetection;
    this.detailedErrors = builder.detailedErrors;
    this.failSafe = builder.failSafe;
    this.failSafeReturnsValid = builder.failSafeReturnsValid;
    this.timeoutNanos = builder.timeoutNanos;
  }

  public static Builder builder() {
    return new Builder();
  }

  public int maxDepth() {
    return maxDepth;
  }

  public int maxCollectionSize() {
    return maxCollectionSize;
  }

  public int maxTrackedObjects() {
    return maxTrackedObjects;
  }

  public boolean cycleDetection() {
    return cycleDetection;
  }

  public boolean detailedErrors() {
    return detailedErrors;
  }

  public boolean failSafe() {
    return failSafe;
  }

  public boolean failSafeReturnsValid() {
    return failSafeReturnsValid;
  }

  public long timeoutNanos() {
    return timeoutNanos;
  }

  public String errorMessage(String detailed, String generic) {
    return detailedErrors ? detailed : generic;
  }

  public String errorMessage(String detailed) {
    return errorMessage(detailed, "Validation failed");
  }

  public ValidationResult validate(Object value, Rule<Object> rule) {
    ValidationState state = new ValidationState(this);
    try {
      return state.execute(value, rule);
    } finally {
      state.cleanup();
    }
  }

  @SuppressWarnings("unchecked")
  public <T> ValidationResult validateTyped(T value, Rule<T> rule) {
    return validate(value, (Rule<Object>) rule);
  }

  public static final class Builder {

    private int maxDepth = 32;
    private int maxCollectionSize = 10_000;
    private int maxTrackedObjects = 1_000;
    private boolean cycleDetection = true;
    private boolean detailedErrors = false;
    private boolean failSafe = false;
    private boolean failSafeReturnsValid = false;
    private long timeoutNanos = 0;

    private Builder() {
    }

    public Builder maxDepth(int maxDepth) {
      if (maxDepth < MIN_DEPTH) {
        throw new IllegalArgumentException(
            "maxDepth must be at least " + MIN_DEPTH);
      }
      this.maxDepth = maxDepth;
      return this;
    }

    public Builder maxCollectionSize(int maxCollectionSize) {
      if (maxCollectionSize < MIN_COLLECTION_SIZE) {
        throw new IllegalArgumentException(
            "maxCollectionSize must be at least " + MIN_COLLECTION_SIZE);
      }
      this.maxCollectionSize = maxCollectionSize;
      return this;
    }

    public Builder maxTrackedObjects(int maxTrackedObjects) {
      if (maxTrackedObjects < MIN_TRACKED_OBJECTS) {
        throw new IllegalArgumentException(
            "maxTrackedObjects must be at least " + MIN_TRACKED_OBJECTS);
      }
      this.maxTrackedObjects = maxTrackedObjects;
      return this;
    }

    public Builder cycleDetection(boolean cycleDetection) {
      this.cycleDetection = cycleDetection;
      return this;
    }

    public Builder detailedErrors(boolean detailedErrors) {
      this.detailedErrors = detailedErrors;
      return this;
    }

    public Builder failSafe(boolean failSafe) {
      this.failSafe = failSafe;
      return this;
    }

    public Builder failSafeReturnsValid(boolean failSafeReturnsValid) {
      this.failSafeReturnsValid = failSafeReturnsValid;
      return this;
    }

    public Builder timeout(Duration timeout) {
      Objects.requireNonNull(timeout, "timeout cannot be null");
      if (timeout.isNegative()) {
        throw new IllegalArgumentException("timeout cannot be negative");
      }
      this.timeoutNanos = timeout.toNanos();
      return this;
    }

    public ValidationContext build() {
      return new ValidationContext(this);
    }
  }
}
