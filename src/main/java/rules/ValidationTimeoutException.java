package rules;

public final class ValidationTimeoutException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ValidationTimeoutException(String message) {
    super(message);
  }
}