package uk.nhs.hee.tis.trainee.forms.annotations;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
public class MaxDateValidator implements ConstraintValidator<MaxDateValidation, LocalDate> {

  private int maxYearsInFuture;

  @Override
  public void initialize(MaxDateValidation constraintAnnotation) {
    this.maxYearsInFuture = constraintAnnotation.maxYearsInFuture();
  }

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
