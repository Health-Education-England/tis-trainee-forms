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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import uk.nhs.hee.tis.trainee.forms.config.ReviewWorkflowProperties;
import uk.nhs.hee.tis.trainee.forms.config.StateStage;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.ReviewStageStatus;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.ProgrammeMembership;

class ReviewStageServiceTest {

  private static final String DBC = "1-1RSSQ6R";

  private ReviewStageService service;
  private ReviewWorkflowProperties workflowProperties;

  @BeforeEach
  void setUp() {
    workflowProperties = new ReviewWorkflowProperties();
    service = new ReviewStageService(workflowProperties);
  }

  // -- helpers --

  private static StateStage stage(String label) {
    return new StateStage(label, true);
  }

  private static StateStage disabledStage(String label) {
    return new StateStage(label, false);
  }

  private LtftForm formWithDbc(String dbc) {
    LtftForm form = new LtftForm();
    form.setContent(LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(dbc)
            .build())
        .build());
    return form;
  }

  private LtftForm formAtReviewStage(String dbc, int index, String label) {
    LtftForm form = formWithDbc(dbc);
    form.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(SUBMITTED)
            .reviewStage(new ReviewStageStatus(index, label))
            .build())
        .build());
    return form;
  }

  // -- resolveReviewStageForTransition --

  @Test
  void shouldReturnNullReviewStageWhenNoWorkflowConfiguredAndEnteringSubmitted() {
    LtftForm form = formWithDbc(DBC);

    ReviewStageStatus result = service.resolveReviewStageForTransition(form, SUBMITTED);

    assertThat("Unexpected review stage.", result, nullValue());
  }

  @Test
  void shouldReturnFirstStageWhenWorkflowConfiguredAndEnteringSubmitted() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formWithDbc(DBC);
    ReviewStageStatus result = service.resolveReviewStageForTransition(form, SUBMITTED);

    assertThat("Unexpected stage index.", result.index(), is(0));
    assertThat("Unexpected stage label.", result.label(), is("Triage"));
  }

  @Test
  void shouldAlwaysReturnFirstStageOnResubmitAfterUnsubmit() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"))));

    // Form was previously at stage 1 before being unsubmitted; re-submit goes back to 0.
    LtftForm form = formAtReviewStage(DBC, 1, "Manager Review");
    ReviewStageStatus result = service.resolveReviewStageForTransition(form, SUBMITTED);

    assertThat("Unexpected stage index on re-submit.", result.index(), is(0));
    assertThat("Unexpected stage label on re-submit.", result.label(), is("Triage"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = {"SUBMITTED"})
  void shouldReturnNullReviewStageWhenLeavingSubmitted(LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");
    ReviewStageStatus result = service.resolveReviewStageForTransition(form, targetState);

    assertThat("Unexpected review stage when leaving SUBMITTED.", result, nullValue());
  }

  @Test
  void shouldReturnNullWhenDbcHasNoContent() {
    LtftForm form = new LtftForm(); // no content set

    ReviewStageStatus result = service.resolveReviewStageForTransition(form, SUBMITTED);

    assertThat("Unexpected review stage for form with no content.", result, nullValue());
  }

  @Test
  void shouldSkipDisabledFirstStageAndReturnFirstEnabledStageOnSubmit() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        disabledStage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formWithDbc(DBC);
    ReviewStageStatus result = service.resolveReviewStageForTransition(form, SUBMITTED);

    assertThat("Unexpected stage index.", result.index(), is(1));
    assertThat("Unexpected stage label.", result.label(), is("Manager Review"));
  }

  @Test
  void shouldReturnNullWhenAllStagesDisabledOnSubmit() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        disabledStage("Triage"), disabledStage("Manager Review"))));

    LtftForm form = formWithDbc(DBC);
    ReviewStageStatus result = service.resolveReviewStageForTransition(form, SUBMITTED);

    assertThat("Unexpected review stage when all stages disabled.", result, nullValue());
  }

  @Test
  void shouldReturnNullWhenNoStageEnabledAndResubmitting() {
    // All disabled → null, even if re-submitting from a previous stage.
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        disabledStage("Triage"), disabledStage("Manager Review"))));

    LtftForm form = formAtReviewStage(DBC, 1, "Manager Review");
    ReviewStageStatus result = service.resolveReviewStageForTransition(form, SUBMITTED);

    assertThat("Unexpected review stage when all stages disabled on re-submit.", result, nullValue());
  }

  @Test
  void shouldReturnEmptyWhenNoWorkflowConfigured() {
    LtftForm form = formAtReviewStage(DBC, 0, "Triage");

    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(), "Expected empty optional when no workflow configured.");
  }

  @Test
  void shouldReturnNextStageWhenNotAtFinalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(), "Expected next stage to be present.");
    assertThat("Unexpected next stage index.", result.get().index(), is(1));
    assertThat("Unexpected next stage label.", result.get().label(), is("Manager Review"));
  }

  @Test
  void shouldReturnEmptyWhenAtFinalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 2, "Dean Approval");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(), "Expected empty optional at final stage.");
  }

  @Test
  void shouldReturnEmptyWhenAtFinalStageOfSingleStageWorkflow() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Only Stage"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Only Stage");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(), "Expected empty optional for single-stage workflow at stage 0.");
  }

  @Test
  void shouldAdvanceThroughAllStagesInOrder() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Stage A"), stage("Stage B"), stage("Stage C"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Stage A");

    Optional<ReviewStageStatus> step1 = service.resolveAdvance(form);
    assertTrue(step1.isPresent(), "Expected step 1 to be present.");
    assertThat("Unexpected step 1 index.", step1.get().index(), is(1));
    assertThat("Unexpected step 1 label.", step1.get().label(), is("Stage B"));

    LtftForm formAt1 = formAtReviewStage(DBC, 1, "Stage B");
    Optional<ReviewStageStatus> step2 = service.resolveAdvance(formAt1);
    assertTrue(step2.isPresent(), "Expected step 2 to be present.");
    assertThat("Unexpected step 2 index.", step2.get().index(), is(2));
    assertThat("Unexpected step 2 label.", step2.get().label(), is("Stage C"));

    LtftForm formAt2 = formAtReviewStage(DBC, 2, "Stage C");
    Optional<ReviewStageStatus> step3 = service.resolveAdvance(formAt2);
    assertTrue(step3.isEmpty(), "Expected step 3 to be empty (final stage, no further advance).");
  }

  @Test
  void shouldSkipDisabledStageWhenAdvancing() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Middle"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(), "Expected next stage to be present after skipping disabled.");
    assertThat("Unexpected next stage index.", result.get().index(), is(2));
    assertThat("Unexpected next stage label.", result.get().label(), is("Dean Approval"));
  }

  @Test
  void shouldReturnEmptyWhenAllRemainingStagesAreDisabled() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("End"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(),
        "Expected empty optional when no enabled stages follow the current stage.");
  }

  @Test
  void shouldAdvanceFromDisabledStageToNextEnabledStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Middle"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 1, "Middle");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(), "Expected next stage when advancing from a disabled stage.");
    assertThat("Unexpected next stage index.", result.get().index(), is(2));
    assertThat("Unexpected next stage label.", result.get().label(), is("Dean Approval"));
  }

  @Test
  void shouldReturnEmptyWhenAtDisabledFinalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("End"))));

    LtftForm form = formAtReviewStage(DBC, 1, "End");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(), "Expected empty optional at disabled final stage.");
  }

  @Test
  void shouldSkipMultipleConsecutiveDisabledStagesWhenAdvancing() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Stage A"), disabledStage("Stage B"), disabledStage("Stage C"), stage("Stage D"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Stage A");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(), "Expected next stage after skipping multiple disabled stages.");
    assertThat("Unexpected next stage index.", result.get().index(), is(3));
    assertThat("Unexpected next stage label.", result.get().label(), is("Stage D"));
  }

  @Test
  void shouldAllowUnsubmitFromAnyStageWhenNoWorkflowConfigured() {
    LtftForm form = formWithDbc(DBC);

    assertTrue(service.canTransitionToLifecycleState(form, UNSUBMITTED),
        "Expected UNSUBMIT to be allowed with no workflow.");
  }

  @Test
  void shouldAllowUnsubmitFromFirstStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");

    assertTrue(service.canTransitionToLifecycleState(form, UNSUBMITTED),
        "Expected UNSUBMIT to be allowed from first stage.");
  }

  @Test
  void shouldAllowUnsubmitFromMiddleStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 1, "Manager Review");

    assertTrue(service.canTransitionToLifecycleState(form, UNSUBMITTED),
        "Expected UNSUBMIT to be allowed from middle stage.");
  }

  @Test
  void shouldAllowUnsubmitFromFinalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 2, "Dean Approval");

    assertTrue(service.canTransitionToLifecycleState(form, UNSUBMITTED),
        "Expected UNSUBMIT to be allowed from final stage.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldDenyTerminalTransitionFromNonFinalStageWhenWorkflowConfigured(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");

    assertFalse(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition to be denied from non-final stage.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldAllowTerminalTransitionFromFinalStageWhenWorkflowConfigured(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 2, "Dean Approval");

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition to be allowed from final stage.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldAllowTerminalTransitionWhenNoWorkflowConfiguredForDbc(LifecycleState targetState) {
    // No workflow entry for DBC — existing behaviour applies.
    LtftForm form = formWithDbc(DBC);

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition to be allowed when no workflow is configured.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldDenyTerminalTransitionWhenWorkflowExistsButNoCurrentReviewStage(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    // Form has content + DBC but no reviewStage in current status.
    LtftForm form = formWithDbc(DBC);
    form.setStatus(Status.builder()
        .current(StatusInfo.builder().state(SUBMITTED).build()) // reviewStage is null
        .build());

    assertFalse(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition to be denied when reviewStage is null but workflow exists.");
  }

  @Test
  void shouldAllowSingleStageWorkflowToApproveAtStageZero() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Only Stage"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Only Stage");

    assertTrue(service.canTransitionToLifecycleState(form, APPROVED),
        "Expected APPROVE to be allowed from the only stage in a single-stage workflow.");
  }

  @Test
  void shouldHandleDifferentDbcsIndependently() {
    String dbc2 = "1-1RUZUSF";
    Map<String, List<StateStage>> workflows = new HashMap<>();
    workflows.put(DBC, List.of(stage("Triage"), stage("Dean Approval")));
    workflows.put(dbc2, List.of(stage("Completeness checks")));
    workflowProperties.setReviewWorkflows(workflows);

    // DBC at final stage — allow approve
    LtftForm formDbc1 = formAtReviewStage(DBC, 1, "Dean Approval");
    assertTrue(service.canTransitionToLifecycleState(formDbc1, APPROVED),
        "Expected APPROVE for DBC1 at final stage.");

    // DBC2 at final stage — allow approve
    LtftForm formDbc2 = formAtReviewStage(dbc2, 0, "Completeness checks");
    assertTrue(service.canTransitionToLifecycleState(formDbc2, APPROVED),
        "Expected APPROVE for DBC2 at final stage.");

    // DBC at first stage — deny approve
    LtftForm formDbc1Stage0 = formAtReviewStage(DBC, 0, "Triage");
    assertFalse(service.canTransitionToLifecycleState(formDbc1Stage0, APPROVED),
        "Expected APPROVE to be denied for DBC1 at non-final stage.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldAllowTerminalTransitionWhenAllStagesDisabled(LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        disabledStage("Triage"), disabledStage("Manager Review"))));

    LtftForm form = formWithDbc(DBC);

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition to be allowed when all stages disabled.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldAllowTerminalTransitionFromEnabledStageWhenAllSubsequentStagesDisabled(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Manager Review"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition allowed from enabled stage when all subsequent stages disabled.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldDenyTerminalTransitionFromEnabledStageWhenEnabledStagesFollowIt(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Middle"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");

    assertFalse(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition denied when an enabled stage follows the current stage.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldAllowTerminalTransitionFromDisabledStageWhenNoEnabledStagesFollowIt(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Manager Review"))));

    LtftForm form = formAtReviewStage(DBC, 1, "Manager Review");

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition allowed from disabled stage with no enabled stages after it.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldDenyTerminalTransitionFromDisabledStageWhenEnabledStagesFollowIt(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Middle"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 1, "Middle");

    assertFalse(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition denied from disabled stage when enabled stages follow it.");
  }

  @Test
  void shouldAllowUnsubmitFromDisabledStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 1, "Manager Review");

    assertTrue(service.canTransitionToLifecycleState(form, UNSUBMITTED),
        "Expected UNSUBMIT to be allowed from a disabled stage.");
  }
}

