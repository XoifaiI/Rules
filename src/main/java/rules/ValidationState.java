package rules;

import java.util.Arrays;

public final class ValidationState {

  private static final int INITIAL_CAPACITY = 16;

  private final ValidationContext ctx;
  private final long deadlineNanos;

  private int depth;
  private Object[] visited;
  private int visitedCount;

  ValidationState(ValidationContext ctx) {
    this.ctx = ctx;
    this.depth = 0;
    this.visited = ctx.cycleDetection() ? new Object[INITIAL_CAPACITY] : null;
    this.visitedCount = 0;
    long timeout = ctx.timeoutNanos();
    if (timeout > 0) {
      long now = System.nanoTime();
      this.deadlineNanos = (Long.MAX_VALUE - now < timeout)
          ? Long.MAX_VALUE
          : now + timeout;
    } else {
      this.deadlineNanos = 0;
    }
  }

  public ValidationContext context() {
    return ctx;
  }

  public void checkTimeout() {
    if (deadlineNanos > 0 && System.nanoTime() > deadlineNanos) {
      throw new ValidationTimeoutException("Validation timeout exceeded");
    }
  }

  public ValidationResult enterScope(Object value) {
    checkTimeout();

    if (depth >= ctx.maxDepth()) {
      return ValidationResult.systemError("Maximum validation depth exceeded");
    }

    if (ctx.cycleDetection() && value != null) {
      if (visitedCount >= ctx.maxTrackedObjects()) {
        return ValidationResult.systemError("Maximum tracked objects exceeded");
      }

      if (containsIdentity(value)) {
        return ValidationResult.systemError("Cyclic reference detected");
      }

      addVisited(value);
    }

    depth++;
    return ValidationResult.valid();
  }

  public void exitScope(Object value) {
    if (depth > 0) {
      depth--;
    }

    if (ctx.cycleDetection() && value != null) {
      removeVisited(value);
    }
  }

  public ValidationResult checkCollectionSize(int size) {
    checkTimeout();

    if (size > ctx.maxCollectionSize()) {
      return ValidationResult.systemError("Collection size exceeds maximum allowed");
    }
    return ValidationResult.valid();
  }

  ValidationResult execute(Object value, Rule<Object> rule) {
    try {
      ValidationResult result = rule.validateWithState(value, this);
      return result != null ? result
          : ValidationResult.invalid(
              ctx.errorMessage("Null validation result"));
    } catch (ValidationTimeoutException e) {
      if (ctx.failSafe()) {
        return ctx.failSafeReturnsValid()
            ? ValidationResult.valid()
            : ValidationResult.unavailable();
      }
      throw e;
    } catch (Throwable t) {
      return handleError(t);
    }
  }

  ValidationResult handleError(Throwable t) {
    if (ctx.failSafe()) {
      return ctx.failSafeReturnsValid()
          ? ValidationResult.valid()
          : ValidationResult.unavailable();
    }
    if (t instanceof Error) {
      throw (Error) t;
    }
    if (t instanceof RuntimeException) {
      throw (RuntimeException) t;
    }
    throw new RuntimeException(ctx.errorMessage("Validation error"), t);
  }

  void cleanup() {
    depth = 0;
    if (visited != null) {
      Arrays.fill(visited, 0, visitedCount, null);
      visitedCount = 0;
    }
  }

  private boolean containsIdentity(Object obj) {
    for (int i = 0; i < visitedCount; i++) {
      if (visited[i] == obj) {
        return true;
      }
    }
    return false;
  }

  private void addVisited(Object obj) {
    if (visitedCount == visited.length) {
      int newLen = Math.min(
          visited.length * 2,
          ctx.maxTrackedObjects());
      if (newLen == visited.length) {
        return;
      }
      visited = Arrays.copyOf(visited, newLen);
    }
    visited[visitedCount++] = obj;
  }

  private void removeVisited(Object obj) {
    for (int i = visitedCount - 1; i >= 0; i--) {
      if (visited[i] == obj) {
        visited[i] = visited[--visitedCount];
        visited[visitedCount] = null;
        return;
      }
    }
  }
}