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

package uk.nhs.hee.tis.trainee.forms.service;

import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.content.FormContent;

/**
 * A service interface for managing submission history of forms.
 *
 * @param <F> The type of form content.
 */
public interface SubmissionHistoryService<F extends AbstractAuditedForm<? extends FormContent>> {

  /**
   * Take a snapshot of the form and save it to the repository.
   *
   * @param form The form to be saved.
   */
  void takeSnapshot(F form);

  /**
   * Move all form submissions from one trainee to another. Assumes that fromTraineeId and
   * toTraineeId are valid.
   *
   * @param fromTraineeId The trainee ID to move forms from.
   * @param toTraineeId   The trainee ID to move forms to.
   * @return The number of form submissions moved.
   */
  int moveHistory(String fromTraineeId, String toTraineeId);
}
