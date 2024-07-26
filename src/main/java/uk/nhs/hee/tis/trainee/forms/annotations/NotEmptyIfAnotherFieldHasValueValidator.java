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
import org.apache.commons.beanutils.BeanUtils;

/**
 * Implementation of {@link NotEmptyIfAnotherFieldHasValueValidation} validator.
 **/
public class NotEmptyIfAnotherFieldHasValueValidator
    implements ConstraintValidator<NotEmptyIfAnotherFieldHasValueValidation, Object> {

  private String fieldName;
  private String expectedFieldValue;
  private boolean isNotCondition;
  private String dependFieldName;
  private String message;

  @Override
  public void initialize(NotEmptyIfAnotherFieldHasValueValidation annotation) {
    fieldName = annotation.fieldName();
    expectedFieldValue = annotation.fieldValue();
    isNotCondition = annotation.isNotCondition();
    dependFieldName = annotation.dependFieldName();
    message = annotation.message();
  }

  /**
   * Convenience initialisation method for unit testing.
   *
   * @param fieldName          The field name.
   * @param expectedFieldValue The expected field value.
   * @param isNotCondition     Whether to test for equality or not.
   * @param dependFieldName    The dependent field name.
   * @param message            The error message.
   */
  protected void initWithValues(String fieldName, String expectedFieldValue,
      boolean isNotCondition, String dependFieldName, String message) {
    this.fieldName = fieldName;
    this.expectedFieldValue = expectedFieldValue;
    this.isNotCondition = isNotCondition;
    this.dependFieldName = dependFieldName;
    this.message = message;
  }

  /**
   * Is the value of the dependent field valid? i.e. is it a string of length at least 1 if the
   * fieldName field has expectedFieldValue value; otherwise it can be null or empty. If
   * isNotCondition, then this applies if fieldName does NOT have expectedFieldValue value.
   *
   * @param value The object to validate.
   * @param ctx   The context in which the constraint is evaluated.
   * @return True if the dependent field is valid, otherwise false.
   */
  @Override
  public boolean isValid(Object value, ConstraintValidatorContext ctx) {

    if (value == null) {
      return !isNotCondition;
    }

    try {
      String fieldValue = BeanUtils.getProperty(value, fieldName);
      String dependFieldValue = BeanUtils.getProperty(value, dependFieldName);

      if ((dependFieldValue == null || dependFieldValue.isEmpty())
          && (expectedFieldValue.equals(fieldValue) == !isNotCondition)) {
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
