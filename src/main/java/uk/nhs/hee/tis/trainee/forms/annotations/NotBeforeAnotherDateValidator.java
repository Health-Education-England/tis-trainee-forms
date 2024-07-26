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

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import org.apache.commons.beanutils.BeanUtils;

/**
 * Implementation of {@link NotBeforeAnotherDateValidation} validator.
 **/
public class NotBeforeAnotherDateValidator
    implements ConstraintValidator<NotBeforeAnotherDateValidation, Object> {

  private String fieldName;
  private String dependFieldName;
  private String message;

  @Override
  public void initialize(NotBeforeAnotherDateValidation annotation) {
    fieldName = annotation.fieldName();
    dependFieldName = annotation.dependFieldName();
    message = annotation.message();
  }

  /**
   * Convenience initialisation method for unit testing.
   *
   * @param fieldName       The field name.
   * @param dependFieldName The dependent field name.
   * @param message         The error message if not valid.
   */
  protected void initWithValues(String fieldName, String dependFieldName, String message) {
    this.fieldName = fieldName;
    this.dependFieldName = dependFieldName;
    this.message = message;
  }

  /**
   * Is the value of the dependent field valid, i.e. is it not before fieldName value.
   *
   * @param value The object to validate.
   * @param ctx   The context in which the constraint is evaluated.
   * @return True if the dependent field is valid, otherwise false.
   */
  @Override
  public boolean isValid(Object value, ConstraintValidatorContext ctx) {

    if (value == null) {
      return true;
    }

    try {
      boolean isOk = true;
      String fieldValue = BeanUtils.getProperty(value, fieldName);
      String dependFieldValue = BeanUtils.getProperty(value, dependFieldName);
      if (fieldValue == null) {
        return true;
      }
      if (dependFieldValue == null) {
        isOk = false;
      }
      if (isOk) {
        LocalDate fieldDate = LocalDate.parse(fieldValue);
        LocalDate dependFieldDate = LocalDate.parse(dependFieldValue);

        if (dependFieldDate.isBefore(fieldDate)) {
          isOk = false;
        }
      }
      if (!isOk) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(dependFieldName)
            .addConstraintViolation();
        return false;
      }

    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
      throw new RuntimeException(ex);
    }

    return true;
  }
}
