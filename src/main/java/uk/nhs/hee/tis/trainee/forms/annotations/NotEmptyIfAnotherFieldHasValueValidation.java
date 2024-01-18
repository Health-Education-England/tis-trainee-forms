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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * Validates that field {@code dependFieldName} is not null or empty if field {@code fieldName} has
 * value {@code fieldValue}
 * (or does NOT have value {@code fieldValue} if {@code isNotCondition is true}).
 **/
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Repeatable(NotEmptyIfAnotherFieldHasValueValidation.List.class)
@Constraint(validatedBy = NotEmptyIfAnotherFieldHasValueValidator.class)
@Documented
public @interface NotEmptyIfAnotherFieldHasValueValidation {

  /**
   * The constraining field.
   *
   * @return The constraining field.
   */
  String fieldName();

  /**
   * The constraining field value.
   *
   * @return The constraining field value.
   */
  String fieldValue();

  /**
   * Require the field to NOT have the field value.
   *
   * @return true if the field must NOT have the field value.
   */
  boolean isNotCondition() default false;

  /**
   * The dependent field, that cannot have a null or empty value if the constraining field has the
   * constraining value.
   *
   * @return The dependent field name.
   */
  String dependFieldName();

  /**
   * The error message if the dependent value is not valid.
   *
   * @return The error message.
   */
  String message() default "This field must have a value";

  /**
   * The group of constraints.
   *
   * @return The array of constraint classes.
   */
  public Class<?>[] groups() default {};

  /**
   * Additional information about the annotation.
   *
   * @return The payload extension.
   */
  public Class<? extends Payload>[] payload() default {};

  @Target({TYPE, ANNOTATION_TYPE})
  @Retention(RUNTIME)
  @Documented
  @interface List {

    NotEmptyIfAnotherFieldHasValueValidation[] value();
  }

}