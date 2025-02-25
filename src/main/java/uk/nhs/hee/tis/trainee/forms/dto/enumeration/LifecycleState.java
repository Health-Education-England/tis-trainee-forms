/*
 * The MIT License (MIT)
 *
 * Copyright 2020 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.dto.enumeration;

import java.util.Set;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.AbstractFormR;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

public enum LifecycleState {
  APPROVED,
  DELETED,
  DRAFT,
  REJECTED,
  SUBMITTED,
  UNSUBMITTED,
  WITHDRAWN;

  private Set<Class<? extends AbstractForm>> allowedFormTypes;
  private Set<LifecycleState> allowedTransitions;

  static {
    APPROVED.allowedTransitions = Set.of();
    APPROVED.allowedFormTypes = Set.of(LtftForm.class);

    DELETED.allowedTransitions = Set.of();
    DELETED.allowedFormTypes = Set.of(AbstractFormR.class);

    DRAFT.allowedTransitions = Set.of(DELETED, SUBMITTED);
    DRAFT.allowedFormTypes = Set.of(AbstractForm.class);

    REJECTED.allowedTransitions = Set.of();
    REJECTED.allowedFormTypes = Set.of(LtftForm.class);

    SUBMITTED.allowedTransitions = Set.of(APPROVED, DELETED, REJECTED, UNSUBMITTED, WITHDRAWN);
    SUBMITTED.allowedFormTypes = Set.of(AbstractForm.class);

    UNSUBMITTED.allowedTransitions = Set.of(SUBMITTED, WITHDRAWN);
    UNSUBMITTED.allowedFormTypes = Set.of(AbstractForm.class);

    WITHDRAWN.allowedTransitions = Set.of();
    WITHDRAWN.allowedFormTypes = Set.of(LtftForm.class);
  }

  /**
   * Checks whether the transition from the form's current state to the new state is allowed.
   *
   * @param form              The form to check.
   * @param newLifecycleState The new target state.
   * @return Whether the transition is allowed, false if the form's state is null.
   */
  public static boolean canTransitionTo(AbstractForm form, LifecycleState newLifecycleState) {
    LifecycleState currentState = form.getLifecycleState();

    return currentState != null && currentState.allowedTransitions.contains(newLifecycleState)
        && newLifecycleState.allowedFormTypes.stream()
        .anyMatch(type -> type.isAssignableFrom(form.getClass()));
  }
}
