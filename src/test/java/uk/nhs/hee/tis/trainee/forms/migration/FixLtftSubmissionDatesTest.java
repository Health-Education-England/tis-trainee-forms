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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

class FixLtftSubmissionDatesTest {

  private FixLtftSubmissionDates migration;
  private MongoTemplate template;
  private LtftService ltftService;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    ltftService = mock(LtftService.class);
    when(ltftService.getLtftAssignmentUpdateTopic()).thenReturn("test-ltft-assignment-topic");
    migration = new FixLtftSubmissionDates(template, ltftService);
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
        .submitted(Instant.parse("2025-01-02T10:00:00Z"))
        .history(List.of(adminInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
  }

  @Test
  void shouldNotUpdateFormWhenSubmittedTimestampAlreadyCorrect() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    Instant traineeTimestamp = Instant.parse("2025-01-01T10:00:00Z");

    StatusInfo traineeInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(traineeTimestamp)
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .submitted(traineeTimestamp)
        .history(List.of(traineeInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    migration.migrate();

    verify(template, never()).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
    verify(ltftService, never()).publishUpdateNotification(any(), anyString(), anyString());
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
        .submitted(adminTimestamp)
        .history(List.of(traineeInfo, adminInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(template.updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class)))
        .thenReturn(updateResult);

    LtftForm updatedForm = new LtftForm();
    updatedForm.setStatus(Status.builder()
        .submitted(traineeTimestamp)
        .history(List.of(traineeInfo, adminInfo))
        .build());
    when(template.findOne(any(Query.class), eq(LtftForm.class)))
        .thenReturn(updatedForm);

    migration.migrate();

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(template).updateFirst(any(Query.class), updateCaptor.capture(), eq(LtftForm.class));

    Update update = updateCaptor.getValue();
    Document updateObject = update.getUpdateObject();
    Document setObject = updateObject.get("$set", Document.class);
    Instant updatedTimestamp = setObject.get("status.submitted", Instant.class);
    assertThat("Unexpected updated timestamp.", updatedTimestamp, is(traineeTimestamp));

    verify(ltftService).publishUpdateNotification(any(), eq(null), anyString());
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
            .submitted(adminTimestamp)
            .history(List.of(firstTraineeInfo, secondTraineeInfo, adminInfo))
            .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(template.updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class)))
        .thenReturn(updateResult);

    LtftForm updatedForm = new LtftForm();
    updatedForm.setStatus(Status.builder()
        .submitted(secondTraineeTimestamp)
        .history(List.of(firstTraineeInfo, secondTraineeInfo, adminInfo))
        .build());
    when(template.findOne(any(Query.class), eq(LtftForm.class)))
        .thenReturn(updatedForm);

    migration.migrate();

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(template).updateFirst(any(Query.class), updateCaptor.capture(), eq(LtftForm.class));

    Update update = updateCaptor.getValue();
    Document updateObject = update.getUpdateObject();
    Document setObject = updateObject.get("$set", Document.class);
    Instant updatedTimestamp = setObject.get("status.submitted", Instant.class);
    assertThat("Unexpected updated timestamp.", updatedTimestamp, is(secondTraineeTimestamp));

    verify(ltftService).publishUpdateNotification(any(), eq(null), anyString());
  }

  @Test
  void shouldUpdateFormWhenSubmittedTimestampDiffersFromTraineeTimestamp() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    Instant traineeTimestamp = Instant.parse("2025-01-01T10:00:00Z");
    Instant wrongTimestamp = Instant.parse("2025-01-02T10:00:00Z");

    StatusInfo traineeInfo = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(traineeTimestamp)
        .build();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .submitted(wrongTimestamp)
        .history(List.of(traineeInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(template.updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class)))
        .thenReturn(updateResult);

    LtftForm updatedForm = new LtftForm();
    updatedForm.setStatus(Status.builder()
        .submitted(traineeTimestamp)
        .history(List.of(traineeInfo))
        .build());
    when(template.findOne(any(Query.class), eq(LtftForm.class)))
        .thenReturn(updatedForm);

    migration.migrate();

    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(template).updateFirst(any(Query.class), updateCaptor.capture(), eq(LtftForm.class));

