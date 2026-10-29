/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.ReviewOutcome.Outcome;

class LtftFormTest {

  @Test
  void shouldGetFormType() {
    assertThat("Unexpected form type.", new LtftForm().getFormType(), is("ltft"));
  }

  @Test
  void shouldGetLifecycleStateIfNull() {
    assertThat("Unexpected lifecycle state.", new LtftForm().getLifecycleState(), is(nullValue()));
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldGetLifecycleState(LifecycleState s) {
    LtftForm form = new LtftForm();
    StatusInfo current = StatusInfo.builder()
        .state(s)
        .build();
    List<StatusInfo> history = List.of(StatusInfo.builder()
        .state(LifecycleState.SUBMITTED)
        .detail(Status.StatusDetail.builder().reason("test").build())
        .timestamp(Instant.now())
        .build()
    );
    form.setStatus(Status.builder()
        .current(current)
        .history(history)
        .build()
    );
    assertThat("Unexpected lifecycle state.", form.getLifecycleState(), is(s));
  }

  @Test
  void shouldResolveNullResponsibleRoleWhenStateNull() {
    LtftForm form = new LtftForm();
    StatusInfo statusInfo = StatusInfo.builder().build();

    assertThat("Unexpected responsible role.", form.resolveResponsibleRole(statusInfo),
        is(nullValue()));
  }

  @ParameterizedTest
  @CsvSource(nullValues = "null", textBlock = """
      APPROVED, null
      DELETED, null
      DRAFT, null
      REJECTED, null
      SUBMITTED, ADMIN
      UNDER_REVIEW, ADMIN
      UNSUBMITTED, TRAINEE
      WITHDRAWN, null
      """)
  void shouldResolveResponsibleRoleFromLifecycleState(LifecycleState state,
      ResponsibleRole expected) {
    LtftForm form = new LtftForm();
    StatusInfo statusInfo = StatusInfo.builder().state(state).build();

    assertThat("Unexpected responsible role.", form.resolveResponsibleRole(statusInfo),
        is(expected));
  }

  @Test
  void shouldResolveResponsibleRoleFromGivenStatusInfoNotCurrentStatus() {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.SUBMITTED);
    StatusInfo statusInfo = StatusInfo.builder().state(LifecycleState.UNSUBMITTED).build();

    assertThat("Unexpected responsible role.", form.resolveResponsibleRole(statusInfo),
        is(ResponsibleRole.TRAINEE));
  }

  @ParameterizedTest
  @CsvSource(nullValues = "null", textBlock = """
      APPROVED,     false, false, null
      APPROVED,     true,  false, null
      APPROVED,     true,  true,  null
      DRAFT,        true,  false, null
      REJECTED,     true,  false, null
      SUBMITTED,    false, false, ADMIN
      SUBMITTED,    true,  false, REVIEWER
      SUBMITTED,    true,  true,  ADMIN
      SUBMITTED,    false, true,  ADMIN
      UNDER_REVIEW, false, false, ADMIN
      UNDER_REVIEW, true,  false, REVIEWER
      UNDER_REVIEW, true,  true,  ADMIN
      UNDER_REVIEW, false, true,  ADMIN
      UNSUBMITTED,  true,  false, TRAINEE
      UNSUBMITTED,  true,  true,  TRAINEE
      WITHDRAWN,    true,  false, null
      """)
  void shouldResolveResponsibleRoleFromReviewerAndReviewOutcome(LifecycleState state,
      boolean hasReviewer, boolean hasOutcome, ResponsibleRole expected) {
    LtftForm form = new LtftForm();
    StatusInfo statusInfo = StatusInfo.builder()
        .state(state)
        .assignedReviewer(hasReviewer ? Person.builder().name("Tee-Pee Dee").build() : null)
        .reviewOutcome(hasOutcome ? createReviewOutcome() : null)
        .build();

    assertThat("Unexpected responsible role.", form.resolveResponsibleRole(statusInfo),
        is(expected));
  }

