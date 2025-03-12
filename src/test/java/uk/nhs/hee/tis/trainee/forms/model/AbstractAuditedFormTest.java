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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
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
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
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
    form.setLifecycleState(LifecycleState.APPROVED);

    assertEquals(LifecycleState.APPROVED, form.getLifecycleState(),
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
