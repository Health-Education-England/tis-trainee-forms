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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusDetail;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;

class AbstractAuditedFormTest {

  private AbstractAuditedForm<?> form;

  @BeforeEach
  void setUp() {
    form = new StubForm();
  }

  @Test
  void shouldConsiderFormWithoutCreatedAsNew() {
    assertThat("Unexpected isNew.", form.isNew(), is(true));
  }

  @Test
  void shouldNotConsiderFormWithCreatedAsNew() {
    form.setCreated(Instant.now());
    assertThat("Unexpected isNew.", form.isNew(), is(false));
  }

  @Test
  void shouldReturnNullLifecycleStateWhenNoStatus() {
    form.setStatus(null);

    LifecycleState state = form.getLifecycleState();

    assertThat("Unexpected lifecycle state.", state, nullValue());
  }

  @Test
  void shouldReturnNullLifecycleStateWhenNoCurrentStatus() {
    form.setStatus(Status.builder().build());

    LifecycleState state = form.getLifecycleState();

    assertThat("Unexpected lifecycle state.", state, nullValue());
  }

  @Test
  void shouldSetLifecycleState() {
    form.setRevision(1);
    form.setStatus(null);
    form.setLifecycleState(LifecycleState.SUBMITTED);

    assertEquals(LifecycleState.SUBMITTED, form.getLifecycleState(),
        "Unexpected lifecycle state.");
    Status.StatusInfo statusInfo = form.getStatus().current();
    assertEquals(LifecycleState.SUBMITTED, statusInfo.state(),
        "Unexpected lifecycle state in status.");
    assertThat("Unexpected modified by in status.", statusInfo.modifiedBy(), nullValue());
    assertThat("Unexpected detail in status.", statusInfo.detail(), nullValue());
    assertThat("Unexpected revision in status.", statusInfo.revision(), is(1));
    assertThat("Unexpected timestamp in status.",
        statusInfo.timestamp().truncatedTo(ChronoUnit.SECONDS),
        is(Instant.now().truncatedTo(ChronoUnit.SECONDS)));

    Status.StatusInfo statusInfoHistory = form.getStatus().history().get(0);
    assertEquals(statusInfoHistory, statusInfo, "Unexpected status info in history.");
  }

  @Test
  void shouldSetFullLifecycleState() {
    form.setRevision(1);
    form.setStatus(null);
    Status.StatusDetail detail = new Status.StatusDetail("reason", "message");
    Person modifiedBy = new Person("name", "email", "role");
    form.setLifecycleState(LifecycleState.SUBMITTED, detail, modifiedBy, 2);

    assertEquals(LifecycleState.SUBMITTED, form.getLifecycleState(),
        "Unexpected lifecycle state.");
    Status.StatusInfo statusInfo = form.getStatus().current();
    assertEquals(LifecycleState.SUBMITTED, statusInfo.state(),
        "Unexpected lifecycle state in status.");
    assertThat("Unexpected modified by in status.", statusInfo.modifiedBy(), is(modifiedBy));
    assertThat("Unexpected detail in status.", statusInfo.detail(), is(detail));
    assertThat("Unexpected revision in status.", statusInfo.revision(), is(2));
    assertThat("Unexpected timestamp in status.",
        statusInfo.timestamp().truncatedTo(ChronoUnit.SECONDS),
        is(Instant.now().truncatedTo(ChronoUnit.SECONDS)));

    Status.StatusInfo statusInfoHistory = form.getStatus().history().get(0);
    assertEquals(statusInfoHistory, statusInfo, "Unexpected status info in history.");
  }

