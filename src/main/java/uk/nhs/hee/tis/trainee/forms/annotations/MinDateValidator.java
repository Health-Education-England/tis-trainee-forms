package uk.nhs.hee.tis.trainee.forms.annotations;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
public class MinDateValidator implements ConstraintValidator<MinDateValidation, LocalDate> {

  private int maxYearsAgo;

  @Override
  public void initialize(MinDateValidation constraintAnnotation) {
    this.maxYearsAgo = constraintAnnotation.maxYearsAgo();
  }

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
