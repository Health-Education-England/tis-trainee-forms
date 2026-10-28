/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.hee.tis.trainee.forms.annotations;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.nhs.hee.tis.trainee.forms.dto.LtftReviewOutcomeDto;
import uk.nhs.hee.tis.trainee.forms.model.ReviewOutcome.Outcome;

/**
 * Implementation of {@link LtftReviewOutcomeValidation}.
 */
public class LtftReviewOutcomeValidator
    implements ConstraintValidator<LtftReviewOutcomeValidation, LtftReviewOutcomeDto> {

  @Override
  public boolean isValid(LtftReviewOutcomeDto reviewOutcome, ConstraintValidatorContext context) {
    if (reviewOutcome == null || reviewOutcome.outcome() == null) {
      return true;
    }

    boolean valid = true;
    context.disableDefaultConstraintViolation();

    if (reviewOutcome.outcome() == Outcome.RECOMMEND_APPROVAL) {
      if (reviewOutcome.outcomeDetails() == null) {
        addFieldViolation(context, "outcomeDetails",
            "must not be null when recommending approval");
        valid = false;
      }
    } else {
      if (reviewOutcome.outcomeDetails() != null) {
        addFieldViolation(context, "outcomeDetails",
            "must be null when recommending rejection");
        valid = false;
      }
      if (reviewOutcome.outcomeComments() == null || reviewOutcome.outcomeComments().isBlank()) {
        addFieldViolation(context, "outcomeComments",
            "must not be blank when recommending rejection");
        valid = false;
      }
    }

    return valid;
  }

  /**
   * Add a field violation to the validation context.
   *
   * @param context The validation context.
   * @param field   The field name.
   * @param message The violation message.
   */
  private void addFieldViolation(ConstraintValidatorContext context, String field, String message) {
    context.buildConstraintViolationWithTemplate(message)
        .addPropertyNode(field)
        .addConstraintViolation();
  }
}