  @ParameterizedTest
  @CsvSource(nullValues = "null", textBlock = """
      DRAFT, null
      SUBMITTED, ADMIN
      UNDER_REVIEW, ADMIN
      UNSUBMITTED, TRAINEE
      APPROVED, null
      """)
  void shouldRecordResponsibleRoleWhenLifecycleStateSet(LifecycleState state,
      ResponsibleRole expected) {
    LtftForm form = new LtftForm();

    form.setLifecycleState(state);

    assertThat("Unexpected current responsible role.",
        form.getStatus().current().responsibleRole(), is(expected));
    assertThat("Unexpected history responsible role.",
        form.getStatus().history().get(0).responsibleRole(), is(expected));
  }

  @Test
  void shouldRecordResponsibleRoleHistoryWhenLifecycleStateChanges() {
    LtftForm form = new LtftForm();

    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setLifecycleState(LifecycleState.UNSUBMITTED);

    List<StatusInfo> history = form.getStatus().history();
    assertThat("Unexpected history size.", history.size(), is(2));
    assertThat("Unexpected first responsible role.", history.get(0).responsibleRole(),
        is(ResponsibleRole.ADMIN));
    assertThat("Unexpected second responsible role.", history.get(1).responsibleRole(),
        is(ResponsibleRole.TRAINEE));
  }

  @Test
  void shouldRecordResponsibleRoleWhenAdminAssigned() {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.SUBMITTED);

    form.setAssignedAdmin(Person.builder().name("Ad Min").build(), null);

    assertThat("Unexpected current responsible role.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.ADMIN));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNDER_REVIEW"})
  void shouldRecordReviewerResponsibleRoleWhenReviewerAssigned(LifecycleState state) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(state);

    form.setAssignedReviewer(Person.builder().name("Tee-Pee Dee").build(), null);

    List<StatusInfo> history = form.getStatus().history();
    assertThat("Unexpected current responsible role.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.REVIEWER));
    assertThat("Unexpected previous responsible role.", history.get(0).responsibleRole(),
        is(ResponsibleRole.ADMIN));
    assertThat("Unexpected latest responsible role.", history.get(1).responsibleRole(),
        is(ResponsibleRole.REVIEWER));
  }

  @Test
  void shouldRecordAdminResponsibleRoleWhenReviewOutcomeRecorded() {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.UNDER_REVIEW);
    form.setAssignedReviewer(Person.builder().name("Tee-Pee Dee").build(), null);

    form.setReviewOutcome(createReviewOutcome(), null);

    List<StatusInfo> history = form.getStatus().history();
    assertThat("Unexpected current responsible role.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.ADMIN));
    assertThat("Unexpected history responsible roles.",
        history.stream().map(StatusInfo::responsibleRole).toList(),
        is(List.of(ResponsibleRole.ADMIN, ResponsibleRole.REVIEWER, ResponsibleRole.ADMIN)));
  }

  @Test
  void shouldKeepAdminResponsibleRoleWhenAssigneesChangeAfterReviewOutcome() {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.UNDER_REVIEW);
    form.setAssignedReviewer(Person.builder().name("Tee-Pee Dee").build(), null);
    form.setReviewOutcome(createReviewOutcome(), null);

    form.setAssignedAdmin(Person.builder().name("Ad Min").build(), null);
    assertThat("Unexpected responsible role after admin assigned.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.ADMIN));

    form.setReviewStage(new ReviewStageStatus(1, "Stage 2"));
    assertThat("Unexpected responsible role after review stage set.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.ADMIN));
  }

  @Test
  void shouldRecordResponsibleRoleWhenReviewerRemainsAssignedThroughUnsubmitAndResubmit() {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setAssignedReviewer(Person.builder().name("Tee-Pee Dee").build(), null);

    form.setLifecycleState(LifecycleState.UNSUBMITTED);
    assertThat("Unexpected responsible role after unsubmit.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.TRAINEE));

    form.setLifecycleState(LifecycleState.SUBMITTED);
    assertThat("Unexpected responsible role after resubmit.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.REVIEWER));
  }

  @Test
  void shouldRecordResponsibleRoleWhenReviewStageSet() {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.UNDER_REVIEW);

    form.setReviewStage(new ReviewStageStatus(1, "Stage 2"));

    assertThat("Unexpected current responsible role.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.ADMIN));
  }

  /**
   * Create a review outcome.
   *
   * @return The created review outcome.
   */
  private static ReviewOutcome createReviewOutcome() {
    return ReviewOutcome.builder()
        .outcome(Outcome.RECOMMEND_REJECTION)
        .outcomeComments("Some free text value")
        .build();
  }
}