  @Test
  void shouldSetLifecycleStateIfCurrentIsMissing() {
    form.setRevision(1);
    form.setStatus(Status.builder().build());
    form.setLifecycleState(LifecycleState.SUBMITTED);

    assertEquals(LifecycleState.SUBMITTED, form.getLifecycleState(),
        "Unexpected lifecycle state.");
    Status.StatusInfo statusInfo = form.getStatus().current();
    assertEquals(LifecycleState.SUBMITTED, statusInfo.state(),
        "Unexpected lifecycle state in status.");
    assertThat("Unexpected modified by in status.", statusInfo.modifiedBy(), nullValue());
    assertThat("Unexpected detail in status.", statusInfo.detail(), nullValue());
    assertThat("Unexpected revision in status.", statusInfo.revision(), is(1));
    assertThat("Unexpected timestamp in status.",
        statusInfo.timestamp().truncatedTo(ChronoUnit.SECONDS),
        is(Instant.now().truncatedTo(ChronoUnit.SECONDS)));

    Status.StatusInfo statusInfoHistory = form.getStatus().history().get(0);
    assertEquals(statusInfoHistory, statusInfo, "Unexpected status info in history.");
  }

  @Test
  void shouldOverwriteExistingLifecycleState() {
    form.setLifecycleState(LifecycleState.DRAFT);
    form.setLifecycleState(APPROVED);

    assertEquals(APPROVED, form.getLifecycleState(),
        "Unexpected lifecycle state.");
  }

  @Test
  void shouldHandleNullLifecycleState() {
    form.setLifecycleState(null);

    assertNull(form.getLifecycleState(), "Expected lifecycle state to be null.");
  }

  @Test
  void shouldAddLifecycleStateToHistory() {
    form.setLifecycleState(LifecycleState.DRAFT);
    form.setLifecycleState(LifecycleState.SUBMITTED);

    assertThat("Unexpected status history items.",
        form.getStatus().history().stream().map(StatusInfo::state).toList(),
        is(List.of(DRAFT, SUBMITTED)));
  }

  @Test
  void shouldRetainAssignedAdminWhenSettingLifecycleState() {
    StatusInfo assignedStatus = StatusInfo.builder()
        .state(SUBMITTED)
        .assignedAdmin(Person.builder()
            .name("Ad Min")
            .email("ad.min@example.com")
            .role("ADMIN")
            .build())
        .build();

    form.setStatus(Status.builder()
        .current(assignedStatus)
        .history(List.of(assignedStatus))
        .build());

    form.setLifecycleState(APPROVED);

    Person approvedAdmin = form.getStatus().current().assignedAdmin();
    assertThat("Unexpected assigned admin name.", approvedAdmin.name(), is("Ad Min"));
    assertThat("Unexpected assigned admin email.", approvedAdmin.email(), is("ad.min@example.com"));
    assertThat("Unexpected assigned admin role.", approvedAdmin.role(), is("ADMIN"));

    Person historyAdmin1 = form.getStatus().history().get(0).assignedAdmin();
    assertThat("Unexpected historical assigned admin.", historyAdmin1, is(approvedAdmin));

    Person historyAdmin2 = form.getStatus().history().get(1).assignedAdmin();
    assertThat("Unexpected historical assigned admin.", historyAdmin2, is(approvedAdmin));
  }