    Update update = updateCaptor.getValue();
    Document updateObject = update.getUpdateObject();
    Document setObject = updateObject.get("$set", Document.class);
    Instant updatedTimestamp = setObject.get("status.submitted", Instant.class);
    assertThat("Unexpected updated timestamp.", updatedTimestamp, is(traineeTimestamp));

    verify(ltftService).publishUpdateNotification(any(), eq(null), anyString());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }

  @Test
  void shouldHandleMultipleFormsCorrectly() {
    Person trainee = Person.builder()
        .role("TRAINEE")
        .build();
    Person admin = Person.builder()
        .role("ADMIN")
        .build();

    // Form 1: needs fixing
    Instant traineeTimestamp1 = Instant.parse("2025-01-01T10:00:00Z");
    Instant adminTimestamp1 = Instant.parse("2025-01-02T10:00:00Z");
    StatusInfo traineeInfo1 = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(traineeTimestamp1)
        .build();
    StatusInfo adminInfo1 = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(admin)
        .timestamp(adminTimestamp1)
        .build();
    LtftForm form1 = new LtftForm();
    form1.setId(UUID.randomUUID());
    form1.setStatus(Status.builder()
        .submitted(adminTimestamp1)
        .history(List.of(traineeInfo1, adminInfo1))
        .build());

    // Form 2: already correct
    Instant traineeTimestamp2 = Instant.parse("2025-01-03T10:00:00Z");
    StatusInfo traineeInfo2 = StatusInfo.builder()
        .state(SUBMITTED)
        .modifiedBy(trainee)
        .timestamp(traineeTimestamp2)
        .build();
    LtftForm form2 = new LtftForm();
    form2.setId(UUID.randomUUID());
    form2.setStatus(Status.builder()
        .submitted(traineeTimestamp2)
        .history(List.of(traineeInfo2))
        .build());

    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(List.of(form1, form2));

    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(template.updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class)))
        .thenReturn(updateResult);

    LtftForm updatedForm1 = new LtftForm();
    updatedForm1.setId(form1.getId());
    updatedForm1.setStatus(Status.builder()
        .submitted(traineeTimestamp1)
        .history(List.of(traineeInfo1, adminInfo1))
        .build());
    when(template.findOne(any(Query.class), eq(LtftForm.class)))
        .thenReturn(updatedForm1);

    migration.migrate();

    verify(template, times(1)).updateFirst(any(Query.class), any(Update.class),
        eq(LtftForm.class));
    verify(ltftService, times(1)).publishUpdateNotification(any(), eq(null), anyString());
  }

  @Test
  void shouldLogErrorWhenUpdatedFormCannotBeRetrieved() {
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
    form.setId(UUID.randomUUID());
    form.setStatus(Status.builder()
        .submitted(adminTimestamp)
        .history(List.of(traineeInfo, adminInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(template.updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class)))
        .thenReturn(updateResult);

    when(template.findOne(any(Query.class), eq(LtftForm.class)))
        .thenReturn(null);

    migration.migrate();

    verify(template).updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class));
    verify(ltftService, never()).publishUpdateNotification(any(), anyString(), anyString());
  }

  @Test
  void shouldUseCorrectSnsTopicForNotification() {
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
        .submitted(adminTimestamp)
        .history(List.of(traineeInfo, adminInfo))
        .build());
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(Collections.singletonList(form));

    UpdateResult updateResult = mock(UpdateResult.class);
    when(updateResult.getModifiedCount()).thenReturn(1L);
    when(template.updateFirst(any(Query.class), any(Update.class), eq(LtftForm.class)))
        .thenReturn(updateResult);

    LtftForm updatedForm = new LtftForm();
    updatedForm.setStatus(Status.builder()
        .submitted(traineeTimestamp)
        .history(List.of(traineeInfo, adminInfo))
        .build());
    when(template.findOne(any(Query.class), eq(LtftForm.class)))
        .thenReturn(updatedForm);

    migration.migrate();

    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    verify(ltftService).publishUpdateNotification(
        any(),
        eq(null),
        topicCaptor.capture()
    );

    assertThat("Unexpected SNS topic.", topicCaptor.getValue(),
        is("test-ltft-assignment-topic"));
  }
}
