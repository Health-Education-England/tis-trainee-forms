/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
 *
 */

package uk.nhs.hee.tis.trainee.forms.dto.validation;

import jakarta.validation.ConstraintViolation;
import java.util.Set;

/**
 * Utilities for testing DTO validations.
 */
public class ValidationTestUtils {

  /**
   * Check whether a validation result contains at least one violation for the given field and exact
   * message text.
   *
   * @param violations The set of constraint violations returned by the validator.
   * @param field      The property path to match.
   * @param message    The expected validation message text.
   * @param <T>        The type of the DTO that was validated.
   * @return {@code true} when a violation exists for the field with the expected message, otherwise
   * {@code false}.
   */
  public static <T> boolean hasViolationForFieldWithMessage(
      Set<ConstraintViolation<T>> violations, String field, String message) {
    return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field)
        && v.getMessage().equals(message));
  }

  /**
   * Check whether a validation result contains at least one violation for the given field,
   * regardless of message.
   *
   * @param violations The set of constraint violations returned by the validator.
   * @param field      The property path to match.
   * @param <T>        The type of the DTO that was validated.
   * @return {@code true} when any violation exists for the field, otherwise {@code false}.
   */
  public static <T> boolean hasViolationForField(Set<ConstraintViolation<T>> violations,
      String field) {
    return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field));
  }
}
