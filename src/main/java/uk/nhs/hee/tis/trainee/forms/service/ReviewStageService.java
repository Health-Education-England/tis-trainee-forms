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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.trainee.forms.config.ReviewWorkflowProperties;
import uk.nhs.hee.tis.trainee.forms.config.StateStage;
import uk.nhs.hee.tis.trainee.forms.dto.ReviewWorkflowDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.ReviewStageStatus;

/**
 * A service for managing review stage transitions on LTFT forms.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>On entering SUBMITTED: set the review stage to the first <em>enabled</em> configured stage
 *       for the form's DBC, or null if no enabled stages are configured.</li>
 *   <li>On leaving SUBMITTED for any reason: clear the review stage to null.</li>
 *   <li>On re-entering SUBMITTED after UNSUBMITTED: restart from the first enabled stage.</li>
 *   <li>Any review stage may UNSUBMIT a form.</li>
 *   <li>A disabled review stage may be advanced or exited as if it were enabled.</li>
 *   <li>Advancing always moves to the next <em>enabled</em> stage, skipping disabled ones.</li>
 *   <li>Only the <em>effective</em> final stage (the stage after which no further enabled stages
 *       exist) may approve, reject, or otherwise terminate a form.</li>
 *   <li>If no enabled stages are configured for a DBC, that DBC is treated as having no review
 *       workflow at all.</li>
 * </ul>
 */
@Slf4j
@Service
public class ReviewStageService {

  /**
   * The label for the implicit terminal review stage that is appended after all configured stages.
   * This stage allows admins to record a reason for completing the final review before
   * transitioning the form to APPROVED, REJECTED, or WITHDRAWN.
   */
  static final String TERMINAL_STAGE_LABEL = "Review complete";

  private final ReviewWorkflowProperties reviewWorkflowProperties;

  ReviewStageService(ReviewWorkflowProperties reviewWorkflowProperties) {
    this.reviewWorkflowProperties = reviewWorkflowProperties;
  }

  /**
   * Build the {@link ReviewWorkflowDto} for the given form.
   *
   * <p>The {@code stages} list contains only enabled stages, with one exception: if the form is
   * currently SUBMITTED and its active review stage is disabled (e.g. the stage was disabled after
   * the form entered it), that stage is also included at its correct position. When at least one
   * configured stage is enabled, an implicit terminal "Review complete" stage is appended.
   *
   * <p>The {@code currentStage} field is the zero-based index of the form's current review stage
   * within the returned {@code stages} list, or {@code null} if the form is not SUBMITTED or has
   * no active review stage.
   *
   * @param form The form to inspect.
   * @return The workflow DTO describing the visible stages and the form's current position.
   */
  public ReviewWorkflowDto getWorkflowDto(LtftForm form) {
    String dbc = getDesignatedBodyCode(form);
    List<StateStage> allStages = getConfiguredStages(dbc);

    ReviewStageStatus currentReviewStage = getCurrentReviewStage(form);
    boolean isSubmitted = form.getStatus() != null
        && form.getStatus().current() != null
        && form.getStatus().current().state() == SUBMITTED;
    Integer currentAbsoluteIndex = (isSubmitted && currentReviewStage != null)
        ? currentReviewStage.index() : null;

    List<String> visibleLabels = new ArrayList<>();
    Integer currentVisiblePosition = null;

    for (int i = 0; i < allStages.size(); i++) {
      StateStage stage = allStages.get(i);
      boolean isCurrent = currentAbsoluteIndex != null && currentAbsoluteIndex == i;
      if (stage.enabled() || isCurrent) {
        if (isCurrent) {
          currentVisiblePosition = visibleLabels.size();
        }
        visibleLabels.add(stage.label());
      }
    }

    // Append the implicit terminal stage if any configured stages are enabled.
    boolean anyEnabled = allStages.stream().anyMatch(StateStage::enabled);
    if (anyEnabled) {
      if (currentAbsoluteIndex != null && currentAbsoluteIndex == allStages.size()) {
        currentVisiblePosition = visibleLabels.size();
      }
      visibleLabels.add(TERMINAL_STAGE_LABEL);
    }

    return new ReviewWorkflowDto(visibleLabels, currentVisiblePosition);
  }

