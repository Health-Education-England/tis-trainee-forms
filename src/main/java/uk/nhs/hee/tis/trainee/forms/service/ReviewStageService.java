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
 */

package uk.nhs.hee.tis.trainee.forms.service;

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.trainee.forms.config.ReviewWorkflowProperties;
import uk.nhs.hee.tis.trainee.forms.config.StateStage;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.ReviewStageStatus;

/**
 * A service for managing review stage transitions on LTFT forms.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>On entering SUBMITTED: set the review stage to the first configured stage for the form's
 *       DBC, or null if no workflow is configured.</li>
 *   <li>On leaving SUBMITTED for any reason: clear the review stage to null.</li>
 *   <li>On re-entering SUBMITTED after UNSUBMITTED: restart from the first stage.</li>
 *   <li>Any review stage may UNSUBMIT a form.</li>
 *   <li>Only the final review stage may approve, reject, or otherwise terminate a form.</li>
 * </ul>
 */
@Slf4j
@Service
public class ReviewStageService {

  private final ReviewWorkflowProperties reviewWorkflowProperties;

  ReviewStageService(ReviewWorkflowProperties reviewWorkflowProperties) {
    this.reviewWorkflowProperties = reviewWorkflowProperties;
  }

  /**
   * Resolve the {@link ReviewStageStatus} to set on the form when transitioning to the given target
   * lifecycle state.
   *
   * @param form        The form being transitioned.
   * @param targetState The lifecycle state being transitioned to.
   * @return The review stage to record, or {@code null} if the stage should be cleared.
   */
  @Nullable
  public ReviewStageStatus resolveReviewStageForTransition(LtftForm form,
      LifecycleState targetState) {
    if (targetState != SUBMITTED) {
      // Leaving SUBMITTED for any reason (APPROVED, REJECTED, UNSUBMITTED, WITHDRAWN) clears stage.
      return null;
    }

    // Entering (or re-entering) SUBMITTED always starts from the first stage.
    String dbc = getDesignatedBodyCode(form);
    List<StateStage> stages = reviewWorkflowProperties.getReviewWorkflows().get(dbc);

    if (stages == null || stages.isEmpty()) {
      log.debug("No review workflow configured for DBC '{}', setting review stage to null.", dbc);
      return null;
    }

    StateStage first = stages.get(0);
    log.debug("Resolving first review stage for DBC '{}': index=0, label='{}'.", dbc,
        first.label());
    return new ReviewStageStatus(0, first.label());
  }

  /**
   * Resolve the next {@link ReviewStageStatus} when an admin advances the review of a form.
   *
   * <p>If the form is already at the final configured stage, {@link Optional#empty()} is returned
   * to indicate that no further advancement is possible. Transitioning to APPROVED must be
   * performed separately via the normal status-update path.
   *
   * @param form The form whose review is being advanced.
   * @return The next review stage, or empty if the form is already at the final stage.
   */
  public Optional<ReviewStageStatus> resolveAdvance(LtftForm form) {
    String dbc = getDesignatedBodyCode(form);
    List<StateStage> stages = reviewWorkflowProperties.getReviewWorkflows().get(dbc);

    if (stages == null || stages.isEmpty()) {
      log.debug("No review workflow for DBC '{}'; no stages to advance through.", dbc);
      return Optional.empty();
    }

    ReviewStageStatus current =
        form.getStatus() != null && form.getStatus().current() != null
            ? form.getStatus().current().reviewStage()
            : null;
    int currentIndex = current != null ? current.index() : 0;

    if (currentIndex >= stages.size() - 1) {
      log.debug("Form {} is already at the final review stage (index {}); no advancement possible.",
          form.getId(), currentIndex);
      return Optional.empty();
    }

    int nextIndex = currentIndex + 1;
    StateStage next = stages.get(nextIndex);
    log.debug("Advancing form {} from review stage {} to {} ('{}').",
        form.getId(), currentIndex, nextIndex, next.label());
    return Optional.of(new ReviewStageStatus(nextIndex, next.label()));
  }

  /**
   * Determine whether the form's current review stage permits a transition to the given
   * lifecycle state.
   *
   * <p>UNSUBMITTED is always permitted from any review stage. All other lifecycle states
   * (APPROVED, REJECTED, WITHDRAWN) require the form to be at the final configured stage. If no
   * workflow is configured for the form's DBC the transition is always permitted.
   *
   * @param form        The form to check.
   * @param targetState The lifecycle state the form is being transitioned to.
   * @return {@code true} if the transition is permitted, {@code false} otherwise.
   */
  public boolean canTransitionToLifecycleState(LtftForm form, LifecycleState targetState) {
    if (targetState == UNSUBMITTED) {
      return true;
    }

    String dbc = getDesignatedBodyCode(form);
    List<StateStage> stages = reviewWorkflowProperties.getReviewWorkflows().get(dbc);

    if (stages == null || stages.isEmpty()) {
      return true;
    }

    ReviewStageStatus currentReviewStage =
        form.getStatus() != null && form.getStatus().current() != null
            ? form.getStatus().current().reviewStage()
            : null;

    if (currentReviewStage == null) {
      log.warn("Form {} has a review workflow but no current review stage; "
          + "denying transition to {}.", form.getId(), targetState);
      return false;
    }

    boolean atFinalStage = currentReviewStage.index() == stages.size() - 1;
    if (!atFinalStage) {
      log.warn("Form {} is at review stage {} of {} but attempted to transition to {}.",
          form.getId(), currentReviewStage.index(), stages.size() - 1, targetState);
    }
    return atFinalStage;
  }

  @Nullable
  private String getDesignatedBodyCode(LtftForm form) {
    if (form.getContent() == null || form.getContent().programmeMembership() == null) {
      return null;
    }
    return form.getContent().programmeMembership().designatedBodyCode();
  }
}

