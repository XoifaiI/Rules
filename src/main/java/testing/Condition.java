package testing;

import java.util.function.Function;

public final class Condition {

  private final String conditionName;
  private final Function<Object, Result> testFunction;

  public Condition(String conditionName, Function<Object, Result> testFunction) {
    this.conditionName = conditionName;
    this.testFunction = testFunction;
  }

  public Result test(Object value) {
    return testFunction.apply(value);
  }

  @Override
  public String toString() {
    return conditionName;
  }

  public static final class Result {

    private final boolean passed;
    private final String failureMessage;

    private Result(boolean passed, String failureMessage) {
      this.passed = passed;
      this.failureMessage = failureMessage;
    }

    public static Result pass() {
      return new Result(true, null);
    }

    public static Result fail(String failureMessage) {
      return new Result(false, failureMessage);
    }

    public boolean passed() {
      return passed;
    }

    public String message() {
      return failureMessage;
    }
  }
}