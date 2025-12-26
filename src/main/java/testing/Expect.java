package testing;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class Expect {

  private final Object actualValue;
  private final boolean isNegated;

  Expect(Object actualValue, boolean isNegated) {
    this.actualValue = actualValue;
    this.isNegated = isNegated;
  }

  public Expect never() {
    return new Expect(actualValue, true);
  }

  public void toBe(Object expected) {
    if (expected instanceof Condition condition) {
      Condition.Result result = condition.test(actualValue);
      assertCondition(
          result.passed(),
          result.message() != null
              ? result.message()
              : String.format("Expected %s to %sbe %s",
                  Readable.format(actualValue), negationTag(), condition));
    } else {
      assertCondition(
          Objects.equals(actualValue, expected),
          String.format("Expected %s to %sbe %s",
              Readable.format(actualValue), negationTag(), 
              Readable.format(expected)));
    }
  }

  public void toEqual(Object expected) {
    toBe(expected);
  }

  public void toBeDefined() {
    assertCondition(
        actualValue != null,
        String.format("Expected %s to %sbe defined",
            Readable.format(actualValue), negationTag()));
  }

  public void toBeCloseTo(double expected, int precision) {
    assertType(Number.class, "toBeCloseTo");
    double actual = ((Number) actualValue).doubleValue();

    boolean isClose;
    if (expected == Double.POSITIVE_INFINITY) {
      isClose = actual == Double.POSITIVE_INFINITY;
    } else if (expected == Double.NEGATIVE_INFINITY) {
      isClose = actual == Double.NEGATIVE_INFINITY;
    } else {
      isClose = Math.abs(actual - expected) < Math.pow(10, -precision) / 2;
    }

    assertCondition(
        isClose,
        String.format("Expected %s to %sbe close to %s",
            Readable.format(actualValue), negationTag(), expected));
  }

  public void toBeGreaterThan(double expected) {
    assertType(Number.class, "toBeGreaterThan");
    double actual = ((Number) actualValue).doubleValue();
    assertCondition(
        actual > expected,
        String.format("Expected %s to %sbe greater than %s",
            Readable.format(actualValue), negationTag(), expected));
  }

  public void toBeGreaterThanOrEqual(double expected) {
    assertType(Number.class, "toBeGreaterThanOrEqual");
    double actual = ((Number) actualValue).doubleValue();
    assertCondition(
        actual >= expected,
        String.format("Expected %s to %sbe greater than or equal to %s",
            Readable.format(actualValue), negationTag(), expected));
  }

  public void toBeLessThan(double expected) {
    assertType(Number.class, "toBeLessThan");
    double actual = ((Number) actualValue).doubleValue();
    assertCondition(
        actual < expected,
        String.format("Expected %s to %sbe less than %s",
            Readable.format(actualValue), negationTag(), expected));
  }

  public void toBeLessThanOrEqual(double expected) {
    assertType(Number.class, "toBeLessThanOrEqual");
    double actual = ((Number) actualValue).doubleValue();
    assertCondition(
        actual <= expected,
        String.format("Expected %s to %sbe less than or equal to %s",
            Readable.format(actualValue), negationTag(), expected));
  }

  public void toBeNull() {
    assertCondition(
        actualValue == null,
        String.format("Expected %s to %sbe null",
            Readable.format(actualValue), negationTag()));
  }

  public void toBeNaN() {
    boolean isNaN = actualValue instanceof Number number 
        && Double.isNaN(number.doubleValue());
    assertCondition(
        isNaN,
        String.format("Expected %s to %sbe NaN",
            Readable.format(actualValue), negationTag()));
  }

  public void toHaveLength(int expectedLength) {
    int actualLength = getLength();
    assertCondition(
        actualLength == expectedLength,
        String.format("Expected %s to %shave length %d",
            Readable.format(actualValue), negationTag(), expectedLength));
  }

  public void toBeFalsy() {
    boolean isFalsy = actualValue == null
        || Boolean.FALSE.equals(actualValue)
        || (actualValue instanceof Number number && number.doubleValue() == 0);
    assertCondition(
        isFalsy,
        String.format("Expected %s to %sbe falsy",
            Readable.format(actualValue), negationTag()));
  }

  public void toBeTruthy() {
    boolean isTruthy = actualValue != null
        && !Boolean.FALSE.equals(actualValue)
        && !(actualValue instanceof Number number && number.doubleValue() == 0);
    assertCondition(
        isTruthy,
        String.format("Expected %s to %sbe truthy",
            Readable.format(actualValue), negationTag()));
  }

  public void toThrow() {
    toThrow(null);
  }

  public void toThrow(String expectedMessage) {
    if (!(actualValue instanceof Testing.ThrowingRunnable runnable)) {
      throw new AssertionError(String.format(
          "Expected %s to be a ThrowingRunnable", Readable.format(actualValue)));
    }

    Throwable caughtException = null;
    try {
      runnable.run();
    } catch (Throwable thrown) {
      caughtException = thrown;
    }

    if (expectedMessage != null) {
      if (caughtException == null) {
        assertCondition(false, String.format(
            "Expected %s to %sthrow an error",
            Readable.format(actualValue), negationTag()));
      } else {
        String actualMessage = caughtException.getMessage();
        assertCondition(
            Objects.equals(actualMessage, expectedMessage),
            String.format("Got error %s expected to %sthrow %s",
                Readable.format(actualMessage), negationTag(),
                Readable.format(expectedMessage)));
      }
    } else {
      assertCondition(
          caughtException != null,
          String.format("Expected %s to %sthrow an error",
              Readable.format(actualValue), negationTag()));
    }
  }

  public void toBeInstanceOf(Class<?> expectedType) {
    assertCondition(
        expectedType.isInstance(actualValue),
        String.format("Expected %s to %sbe instance of %s",
            Readable.format(actualValue), negationTag(), 
            expectedType.getSimpleName()));
  }

  public void toNotBe(Object unexpected) {
    assertCondition(
        !Objects.equals(actualValue, unexpected),
        String.format("Expected %s to not be %s",
            Readable.format(actualValue), Readable.format(unexpected)));
  }

  public void toContain(String expectedSubstring) {
    if (!(actualValue instanceof String actualString)) {
      throw new AssertionError(String.format(
          "toContain requires String but got %s", 
          Readable.format(actualValue)));
    }
    assertCondition(
        actualString.contains(expectedSubstring),
        String.format("Expected %s to %scontain %s",
            Readable.format(actualValue), negationTag(), 
            Readable.format(expectedSubstring)));
  }

  private void assertCondition(boolean conditionMet, String failureMessage) {
    boolean passed = isNegated ? !conditionMet : conditionMet;
    if (!passed) {
      throw new AssertionError(failureMessage);
    }
  }

  private void assertType(Class<?> requiredType, String methodName) {
    if (!requiredType.isInstance(actualValue)) {
      throw new AssertionError(String.format(
          "%s requires %s but got %s",
          methodName, requiredType.getSimpleName(), 
          Readable.format(actualValue)));
    }
  }

  private String negationTag() {
    return isNegated ? "not " : "";
  }

  private int getLength() {
    if (actualValue == null) {
      throw new AssertionError("Cannot get length of null");
    }
    if (actualValue instanceof String string) {
      return string.length();
    }
    if (actualValue instanceof Collection<?> collection) {
      return collection.size();
    }
    if (actualValue instanceof Map<?, ?> map) {
      return map.size();
    }
    if (actualValue.getClass().isArray()) {
      return java.lang.reflect.Array.getLength(actualValue);
    }
    throw new AssertionError(String.format(
        "Cannot get length of %s", actualValue.getClass().getSimpleName()));
  }

  public static Condition any(Class<?> expectedType) {
    return new Condition(expectedType.getSimpleName(), 
        value -> expectedType.isInstance(value)
            ? Condition.Result.pass()
            : Condition.Result.fail(String.format(
                "value is supposed to %%sbe of type %s, got %s",
                expectedType.getSimpleName(),
                value == null ? "null" : value.getClass().getSimpleName())));
  }

  public static Condition type(Class<?> expectedType) {
    return any(expectedType);
  }

  public static Condition similar(Object expected) {
    return new Condition("similar", value -> {
      DeepDiff diff = DeepDiff.compare(expected, value);
      if (diff.hasDifferences()) {
        return Condition.Result.fail(String.format(
            "value is supposed to %%sbe similar, differences:%n%s",
            diff.format()));
      }
      return Condition.Result.pass();
    });
  }

  public static Condition nothing() {
    return new Condition("nothing", value -> value == null
        ? Condition.Result.pass()
        : Condition.Result.fail("Expected nothing (null)"));
  }
}