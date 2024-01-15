package uk.nhs.hee.tis.trainee.forms.annotations;

import java.time.LocalDate;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

@Component
public class LegalAgeValidator implements ConstraintValidator<LegalAgeValidation, LocalDate> {

  private int adultAgeYears;

  @Override
  public void initialize(LegalAgeValidation constraintAnnotation) {
    this.adultAgeYears = constraintAnnotation.adultAgeYears();
  }

  /**
   * Does the birthdate mean the person is an adult.
   *
   * @param theDate The object to validate.
   * @param cxt     The context in which the constraint is evaluated.
   *
   * @return True if the person is an adult, otherwise false.
   */
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
