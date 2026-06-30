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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
import uk.nhs.hee.tis.trainee.forms.dto.ReviewWorkflowDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.ReviewStageStatus;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.ProgrammeMembership;
import static uk.nhs.hee.tis.trainee.forms.service.ReviewStageService.TERMINAL_STAGE_LABEL;

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

  private LtftForm formWithNoProgrammeMembership() {
    LtftForm form = new LtftForm();
    form.setContent(LtftContent.builder().build()); // content present but no programmeMembership
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

    assertThat("Unexpected review stage when all stages disabled on re-submit.", result,
        nullValue());
  }

  @Test
  void shouldReturnNullWhenFormHasContentButNoProgrammeMembership() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    LtftForm form = formWithNoProgrammeMembership();
    ReviewStageStatus result = service.resolveReviewStageForTransition(form, SUBMITTED);

    assertThat("Unexpected review stage when no programme membership.", result, nullValue());
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
  void shouldReturnTerminalStageWhenAtFinalConfiguredStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 2, "Dean Approval");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(), "Expected terminal stage to be present at final stage.");
    assertThat("Unexpected terminal stage index.", result.get().index(), is(3));
    assertThat("Unexpected terminal stage label.", result.get().label(), is(TERMINAL_STAGE_LABEL));
  }

  @Test
  void shouldReturnTerminalStageWhenAtFinalStageOfSingleStageWorkflow() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Only Stage"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Only Stage");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(),
        "Expected terminal stage for single-stage workflow at stage 0.");
    assertThat("Unexpected terminal stage index.", result.get().index(), is(1));
    assertThat("Unexpected terminal stage label.", result.get().label(), is(TERMINAL_STAGE_LABEL));
  }

  @Test
  void shouldReturnEmptyWhenAlreadyAtTerminalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    // Terminal stage index = stages.size() = 3.
    LtftForm form = formAtReviewStage(DBC, 3, TERMINAL_STAGE_LABEL);
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(),
        "Expected empty optional when already at the terminal stage.");
  }

  @Test
  void shouldReturnEmptyWhenAllStagesDisabledForAdvance() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        disabledStage("Triage"), disabledStage("Manager Review"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(),
        "Expected empty optional when all stages are disabled.");
  }

  @Test
  void shouldAdvanceThroughAllStagesInOrderIncludingTerminal() {
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
    assertTrue(step3.isPresent(), "Expected step 3 (terminal) to be present.");
    assertThat("Unexpected step 3 index.", step3.get().index(), is(3));
    assertThat("Unexpected step 3 label.", step3.get().label(), is(TERMINAL_STAGE_LABEL));

    LtftForm formAt3 = formAtReviewStage(DBC, 3, TERMINAL_STAGE_LABEL);
    Optional<ReviewStageStatus> step4 = service.resolveAdvance(formAt3);
    assertTrue(step4.isEmpty(),
        "Expected step 4 to be empty (already at terminal stage, no further advance).");
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
  void shouldReturnTerminalStageWhenAllRemainingStagesAreDisabled() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("End"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(),
        "Expected terminal stage when no enabled stages follow the current stage.");
    assertThat("Unexpected terminal stage index.", result.get().index(), is(2));
    assertThat("Unexpected terminal stage label.", result.get().label(), is(TERMINAL_STAGE_LABEL));
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
  void shouldReturnTerminalStageWhenAtDisabledFinalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("End"))));

    LtftForm form = formAtReviewStage(DBC, 1, "End");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(),
        "Expected terminal stage at disabled final stage.");
    assertThat("Unexpected terminal stage index.", result.get().index(), is(2));
    assertThat("Unexpected terminal stage label.", result.get().label(), is(TERMINAL_STAGE_LABEL));
  }

  @Test
  void shouldReturnTerminalStageAfterSkippingMultipleConsecutiveDisabledFinalStages() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Stage A"), disabledStage("Stage B"), disabledStage("Stage C"), stage("Stage D"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Stage A");
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isPresent(), "Expected next stage after skipping multiple disabled stages.");
    assertThat("Unexpected next stage index.", result.get().index(), is(3));
    assertThat("Unexpected next stage label.", result.get().label(), is("Stage D"));
  }

  @Test
  void shouldReturnEmptyWhenNoCurrentReviewStageAndWorkflowConfigured() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"))));

    // Form has no status at all — getCurrentReviewStage returns null → cannot advance
    LtftForm form = formWithDbc(DBC);
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(),
        "Expected empty optional when there is no current review stage to advance from.");
  }

  @Test
  void shouldReturnEmptyWhenStatusPresentButCurrentIsNull() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"))));

    // Status is non-null but current() is null — hits the second condition in getCurrentReviewStage
    LtftForm form = formWithDbc(DBC);
    form.setStatus(Status.builder().build()); // current() is null
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(),
        "Expected empty optional when status is present but current status is null.");
  }

  @Test
  void shouldReturnEmptyWhenFormHasNoProgrammeMembershipForAdvance() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    LtftForm form = formWithNoProgrammeMembership();
    Optional<ReviewStageStatus> result = service.resolveAdvance(form);

    assertTrue(result.isEmpty(),
        "Expected empty optional when form has no programme membership.");
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
  void shouldDenyTerminalTransitionFromFinalConfiguredStageWhenWorkflowConfigured(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    // At the final configured stage (index 2), not yet at the terminal stage.
    LtftForm form = formAtReviewStage(DBC, 2, "Dean Approval");

    assertFalse(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition to be denied from final configured stage "
            + "(must advance to terminal stage first).");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldAllowTerminalTransitionFromTerminalStageWhenWorkflowConfigured(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    // At the implicit terminal stage (index = stages.size() = 3).
    LtftForm form = formAtReviewStage(DBC, 3, TERMINAL_STAGE_LABEL);

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition to be allowed from the terminal stage.");
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
  void shouldAllowTerminalTransitionWhenWorkflowExistsButNoCurrentReviewStage(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    // Form has content + DBC but no reviewStage in current status — pre-workflow form.
    LtftForm form = formWithDbc(DBC);
    form.setStatus(Status.builder()
        .current(StatusInfo.builder().state(SUBMITTED).build()) // reviewStage is null
        .build());

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition to be allowed for pre-workflow form with null reviewStage.");
  }

  @Test
  void shouldDenySingleStageWorkflowApprovalAtStageZero() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Only Stage"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Only Stage");

    assertFalse(service.canTransitionToLifecycleState(form, APPROVED),
        "Expected APPROVE to be denied from the only configured stage "
            + "(must advance to terminal stage first).");
  }

  @Test
  void shouldAllowSingleStageWorkflowApprovalAtTerminalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Only Stage"))));

    // At terminal stage (index = stages.size() = 1).
    LtftForm form = formAtReviewStage(DBC, 1, TERMINAL_STAGE_LABEL);

    assertTrue(service.canTransitionToLifecycleState(form, APPROVED),
        "Expected APPROVE to be allowed from the terminal stage of a single-stage workflow.");
  }

  @Test
  void shouldHandleDifferentDbcsIndependently() {
    String dbc2 = "1-1RUZUSF";
    Map<String, List<StateStage>> workflows = new HashMap<>();
    workflows.put(DBC, List.of(stage("Triage"), stage("Dean Approval")));
    workflows.put(dbc2, List.of(stage("Completeness checks")));
    workflowProperties.setReviewWorkflows(workflows);

    // DBC at terminal stage — allow approve
    LtftForm formDbc1 = formAtReviewStage(DBC, 2, TERMINAL_STAGE_LABEL);
    assertTrue(service.canTransitionToLifecycleState(formDbc1, APPROVED),
        "Expected APPROVE for DBC1 at terminal stage.");

    // DBC2 at terminal stage — allow approve
    LtftForm formDbc2 = formAtReviewStage(dbc2, 1, TERMINAL_STAGE_LABEL);
    assertTrue(service.canTransitionToLifecycleState(formDbc2, APPROVED),
        "Expected APPROVE for DBC2 at terminal stage.");

    // DBC at first stage — deny approve
    LtftForm formDbc1Stage0 = formAtReviewStage(DBC, 0, "Triage");
    assertFalse(service.canTransitionToLifecycleState(formDbc1Stage0, APPROVED),
        "Expected APPROVE to be denied for DBC1 at non-final stage.");

    // DBC at final configured stage — deny approve (must reach terminal first)
    LtftForm formDbc1FinalConfigured = formAtReviewStage(DBC, 1, "Dean Approval");
    assertFalse(service.canTransitionToLifecycleState(formDbc1FinalConfigured, APPROVED),
        "Expected APPROVE to be denied for DBC1 at final configured stage.");
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
  void shouldDenyTerminalTransitionFromEnabledStageWhenAllSubsequentStagesDisabled(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Manager Review"))));

    // At the last enabled stage, but not yet at the terminal stage.
    LtftForm form = formAtReviewStage(DBC, 0, "Triage");

    assertFalse(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition denied from enabled stage "
            + "(must advance to terminal stage first).");
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
  void shouldDenyTerminalTransitionFromDisabledStageWhenNoEnabledStagesFollowIt(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Manager Review"))));

    // At a disabled final stage, but not yet at the terminal stage.
    LtftForm form = formAtReviewStage(DBC, 1, "Manager Review");

    assertFalse(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition denied from disabled stage "
            + "(must advance to terminal stage first).");
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

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldAllowTerminalTransitionWhenFormHasNoProgrammeMembership(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    // No programme membership → dbc is null → no configured stages → allow
    LtftForm form = formWithNoProgrammeMembership();

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition allowed when form has no programme membership.");
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldAllowTerminalTransitionWhenStatusPresentButCurrentIsNullAndWorkflowConfigured(
      LifecycleState targetState) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    // Status is non-null but current() is null — getCurrentReviewStage returns null.
    // Treated as a pre-workflow form; transition is allowed.
    LtftForm form = formWithDbc(DBC);
    form.setStatus(Status.builder().build()); // current() is null

    assertTrue(service.canTransitionToLifecycleState(form, targetState),
        "Expected terminal transition allowed when status.current() is null — pre-workflow form.");
  }

  // -- getWorkflowDto --

  @Test
  void shouldReturnEmptyStagesWhenNoWorkflowConfigured() {
    LtftForm form = formWithDbc(DBC);

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(), empty());
    assertThat("Unexpected current stage.", dto.currentStage(), nullValue());
  }

  @Test
  void shouldReturnOnlyEnabledStageLabelsWhenFormNotSubmitted() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Middle"), stage("Dean Approval"))));

    LtftForm form = formWithDbc(DBC); // not SUBMITTED

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", "Dean Approval", TERMINAL_STAGE_LABEL));
    assertThat("Unexpected current stage.", dto.currentStage(), nullValue());
  }

  @Test
  void shouldReturnNullCurrentStageWhenFormNotSubmitted() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formWithDbc(DBC); // no status set

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", "Manager Review", "Dean Approval", TERMINAL_STAGE_LABEL));

    assertThat("Unexpected current stage when not submitted.", dto.currentStage(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = "SUBMITTED")
  void shouldReturnNullCurrentStageForNonSubmittedStates(LifecycleState state) {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    LtftForm form = formWithDbc(DBC);
    form.setStatus(Status.builder()
        .current(StatusInfo.builder().state(state)
            .reviewStage(new ReviewStageStatus(0, "Triage")).build())
        .build());

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", TERMINAL_STAGE_LABEL));

    assertThat("Unexpected current stage for state " + state + ".", dto.currentStage(),
        nullValue());
  }

  @Test
  void shouldReturnCurrentStagePositionInVisibleListWhenFormSubmitted() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 1, "Manager Review");

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", "Manager Review", "Dean Approval", TERMINAL_STAGE_LABEL));
    assertThat("Unexpected current stage.", dto.currentStage(), is(1));
  }

  @Test
  void shouldReturnCurrentStageRemappedPositionWhenDisabledStagesExistBefore() {
    // Disabled stage at index 1 shifts Dean Approval from abs-index 2 to visible-index 1.
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Middle"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 2, "Dean Approval");

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", "Dean Approval", TERMINAL_STAGE_LABEL));
    assertThat("Unexpected current stage.", dto.currentStage(), is(1));
  }

  @Test
  void shouldExcludeDisabledStageWhenFormIsNotCurrentlyInIt() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Middle"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 0, "Triage");

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", "Dean Approval", TERMINAL_STAGE_LABEL));
    assertThat("Unexpected current stage.", dto.currentStage(), is(0));
  }

  @Test
  void shouldIncludeCurrentDisabledStageInListAtCorrectPosition() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), disabledStage("Middle"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 1, "Middle");

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", "Middle", "Dean Approval", TERMINAL_STAGE_LABEL));
    assertThat("Unexpected current stage.", dto.currentStage(), is(1));
  }

  @Test
  void shouldReturnNullCurrentStageWhenSubmittedWithNoReviewStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    LtftForm form = formWithDbc(DBC);
    form.setStatus(Status.builder()
        .current(StatusInfo.builder().state(SUBMITTED).build()) // no reviewStage
        .build());

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", TERMINAL_STAGE_LABEL));
    assertThat("Unexpected current stage when no review stage set.", dto.currentStage(),
        nullValue());
  }

  @Test
  void shouldReturnEmptyStagesAndNullCurrentStageWhenAllStagesDisabled() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        disabledStage("Triage"), disabledStage("Middle"))));

    LtftForm form = formWithDbc(DBC);

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages when all disabled.", dto.stages(), empty());
    assertThat("Unexpected current stage when all disabled.", dto.currentStage(), nullValue());
  }

  @Test
  void shouldReturnEmptyStagesWhenFormHasContentButNoProgrammeMembership() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(stage("Triage"))));

    LtftForm form = formWithNoProgrammeMembership();

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages when no programme membership.", dto.stages(), empty());
    assertThat("Unexpected current stage when no programme membership.", dto.currentStage(),
        nullValue());
  }

  @Test
  void shouldReturnNullCurrentStageWhenStatusPresentButCurrentIsNull() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"))));

    // Status is non-null but current() is null — isSubmitted evaluates to false
    LtftForm form = formWithDbc(DBC);
    form.setStatus(Status.builder().build()); // current() is null

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", "Manager Review", TERMINAL_STAGE_LABEL));
    assertThat("Unexpected current stage when status.current() is null.", dto.currentStage(),
        nullValue());
  }

  @Test
  void shouldReturnTerminalStagePositionWhenFormIsAtTerminalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    // Form at terminal stage (index = stages.size() = 3).
    LtftForm form = formAtReviewStage(DBC, 3, TERMINAL_STAGE_LABEL);

    ReviewWorkflowDto dto = service.getWorkflowDto(form);

    assertThat("Unexpected stages.", dto.stages(),
        contains("Triage", "Manager Review", "Dean Approval", TERMINAL_STAGE_LABEL));
    assertThat("Unexpected current stage at terminal.", dto.currentStage(), is(3));
  }

  @Test
  void shouldAllowUnsubmitFromTerminalStage() {
    workflowProperties.setReviewWorkflows(Map.of(DBC, List.of(
        stage("Triage"), stage("Manager Review"), stage("Dean Approval"))));

    LtftForm form = formAtReviewStage(DBC, 3, TERMINAL_STAGE_LABEL);

    assertTrue(service.canTransitionToLifecycleState(form, UNSUBMITTED),
        "Expected UNSUBMIT to be allowed from terminal stage.");
  }
}
