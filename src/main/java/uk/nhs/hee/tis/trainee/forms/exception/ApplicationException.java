package uk.nhs.hee.tis.trainee.forms.exception;

public class ApplicationException extends RuntimeException {

  /**
   * @param message The exception message for the Exception
   * @param cause The cause to wrap
   */
  public ApplicationException(String message, Exception cause) {
    super(message, cause);
  }

  /**
   * @param message The exception message for the Exception
   */
  public ApplicationException(String message) {
    super(message);
  }
}
