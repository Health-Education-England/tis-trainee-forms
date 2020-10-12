package uk.nhs.hee.tis.trainee.forms.exception;

public class ApplicationException extends RuntimeException {

  public ApplicationException(String s, Exception e) {
    super(s, e);
  }

  public ApplicationException(String s) {
    super(s);
  }
}
