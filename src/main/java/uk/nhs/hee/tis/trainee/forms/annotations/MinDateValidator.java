package uk.nhs.hee.tis.trainee.forms.annotations;

import java.time.LocalDate;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class MinDateValidator implements ConstraintValidator<MinDateValidation, LocalDate> {

  private int maxYearsAgo;

  @Override
  public void initialize(MinDateValidation constraintAnnotation) {
    this.maxYearsAgo = constraintAnnotation.maxYearsAgo();
  }

  /**
   * Is the date not too far in the past.
   *
   * @param theDate The object to validate.
   * @param cxt     The context in which the constraint is evaluated.
   *
   * @return True if the date is not too far in the past, otherwise false.
   */
  public boolean isValid(LocalDate theDate, ConstraintValidatorContext cxt) {
    LocalDate oldestDate = LocalDate.now().minusYears(this.maxYearsAgo);
    boolean isValid = theDate.isAfter(oldestDate);
    if (!isValid) {
      cxt.disableDefaultConstraintViolation();
      cxt.buildConstraintViolationWithTemplate(
              "The date cannot be " + this.maxYearsAgo + " years or more ago")
          .addConstraintViolation();
    }
    return isValid;
  }
}
