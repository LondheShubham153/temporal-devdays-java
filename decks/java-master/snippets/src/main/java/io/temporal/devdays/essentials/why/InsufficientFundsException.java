package io.temporal.devdays.essentials.why;

/**
 * Thrown by the bank when the source account cannot cover the transfer. Its class name is the
 * failure type the Workflow's Retry Policy lists as non-retryable.
 */
public class InsufficientFundsException extends RuntimeException {
  public InsufficientFundsException(String message) {
    super(message);
  }
}