  @Test
  void shouldSetAssignedAdmin() {
    form.setRevision(1);
    form.setStatus(null);
    form.setAssignedAdmin(
        Person.builder().name("Ad Min").email("ad.min@example.com").role("ADMIN").build(),
        Person.builder().name("Mo Defy").email("mo.defy@example.com").role("ADMIN").build()
    );

    Person assignedAdmin = form.getStatus().current().assignedAdmin();
    assertThat("Unexpected assigned admin name.", assignedAdmin.name(), is("Ad Min"));
    assertThat("Unexpected assigned admin email.", assignedAdmin.email(), is("ad.min@example.com"));
    assertThat("Unexpected assigned admin role.", assignedAdmin.role(), is("ADMIN"));

    Person modifiedBy = form.getStatus().current().modifiedBy();
    assertThat("Unexpected modified by name.", modifiedBy.name(), is("Mo Defy"));
    assertThat("Unexpected modified by email.", modifiedBy.email(), is("mo.defy@example.com"));
    assertThat("Unexpected modified by role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfo> history = form.getStatus().history();
    assertThat("Unexpected history count.", history, hasSize(1));

    StatusInfo historicalStatus = history.get(0);
    assertThat("Unexpected assigned admin history.", historicalStatus.assignedAdmin(),
        is(assignedAdmin));
    assertThat("Unexpected modified by history.", historicalStatus.modifiedBy(), is(modifiedBy));
  }

  @Test
  void shouldSetAssignedAdminIfCurrentIsMissing() {
    form.setStatus(Status.builder().build());
    form.setAssignedAdmin(Person.builder()
            .name("Ad Min").email("ad.min@example.com").role("ADMIN").build(),
        Person.builder().name("Mo Defy").email("mo.defy@example.com").role("ADMIN").build()
    );

    Person assignedAdmin = form.getStatus().current().assignedAdmin();
    assertThat("Unexpected assigned admin name.", assignedAdmin.name(), is("Ad Min"));
    assertThat("Unexpected assigned admin email.", assignedAdmin.email(), is("ad.min@example.com"));
    assertThat("Unexpected assigned admin role.", assignedAdmin.role(), is("ADMIN"));

    Person modifiedBy = form.getStatus().current().modifiedBy();
    assertThat("Unexpected modified by name.", modifiedBy.name(), is("Mo Defy"));
    assertThat("Unexpected modified by email.", modifiedBy.email(), is("mo.defy@example.com"));
    assertThat("Unexpected modified by role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfo> history = form.getStatus().history();
    assertThat("Unexpected history count.", history, hasSize(1));

    StatusInfo historicalStatus = history.get(0);
    assertThat("Unexpected assigned admin history.", historicalStatus.assignedAdmin(),
        is(assignedAdmin));
    assertThat("Unexpected modified by history.", historicalStatus.modifiedBy(), is(modifiedBy));
  }

  @Test
  void shouldOverwriteExistingAssignedAdmin() {
    form.setAssignedAdmin(Person.builder().build(), null);
    form.setAssignedAdmin(Person.builder()
            .name("Ad Min").email("ad.min@example.com").role("ADMIN").build(),
        Person.builder().name("Mo Defy").email("mo.defy@example.com").role("ADMIN").build()
    );

    Person assignedAdmin = form.getStatus().current().assignedAdmin();
    assertThat("Unexpected assigned admin name.", assignedAdmin.name(), is("Ad Min"));
    assertThat("Unexpected assigned admin email.", assignedAdmin.email(), is("ad.min@example.com"));
    assertThat("Unexpected assigned admin role.", assignedAdmin.role(), is("ADMIN"));

    Person modifiedBy = form.getStatus().current().modifiedBy();
    assertThat("Unexpected modified by name.", modifiedBy.name(), is("Mo Defy"));
    assertThat("Unexpected modified by email.", modifiedBy.email(), is("mo.defy@example.com"));
    assertThat("Unexpected modified by role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfo> history = form.getStatus().history();
    assertThat("Unexpected history count.", history, hasSize(2));

    StatusInfo historicalStatus1 = history.get(0);
    assertThat("Unexpected assigned admin history.", historicalStatus1.assignedAdmin(), is(
        Person.builder().build()));
    assertThat("Unexpected modified by history.", historicalStatus1.modifiedBy(), nullValue());

    StatusInfo historicalStatus2 = history.get(1);
    assertThat("Unexpected assigned admin history.", historicalStatus2.assignedAdmin(),
        is(assignedAdmin));
    assertThat("Unexpected modified by history.", historicalStatus2.modifiedBy(), is(modifiedBy));
  }

  @Test
  void shouldHandleNullAssignedAdmin() {
    form.setAssignedAdmin(null, null);

    assertThat("Unexpected assigned admin.", form.getStatus().current().assignedAdmin(),
        nullValue());
  }

  @Test
  void shouldAddAssignedAdminToHistory() {
    form.setAssignedAdmin(Person.builder().name("First Admin").build(), null);
    form.setAssignedAdmin(Person.builder().name("Second Admin").build(), null);

    assertThat("Unexpected assigned admin history items.",
        form.getStatus().history().stream()
            .map(StatusInfo::assignedAdmin)
            .map(Person::name)
            .toList(),
        is(List.of("First Admin", "Second Admin")));
  }

  @Test
  void shouldRetainLifecycleStateWhenSettingAssignedAdmin() {
    StatusInfo submittedStatus = StatusInfo.builder()
        .state(SUBMITTED)
        .detail(StatusDetail.builder()
            .reason("test reason")
            .message("test message")
            .build())
        .revision(2)
        .build();

    form.setStatus(Status.builder()
        .current(submittedStatus)
        .history(List.of(submittedStatus))
        .build());

    form.setAssignedAdmin(
        Person.builder().name("Ad Min").email("ad.min@example.com").role("ADMIN").build(),
        Person.builder().name("Mo Defy").email("mo.defy@example.com").role("ADMIN").build()
    );

    assertThat("Unexpected lifecycle state.", form.getLifecycleState(), is(SUBMITTED));
    assertThat("Unexpected revision.", form.getStatus().current().revision(), is(2));
    assertThat("Unexpected status detail reason.", form.getStatus().current().detail().reason(),
        is("test reason"));
    assertThat("Unexpected status detail message.", form.getStatus().current().detail().message(),
        is("test message"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "2024-01-01T10:00:00Z")
  void shouldRetainPreviousSubmittedTimestampWhenSettingAssignedAdmin(String instantStr) {
    Instant submittedTime = instantStr == null ? null : Instant.parse(instantStr);
    StatusInfo submittedStatus = StatusInfo.builder()
        .state(SUBMITTED)
        .detail(StatusDetail.builder()
            .reason("test reason")
            .message("test message")
            .build())
        .revision(2)
        .timestamp(submittedTime)
        .build();

    form.setStatus(Status.builder()
        .current(submittedStatus)
        .history(List.of(submittedStatus))
        .submitted(submittedTime)
        .build());

    form.setAssignedAdmin(
        Person.builder().name("Ad Min").email("ad.min@example.com").role("ADMIN").build(),
        Person.builder().name("Mo Defy").email("mo.defy@example.com").role("ADMIN").build()
    );

    assertThat("Unexpected submission timestamp.", form.getStatus().submitted(),
        is(submittedTime));
    assertThat("Unexpected last updated timestamp.", form.getStatus().current().timestamp(),
        greaterThan(submittedTime != null ? submittedTime : Instant.EPOCH));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = "SUBMITTED")
  void shouldNotSetSubmittedWhenNotSubmitting(LifecycleState newState) {
    form.setLifecycleState(newState);

    Instant submitted = form.getStatus().submitted();
    assertThat("Unexpected submitted timestamp.", submitted, nullValue());

  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = "SUBMITTED")
  void shouldNotUpdateSubmittedWhenNotSubmitting(LifecycleState newState) {
    Instant yesterday = Instant.now().minus(Duration.ofDays(1));
    form.setStatus(Status.builder()
        .submitted(yesterday)
        .build());
    form.setLifecycleState(newState);

    Instant submitted = form.getStatus().submitted();
    assertThat("Unexpected submitted timestamp.", submitted, is(yesterday));
  }

  @Test
  void shouldSetSubmittedWhenSubmitting() {
    form.setLifecycleState(SUBMITTED);

    Instant submitted = form.getStatus().submitted();
    assertThat("Unexpected submitted timestamp.", submitted, notNullValue());
    assertThat("Unexpected submitted timestamp.", (double) submitted.getEpochSecond(),
        closeTo(Instant.now().getEpochSecond(), 1));

  }

  @Test
  void shouldUpdateSubmittedWhenReSubmitting() {
    form.setStatus(Status.builder()
        .submitted(Instant.now().minus(Duration.ofDays(1)))
        .build());
    form.setLifecycleState(SUBMITTED);

    Instant submitted = form.getStatus().submitted();
    assertThat("Unexpected submitted timestamp.", submitted, notNullValue());
    assertThat("Unexpected submitted timestamp.", (double) submitted.getEpochSecond(),
        closeTo(Instant.now().getEpochSecond(), 1));

  }

  /**
   * A stub for testing the behaviour of the AbstractForm event listener.
   */
  private static class StubForm extends AbstractAuditedForm {

    @Override
    public String getFormType() {
      return "test-auditedform";
    }
  }
}