  /**
   * Resolve the review stage to apply when transitioning the form to a new lifecycle state.
   *
   * <p>When entering SUBMITTED the first <em>enabled</em> stage for the form's DBC is returned. If
   * no enabled stages are configured (or no workflow exists) {@code null} is returned, indicating
   * the form should behave as if no review workflow is in place.
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

    // Entering (or re-entering) SUBMITTED always starts from the first enabled stage.
    String dbc = getDesignatedBodyCode(form);
    List<StateStage> stages = getConfiguredStages(dbc);

    if (stages.isEmpty()) {
      log.debug("No review workflow configured for DBC '{}', setting review stage to null.", dbc);
      return null;
    }

    Optional<ReviewStageStatus> firstEnabled = nextEnabledStageAfter(stages, -1);
    if (firstEnabled.isPresent()) {
      log.debug("Resolving first enabled review stage for DBC '{}': index={}, label='{}'.",
          dbc, firstEnabled.get().index(), firstEnabled.get().label());
    } else {
      log.debug("No enabled review stages configured for DBC '{}'; setting review stage to null.",
          dbc);
    }
    return firstEnabled.orElse(null);
  }

  /**
   * Resolve the next {@link ReviewStageStatus} when an admin advances the review of a form.
   *
   * <p>Disabled stages are skipped: the next <em>enabled</em> stage after the current index is
   * returned. If no enabled stage exists after the current index (i.e. the form is at the
   * effective final configured stage), the implicit terminal "Review complete" stage is returned,
   * allowing the admin to record a reason before transitioning to a terminal lifecycle state.
   *
   * <p>If the form is already at the terminal stage, {@link Optional#empty()} is returned to
   * indicate that no further advancement is possible. Transitioning to APPROVED must be performed
   * separately via the normal status-update path.
   *
   * @param form The form whose review is being advanced.
   * @return The next review stage (including the implicit terminal stage), or empty if the form
   *     is already at the terminal stage.
   */
  public Optional<ReviewStageStatus> resolveAdvance(LtftForm form) {
    String dbc = getDesignatedBodyCode(form);
    List<StateStage> stages = getConfiguredStages(dbc);

    boolean anyEnabled = stages.stream().anyMatch(StateStage::enabled);
    if (!anyEnabled) {
      log.debug("All review stages disabled for DBC '{}'; no stages to advance through.", dbc);
      return Optional.empty();
    }

    ReviewStageStatus current = getCurrentReviewStage(form);
    if (current == null) {
      log.warn("Form {} has a review workflow but no current review stage; cannot advance.",
          form.getId());
      return Optional.empty();
    }

    int currentIndex = current.index();

    // Already at the terminal stage — no further advancement is possible.
    // Note, not stages.size() - 1, as the terminal stage is not part of the configured stages list.
    if (currentIndex == stages.size()) {
      log.debug("Form {} is already at the terminal review stage; no advancement possible.",
          form.getId());
      return Optional.empty();
    }

    Optional<ReviewStageStatus> next = nextEnabledStageAfter(stages, currentIndex);
    if (next.isPresent()) {
      log.debug("Advancing form {} from review stage index {} to {} ('{}').",
          form.getId(), currentIndex, next.get().index(), next.get().label());
      return next;
    }

    // At the effective final configured stage — advance to the implicit terminal stage.
    log.debug("Advancing form {} from final configured stage (index {}) to terminal stage.",
        form.getId(), currentIndex);
    return Optional.of(new ReviewStageStatus(stages.size(), TERMINAL_STAGE_LABEL));
  }


  /**
   * Determine whether the form's current review stage permits a transition to the given
   * lifecycle state.
   *
   * <p>UNSUBMITTED is always permitted from any review stage. All other lifecycle states
   * (APPROVED, REJECTED, WITHDRAWN) require the form to be at the implicit terminal stage,
   * i.e. past all configured stages.
   *
   * <p>If no workflow is configured for the form's DBC, or all configured stages are disabled,
   * the transition is always permitted.
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
    List<StateStage> stages = getConfiguredStages(dbc);

    if (stages.isEmpty()) {
      return true;
    }

    // If all stages are disabled treat as no workflow — allow any transition.
    boolean anyEnabled = stages.stream().anyMatch(StateStage::enabled);
    if (!anyEnabled) {
      log.debug("All review stages disabled for DBC '{}'; treating as no workflow.", dbc);
      return true;
    }

    ReviewStageStatus currentReviewStage = getCurrentReviewStage(form);
    if (currentReviewStage == null) {
      log.debug("Form {} has no review stage; treating as pre-workflow form — allowing transition "
          + "to {}.", form.getId(), targetState);
      return true;
    }

    // Terminal transitions (APPROVED, REJECTED, WITHDRAWN) are only permitted from the implicit
    // terminal stage, i.e. after all configured review stages have been completed.
    boolean atTerminalStage = currentReviewStage.index() >= stages.size();
    if (!atTerminalStage) {
      log.warn("Form {} is at review stage index {} but attempted to transition to {}; "
              + "the form must be advanced to the terminal stage first.",
          form.getId(), currentReviewStage.index(), targetState);
    }
    return atTerminalStage;
  }

  /**
   * Return the configured review-workflow stages for the given DBC code, or an empty list if no
   * workflow is configured.
   *
   * @param dbc The designated body code to look up.
   * @return The list of configured stages, never {@code null}.
   */
  private List<StateStage> getConfiguredStages(@Nullable String dbc) {
    if (dbc == null) {
      return List.of();
    }
    List<StateStage> stages = reviewWorkflowProperties.getReviewWorkflows().get(dbc);
    return stages != null ? stages : List.of();
  }

  /**
   * Return the current {@link ReviewStageStatus} for the given form, or {@code null} if the form
   * has no current status.
   *
   * @param form The form to inspect.
   * @return The current review stage, or {@code null}.
   */
  @Nullable
  private ReviewStageStatus getCurrentReviewStage(LtftForm form) {
    if (form.getStatus() == null || form.getStatus().current() == null) {
      return null;
    }
    return form.getStatus().current().reviewStage();
  }

  /**
   * Find the next enabled stage in {@code stages} whose index is strictly greater than
   * {@code fromIndex}.
   *
   * <p>Pass {@code -1} as {@code fromIndex} to find the very first enabled stage.
   *
   * @param stages    The ordered list of configured stages.
   * @param fromIndex The index to start searching <em>after</em> (exclusive).
   * @return The first enabled stage found, or empty if none exists.
   */
  private Optional<ReviewStageStatus> nextEnabledStageAfter(List<StateStage> stages,
      int fromIndex) {
    for (int i = fromIndex + 1; i < stages.size(); i++) {
      if (stages.get(i).enabled()) {
        return Optional.of(new ReviewStageStatus(i, stages.get(i).label()));
      }
    }
    return Optional.empty();
  }

  @Nullable
  private String getDesignatedBodyCode(LtftForm form) {
    if (form.getContent() == null || form.getContent().programmeMembership() == null) {
      return null;
    }
    return form.getContent().programmeMembership().designatedBodyCode();
  }
}
