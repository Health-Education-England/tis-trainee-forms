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
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNDER_REVIEW"})
  void shouldNotResolveReviewerResponsibleRoleFromAssignedReviewer(LifecycleState state) {
    LtftForm form = new LtftForm();
    StatusInfo statusInfo = StatusInfo.builder()
        .state(state)
        .assignedReviewer(Person.builder().name("Re Viewer").build())
        .build();

    // The assigned reviewer is distinct from the pre-approver, so does not affect the role.
    assertThat("Unexpected responsible role.", form.resolveResponsibleRole(statusInfo),
        is(ResponsibleRole.ADMIN));
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

  @Test
  void shouldRecordResponsibleRoleWhenReviewerAssigned() {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.SUBMITTED);

    form.setAssignedReviewer(Person.builder().name("Re Viewer").build(), null);

    // The assigned reviewer is distinct from the pre-approver, so does not affect the role.
    assertThat("Unexpected current responsible role.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.ADMIN));
  }

  @Test
  void shouldRecordResponsibleRoleWhenReviewStageSet() {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.UNDER_REVIEW);

    form.setReviewStage(new ReviewStageStatus(1, "Stage 2"));

    assertThat("Unexpected current responsible role.",
        form.getStatus().current().responsibleRole(), is(ResponsibleRole.ADMIN));
  }

  // TODO: pre-approver - add resolveResponsibleRole cases for SUBMITTED/UNDER_REVIEW with a
  //  pre-approver and null reviewOutcome (REVIEWER), with a pre-approver and a reviewOutcome
  //  (ADMIN), and UNSUBMITTED with a pre-approver (TRAINEE).
}
