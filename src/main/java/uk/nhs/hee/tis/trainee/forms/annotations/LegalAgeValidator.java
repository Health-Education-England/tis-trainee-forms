/*
 * The MIT License (MIT)
 * Copyright 2024 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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
    if (theDate == null) {
      return true;
    }
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
