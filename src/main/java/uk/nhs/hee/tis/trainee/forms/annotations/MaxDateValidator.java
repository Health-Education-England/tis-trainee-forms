package uk.nhs.hee.tis.trainee.forms.annotations;

import java.time.LocalDate;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class MaxDateValidator implements ConstraintValidator<MaxDateValidation, LocalDate> {

  private int maxYearsInFuture;

  @Override
  public void initialize(MaxDateValidation constraintAnnotation) {
    this.maxYearsInFuture = constraintAnnotation.maxYearsInFuture();
  }

  /**
   * Is the date not too far in the future.
   *
   * @param theDate The object to validate.
   * @param cxt     The context in which the constraint is evaluated.
   *
   * @return True if the date is not too far in the future, otherwise false.
   */
  public boolean isValid(LocalDate theDate, ConstraintValidatorContext cxt) {
    LocalDate biggestDate = LocalDate.now().plusYears(this.maxYearsInFuture);
    boolean isValid = !theDate.isAfter(biggestDate);
    if (!isValid) {
      cxt.disableDefaultConstraintViolation();
      cxt.buildConstraintViolationWithTemplate(
              "The date cannot be " + this.maxYearsInFuture + " years or more in the future")
          .addConstraintViolation();
    }
    return isValid;
  }
}
