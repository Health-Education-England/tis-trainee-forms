package uk.nhs.hee.tis.trainee.forms.annotations;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Component
public class LegalAgeValidator implements ConstraintValidator<LegalAgeValidation, LocalDate> {

  private int adultAgeYears;

  @Override
  public void initialize(LegalAgeValidation constraintAnnotation) {
    this.adultAgeYears = constraintAnnotation.adultAgeYears();
  }

  public boolean isValid(LocalDate theDate, ConstraintValidatorContext cxt) {
    LocalDate adultBorn = LocalDate.now().minusYears(adultAgeYears);
    boolean isValid = !theDate.isAfter(adultBorn);
    if (!isValid) {
      cxt.disableDefaultConstraintViolation();
      cxt.buildConstraintViolationWithTemplate(
              "You must be at least " + this.adultAgeYears + " years old")
          .addConstraintViolation();
    }
    return isValid;
  }
}
