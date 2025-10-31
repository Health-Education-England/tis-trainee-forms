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

package uk.nhs.hee.tis.trainee.forms.migration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;

class FixLtftSubmissionDatesTest {

  private FixLtftSubmissionDates migration;
  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    migration = new FixLtftSubmissionDates(template);
  }

  @Test
  void shouldQueryForFormsWithSubmittedStatus() {
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.emptyList());

    migration.migrate();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(template).find(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    Document submittedExists = queryObject.get("status.submitted", Document.class);
    assertThat("Unexpected submitted exists check.", submittedExists.getBoolean("$exists"),
        is(true));
  }

  @Test
  void shouldNotUpdateFormWhenStatusIsNull() {
    LtftForm form = new LtftForm();
    form.setStatus(null);
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldNotUpdateFormWhenHistoryIsNull() {
    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(null)
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldNotUpdateFormWhenModifiedByIsNull() {
    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(null)
        .timestamp(Instant.parse("2025-01-01T10:00:00Z"))
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(List.of(statusInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldNotUpdateFormWhenTimestampIsNull() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(null)
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(List.of(statusInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldNotUpdateFormWhenNoTraineeSubmission() {
    Person admin = Person.builder()
        .role("ADMIN")
        .build();
    StatusInfo adminInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(admin)
        .timestamp(Instant.parse("2025-01-02T10:00:00Z"))
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(List.of(adminInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldNotUpdateFormWhenNoAdminSubmission() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    StatusInfo traineeInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(Instant.parse("2025-01-01T10:00:00Z"))
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(List.of(traineeInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldNotUpdateFormWhenTraineeSubmittedAfterAdmin() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    Person admin = Person.builder()
        .role("ADMIN")
        .build();
    StatusInfo traineeInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(Instant.parse("2025-01-02T10:00:00Z"))
        .build();
    StatusInfo adminInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(admin)
        .timestamp(Instant.parse("2025-01-01T10:00:00Z"))
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(List.of(adminInfo, traineeInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldIgnoreNonSubmittedStates() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    Person admin = Person.builder()
        .role("ADMIN")
        .build();
    Instant traineeTimestamp = Instant.parse("2025-01-01T10:00:00Z");
    Instant adminTimestamp = Instant.parse("2025-01-02T10:00:00Z");

    //should never happen but just in case
    StatusInfo traineeInfo = StatusInfo.builder()
        .state(DRAFT)
        .modifiedBy(trainee)
        .timestamp(traineeTimestamp)
        .build();
    StatusInfo adminInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(admin)
        .timestamp(adminTimestamp)
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(List.of(traineeInfo, adminInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldUpdateFormWhenAdminSubmittedAfterTrainee() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    Person admin = Person.builder()
        .role("ADMIN")
        .build();
    Instant traineeTimestamp = Instant.parse("2025-01-01T10:00:00Z");
    Instant adminTimestamp = Instant.parse("2025-01-02T10:00:00Z");

    StatusInfo traineeInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(traineeTimestamp)
        .build();
    StatusInfo adminInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(admin)
        .timestamp(adminTimestamp)
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(List.of(traineeInfo, adminInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(template).updateFirst(any(Query.class), updateCaptor.capture(), eq(LtftForm.class));

    Update update = updateCaptor.getValue();
    Document updateObject = update.getUpdateObject();
    Document setObject = updateObject.get("$set", Document.class);
    Instant updatedTimestamp = setObject.get("status.submitted", Instant.class);
    assertThat("Unexpected updated timestamp.", updatedTimestamp, is(traineeTimestamp));
  }

  @Test
  void shouldUseLatestTraineeSubmissionTimestamp() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    Person admin = Person.builder()
        .role("ADMIN")
        .build();
    Instant firstTraineeTimestamp = Instant.parse("2025-01-01T10:00:00Z");
    Instant secondTraineeTimestamp = Instant.parse("2025-01-03T10:00:00Z");
    Instant adminTimestamp = Instant.parse("2025-01-04T10:00:00Z");

    StatusInfo firstTraineeInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(firstTraineeTimestamp)
        .build();
    StatusInfo secondTraineeInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(secondTraineeTimestamp)
        .build();
    StatusInfo adminInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(admin)
        .timestamp(adminTimestamp)
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(
        Status.builder()
            .history(List.of(firstTraineeInfo, secondTraineeInfo, adminInfo))
            .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(template).updateFirst(any(Query.class), updateCaptor.capture(), eq(LtftForm.class));

    Update update = updateCaptor.getValue();
    Document updateObject = update.getUpdateObject();
    Document setObject = updateObject.get("$set", Document.class);
    Instant updatedTimestamp = setObject.get("status.submitted", Instant.class);
    assertThat("Unexpected updated timestamp.", updatedTimestamp, is(secondTraineeTimestamp));
  }

  @Test
  void shouldConsiderLatestAdminSubmissionWhenMultipleExist() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    Person admin = Person.builder()
        .role("ADMIN")
        .build();
    Instant traineeTimestamp = Instant.parse("2025-01-02T10:00:00Z");
    Instant firstAdminTimestamp = Instant.parse("2025-01-01T10:00:00Z");
    Instant secondAdminTimestamp = Instant.parse("2025-01-03T10:00:00Z");

    StatusInfo traineeInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(traineeTimestamp)
        .build();
    StatusInfo firstAdminInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(admin)
        .timestamp(firstAdminTimestamp)
        .build();
    StatusInfo secondAdminInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(admin)
        .timestamp(secondAdminTimestamp)
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .history(List.of(traineeInfo, firstAdminInfo, secondAdminInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(template).updateFirst(any(Query.class), updateCaptor.capture(), eq(LtftForm.class));

    Update update = updateCaptor.getValue();
    Document updateObject = update.getUpdateObject();
    Document setObject = updateObject.get("$set", Document.class);
    Instant updatedTimestamp = setObject.get("status.submitted", Instant.class);
    assertThat("Unexpected updated timestamp.", updatedTimestamp, is(traineeTimestamp));
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
