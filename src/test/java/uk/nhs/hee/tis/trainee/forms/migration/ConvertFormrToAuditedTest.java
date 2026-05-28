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
 *
 */

package uk.nhs.hee.tis.trainee.forms.migration;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.mongodb.client.result.DeleteResult;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.mongodb.core.MongoTemplate;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartaSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;

class ConvertFormrToAuditedTest {

  private static final String S3_BUCKET = UUID.randomUUID().toString();

  private static final String DELETED_VERSION = "version-deleted-1";
  private static final String SUBMITTED_1_VERSION = "version-submitted-1";
  private static final String SUBMITTED_2_VERSION = "version-submitted-2";
  private static final String UNSUBMITTED_VERSION = "version-unsubmitted-1";

  private static final Instant DELETED_MODIFIED = Instant.now().minus(5, DAYS);
  private static final Instant SUBMITTED_1_MODIFIED = Instant.now().minus(20, DAYS);
  private static final Instant SUBMITTED_2_MODIFIED = Instant.now().minus(10, DAYS);
  private static final Instant UNSUBMITTED_MODIFIED = Instant.now().minus(15, DAYS);

  private static final UUID FORM_ID = UUID.randomUUID();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final Instant SUBMISSION_DATE = Instant.now()
      .minus(20, DAYS)
      .truncatedTo(MILLIS);
  private static final Instant LAST_MODIFIED = Instant.now()
      .minus(10, DAYS)
      .truncatedTo(MILLIS);

  private static final String PART_A_COLLECTION = "form-r-part-a";
  private static final String PART_B_COLLECTION = "form-r-part-b";

  private static final String PART_A_HISTORY_COLLECTION = "form-r-part-a-history";
  private static final String PART_B_HISTORY_COLLECTION = "form-r-part-b-history";

  private static final String METADATA_STATE = "lifecyclestate";

  private ConvertFormrToAudited migration;

  private MongoTemplate mongoTemplate;
  private S3Client s3Client;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    s3Client = mock(S3Client.class);
    migration = new ConvertFormrToAudited(mongoTemplate, s3Client, S3_BUCKET);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "DELETED"})
  void shouldSkipFormWhenNotDraftAndNoHistory(LifecycleState state) {
    String collectionName = "form-r-part-x";
    when(mongoTemplate.getCollectionName(any())).thenReturn(collectionName);

    Document before1 = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", state.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    Document before2 = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "DRAFT",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before1, before2));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenThrow(NoSuchKeyException.class);

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(2)).save(documentCaptor.capture(), eq(collectionName));

    List<Document> migrated = documentCaptor.getAllValues();

    Document migrated1 = migrated.get(0);
    assertThat("Unexpected form ID.", migrated1.get("_id"), is(FORM_ID));
    assertThat("Unexpected form class.", migrated1.get("_class"), is(FormRPartA.class.getName()));

    Document migrated2 = migrated.get(1);
    assertThat("Unexpected form ID.", migrated2.get("_id"), is(FORM_ID));
    assertThat("Unexpected form class.", migrated2.get("_class"), is(FormRPartB.class.getName()));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "DELETED"})
  void shouldCleanUpHistoryWhenNotDraftAndNoHistory(LifecycleState state) {
    String collectionName = "form-r-part-x";
    when(mongoTemplate.getCollectionName(any())).thenReturn(collectionName);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    Document before = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", state.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenThrow(NoSuchKeyException.class);

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    verify(mongoTemplate, never()).save(any(), eq(collectionName));
    verify(mongoTemplate).remove(any(), eq(PART_A_HISTORY_COLLECTION));
    verify(mongoTemplate).remove(any(), eq(PART_B_HISTORY_COLLECTION));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"UNSUBMITTED", "DELETED"})
  void shouldSkipFormWhenFirstVersionNotSubmitted(LifecycleState state) {
    String collectionName = "form-r-part-x";
    when(mongoTemplate.getCollectionName(any())).thenReturn(collectionName);

    Document before1 = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", state.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    Document before2 = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "DRAFT",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before1, before2));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(UUID.randomUUID().toString()).build())
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, DRAFT.toString()))
            .build());

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(2)).save(documentCaptor.capture(), eq(collectionName));

    List<Document> migrated = documentCaptor.getAllValues();

    Document migrated1 = migrated.get(0);
    assertThat("Unexpected form ID.", migrated1.get("_id"), is(FORM_ID));
    assertThat("Unexpected form class.", migrated1.get("_class"), is(FormRPartA.class.getName()));

    Document migrated2 = migrated.get(1);
    assertThat("Unexpected form ID.", migrated2.get("_id"), is(FORM_ID));
    assertThat("Unexpected form class.", migrated2.get("_class"), is(FormRPartB.class.getName()));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"UNSUBMITTED", "DELETED"})
  void shouldCleanUpHistoryWhenFirstVersionNotSubmitted(LifecycleState state) {
    String collectionName = "form-r-part-x";
    when(mongoTemplate.getCollectionName(any())).thenReturn(collectionName);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    Document before = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", state.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(UUID.randomUUID().toString()).build())
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, DRAFT.toString()))
            .build());

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    verify(mongoTemplate, never()).save(any(), eq(collectionName));
    verify(mongoTemplate).remove(any(), eq(PART_A_HISTORY_COLLECTION));
    verify(mongoTemplate).remove(any(), eq(PART_B_HISTORY_COLLECTION));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSkipFormWhenS3ContentNotValid(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    Document before1 = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    Document before2 = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "DRAFT",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before1, before2));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(UUID.randomUUID().toString()).build())
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build());

    String s3Content1 = "not a valid form";
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content1.getBytes())));

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected form ID.", migrated.get("_id"), is(FORM_ID));
    assertThat("Unexpected form class.", migrated.get("_class"), is(formClass.getName()));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldCleanUpHistoryWhenS3ContentNotValid(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    Document before = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(UUID.randomUUID().toString()).build())
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build());

    String s3Content1 = "not a valid form";
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content1.getBytes())));

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    verify(mongoTemplate, never()).save(any(), eq(collectionName));
    verify(mongoTemplate).remove(any(),
        eq(formClass == FormRPartA.class ? PART_A_HISTORY_COLLECTION : PART_B_HISTORY_COLLECTION));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldStopMigrationWithCleanHistoryWhenUnhandledExceptionOccurs(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    Document before1 = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    Document before2 = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "DRAFT",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before1, before2));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenThrow(NoSuchBucketException.class);

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    assertThrows(RuntimeException.class, () -> migration.migrateCollections());

    verify(mongoTemplate, never()).save(any(), eq(collectionName));
    verify(mongoTemplate).remove(any(),
        eq(formClass == FormRPartA.class ? PART_A_HISTORY_COLLECTION : PART_B_HISTORY_COLLECTION));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateToAuditedStructure(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "DRAFT",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected root keys.", migrated.keySet(),
        containsInAnyOrder("_id", "traineeTisId", "revision", "content", "status", "created",
            "lastModified", "_class"));

    Map<String, Object> migratedStatus = getEmbeddedMap(migrated, List.of("status"));
    assertThat("Unexpected status keys.", migratedStatus.keySet(),
        containsInAnyOrder("current", "submitted", "history"));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSetFormMetadata(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "DRAFT",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected ID.", migrated.get("_id", UUID.class), is(FORM_ID));
    assertThat("Unexpected trainee ID.", migrated.getString("traineeTisId"), is(TRAINEE_ID));

    assertThat("Unexpected created timestamp.", migrated.get("created", Instant.class),
        is(LAST_MODIFIED));
    assertThat("Unexpected modified timestamp.", migrated.get("lastModified", Instant.class),
        is(LAST_MODIFIED));
    assertThat("Unexpected document class.", migrated.getString("_class"), is(formClass.getName()));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldNotSetFormRefWhenDraft(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", DRAFT.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected form reference.", migrated.get("formRef"), nullValue());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "DELETED"})
  void shouldSetFormRefWhenNotDraft(LifecycleState state) {
    when(mongoTemplate.getCollectionName(FormRPartA.class)).thenReturn(PART_A_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", state.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_A_COLLECTION)))
        .thenReturn(List.of(before));

    when(mongoTemplate.count(any(), eq(PART_A_COLLECTION)))
        .thenReturn(2L);

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(UUID.randomUUID().toString()).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build())
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, state.toString()))
            .build());

    String s3ContentTemplate = """
            {
              "_id": "%s",
              "traineeTisId": "%s",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "lifecycleState": "%s",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3ContentTemplate.formatted(FORM_ID, TRAINEE_ID, SUBMITTED,
                LocalDateTime.ofInstant(SUBMISSION_DATE, UTC),
                LocalDateTime.ofInstant(LAST_MODIFIED, UTC)).getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3ContentTemplate.formatted(FORM_ID, TRAINEE_ID, state,
                LocalDateTime.ofInstant(SUBMISSION_DATE, UTC),
                LocalDateTime.ofInstant(LAST_MODIFIED, UTC)).getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_A_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected form reference.", migrated.get("formRef"),
        is("formr_parta_" + TRAINEE_ID + "_003"));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSetRevisionWhenNotRevised(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", DRAFT.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected revision.", migrated.get("revision"), is(0));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSetRevisionWhenRevised(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", DELETED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(SUBMITTED_2_VERSION).build(),
                ObjectVersion.builder().versionId(UNSUBMITTED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenAnswer(inv -> {
          Consumer<HeadObjectRequest.Builder> builder = inv.getArgument(0);
          HeadObjectRequest request = HeadObjectRequest.builder().applyMutation(builder).build();

          LifecycleState lifecycleState = switch (request.versionId()) {
            case SUBMITTED_1_VERSION, SUBMITTED_2_VERSION -> SUBMITTED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          Instant lastModified = switch (request.versionId()) {
            case SUBMITTED_1_VERSION -> SUBMITTED_1_MODIFIED;
            case SUBMITTED_2_VERSION -> SUBMITTED_2_MODIFIED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED_MODIFIED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          return HeadObjectResponse.builder()
              .lastModified(lastModified)
              .metadata(Map.of(METADATA_STATE, lifecycleState.toString()))
              .build();
        });

    String s3Content1 = """
            {
              "_id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "2024-03-06T16:43:10.413",
              "lastModifiedDate": "2025-04-07T16:43:10.413"
            }
        """.formatted(FORM_ID);
    String s3Content2 = """
            {
              "_id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "2026-05-08T16:43:10.413",
              "lastModifiedDate": "2027-06-09T16:43:10.413"
            }
        """.formatted(FORM_ID);
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content1.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content2.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected revision.", migrated.get("revision"), is(1));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSetContent(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "DRAFT",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> migratedContent = getEmbeddedMap(migrated, List.of("content"));
    assertThat("Unexpected content keys.", migratedContent.keySet(),
        containsInAnyOrder("forename", "surname", "email", "contentKey"));
    assertThat("Unexpected forename.", migratedContent.get("forename"), is("Anthony"));
    assertThat("Unexpected surname.", migratedContent.get("surname"), is("Gilliam"));
    assertThat("Unexpected email.", migratedContent.get("email"),
        is("anthony.gilliam@example.com"));
    assertThat("Unexpected content.", migratedContent.get("contentKey"), is("contentValue"));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldNotSetSubmittedWhenNotPreviouslySubmitted(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "DRAFT",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> migratedStatus = getEmbeddedMap(migrated, List.of("status"));
    assertThat("Unexpected status keys.", migratedStatus.keySet(),
        containsInAnyOrder("current", "history"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.INCLUDE, names = {"SUBMITTED",
      "UNSUBMITTED", "DELETED"})
  void shouldSetSubmittedWhenPreviouslySubmitted(LifecycleState state) {
    when(mongoTemplate.getCollectionName(FormRPartA.class)).thenReturn(PART_A_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", state.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_A_COLLECTION)))
        .thenReturn(List.of(before));

    when(mongoTemplate.count(any(), eq(PART_A_COLLECTION)))
        .thenReturn(2L);

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(UUID.randomUUID().toString()).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build())
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, state.toString()))
            .build());

    String s3ContentTemplate = """
            {
              "_id": "%s",
              "traineeTisId": "%s",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "lifecycleState": "%s",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """;
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3ContentTemplate.formatted(FORM_ID, TRAINEE_ID, SUBMITTED,
                LocalDateTime.ofInstant(SUBMISSION_DATE, UTC),
                LocalDateTime.ofInstant(LAST_MODIFIED, UTC)).getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3ContentTemplate.formatted(FORM_ID, TRAINEE_ID, state,
                LocalDateTime.ofInstant(SUBMISSION_DATE, UTC),
                LocalDateTime.ofInstant(LAST_MODIFIED, UTC)).getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_A_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> migratedStatus = getEmbeddedMap(migrated, List.of("status"));
    assertThat("Unexpected status keys.", migratedStatus.keySet(),
        containsInAnyOrder("current", "submitted", "history"));

    var submitted = migratedStatus.get("submitted");
    assertThat("Unexpected submitted date.", submitted, is(SUBMISSION_DATE));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSetCurrentStatus(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "DRAFT",
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> migratedCurrent = getEmbeddedMap(migrated, List.of("status", "current"));
    assertThat("Unexpected current status keys.", migratedCurrent.keySet(),
        containsInAnyOrder("state", "modifiedBy", "timestamp", "revision"));
    assertThat("Unexpected lifecycle state.", migratedCurrent.get("state"), is(DRAFT));
    assertThat("Unexpected current status timestamp.", migratedCurrent.get("timestamp"),
        is(LAST_MODIFIED));
    assertThat("Unexpected current status revision.", migratedCurrent.get("revision"), is(0));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.INCLUDE, names = {"DRAFT", "SUBMITTED"})
  void shouldSetCurrentModifiedByToTraineeWhenTraineeTransition(LifecycleState state) {
    when(mongoTemplate.getCollectionName(FormRPartA.class)).thenReturn(PART_A_COLLECTION);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", state.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_A_COLLECTION)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(UUID.randomUUID().toString()).build())
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, state.toString()))
            .build());

    String s3Content = """
            {
              "_id": "%s",
              "traineeTisId": "%s",
              "lifecycleState": "%s",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, TRAINEE_ID, state, LocalDateTime.ofInstant(SUBMISSION_DATE, UTC),
        LocalDateTime.ofInstant(LAST_MODIFIED, UTC));
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_A_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> modifiedBy = getEmbeddedMap(migrated,
        List.of("status", "current", "modifiedBy"));
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Anthony Gilliam"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"),
        is("anthony.gilliam@example.com"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("TRAINEE"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.INCLUDE, names = {"UNSUBMITTED", "DELETED"})
  void shouldSetCurrentModifiedByToAdminWhenAdminTransition(LifecycleState state) {
    when(mongoTemplate.getCollectionName(FormRPartA.class)).thenReturn(PART_A_COLLECTION);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", state.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_A_COLLECTION)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(UUID.randomUUID().toString()).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build())
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, state.toString()))
            .build());

    String s3Content = """
            {
              "_id": "%s",
              "traineeTisId": "%s",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "lifecycleState": "%s",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, TRAINEE_ID, state, LocalDateTime.ofInstant(SUBMISSION_DATE, UTC),
        LocalDateTime.ofInstant(LAST_MODIFIED, UTC));
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_A_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> modifiedBy = getEmbeddedMap(migrated,
        List.of("status", "current", "modifiedBy"));
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Unknown Admin"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"), is("no-reply@tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("ADMIN"));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldRebuildStatusHistoryFromS3Versions(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", DELETED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(DELETED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_2_VERSION).build(),
                ObjectVersion.builder().versionId(UNSUBMITTED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenAnswer(inv -> {
          Consumer<HeadObjectRequest.Builder> builder = inv.getArgument(0);
          HeadObjectRequest request = HeadObjectRequest.builder().applyMutation(builder).build();

          LifecycleState lifecycleState = switch (request.versionId()) {
            case DELETED_VERSION -> DELETED;
            case SUBMITTED_1_VERSION, SUBMITTED_2_VERSION -> SUBMITTED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          Instant lastModified = switch (request.versionId()) {
            case DELETED_VERSION -> DELETED_MODIFIED;
            case SUBMITTED_1_VERSION -> SUBMITTED_1_MODIFIED;
            case SUBMITTED_2_VERSION -> SUBMITTED_2_MODIFIED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED_MODIFIED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          return HeadObjectResponse.builder()
              .lastModified(lastModified)
              .metadata(Map.of(METADATA_STATE, lifecycleState.toString()))
              .build();
        });

    String s3Content1 = """
            {
              "_id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "2025-04-07T16:43:10.413"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE, UTC));
    String s3Content2 = """
            {
              "_id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "2026-05-08T16:43:10.413",
              "lastModifiedDate": "2027-06-09T16:43:10.413"
            }
        """.formatted(FORM_ID);
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content1.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content2.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> status = getEmbeddedMap(migrated, List.of("status"));

    Map<String, Object> current = (Map<String, Object>) status.get("current");
    assertThat("Unexpected current state.", current.get("state"), is(DELETED));

    List<Map<String, Object>> history = (List<Map<String, Object>>) status.get("history");
    assertThat("Unexpected history size.", history, hasSize(5));

    Map<String, Object> historyItem = history.get(0);
    assertThat("Unexpected history state.", historyItem.get("state"), is(DRAFT));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMISSION_DATE));

    historyItem = history.get(1);
    assertThat("Unexpected history state.", historyItem.get("state"), is(SUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMITTED_1_MODIFIED));

    historyItem = history.get(2);
    assertThat("Unexpected history state.", historyItem.get("state"), is(UNSUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(UNSUBMITTED_MODIFIED));

    historyItem = history.get(3);
    assertThat("Unexpected history state.", historyItem.get("state"), is(SUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMITTED_2_MODIFIED));

    historyItem = history.get(4);
    assertThat("Unexpected history state.", historyItem.get("state"), is(DELETED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(DELETED_MODIFIED));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSnapshotSubmissionsFromS3Versions(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", DELETED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(DELETED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_2_VERSION).build(),
                ObjectVersion.builder().versionId(UNSUBMITTED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenAnswer(inv -> {
          Consumer<HeadObjectRequest.Builder> builder = inv.getArgument(0);
          HeadObjectRequest request = HeadObjectRequest.builder().applyMutation(builder).build();

          LifecycleState lifecycleState = switch (request.versionId()) {
            case DELETED_VERSION -> DELETED;
            case SUBMITTED_1_VERSION, SUBMITTED_2_VERSION -> SUBMITTED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          Instant lastModified = switch (request.versionId()) {
            case DELETED_VERSION -> DELETED_MODIFIED;
            case SUBMITTED_1_VERSION -> SUBMITTED_1_MODIFIED;
            case SUBMITTED_2_VERSION -> SUBMITTED_2_MODIFIED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED_MODIFIED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          return HeadObjectResponse.builder()
              .lastModified(lastModified)
              .metadata(Map.of(METADATA_STATE, lifecycleState.toString()))
              .build();
        });

    String s3Content1 = """
            {
              "_id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "2024-03-06T16:43:10.413",
              "lastModifiedDate": "2025-04-07T16:43:10.413"
            }
        """.formatted(FORM_ID);
    String s3Content2 = """
            {
              "_id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "2026-05-08T16:43:10.413",
              "lastModifiedDate": "2027-06-09T16:43:10.413"
            }
        """.formatted(FORM_ID);
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content1.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content2.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(2)).save(documentCaptor.capture(),
        eq(formClass == FormRPartA.class ? PART_A_HISTORY_COLLECTION : PART_B_HISTORY_COLLECTION));

    List<Document> migrated = documentCaptor.getAllValues();
    assertThat("Unexpected snapshot count.", migrated, hasSize(2));

    String formRefPrefix = formClass == FormRPartA.class ? "formr_parta_" : "formr_partb_";
    String expectedFormRef = formRefPrefix + TRAINEE_ID + "_001";

    Document migrated1 = migrated.get(0);
    assertThat("Unexpected ID.", migrated1.get("_id"), both(notNullValue()).and(not(FORM_ID)));
    assertThat("Unexpected snapshot formRef.", migrated1.get("formRef"), is(expectedFormRef));
    Map<String, Object> current1 = getEmbeddedMap(migrated1, List.of("status", "current"));
    assertThat("Unexpected snapshot state.", current1.get("state"), is(SUBMITTED));
    assertThat("Unexpected snapshot revision.", current1.get("revision"), is(0));

    Document migrated2 = migrated.get(1);
    assertThat("Unexpected ID.", migrated2.get("_id"), both(notNullValue()).and(not(FORM_ID)));
    assertThat("Unexpected snapshot formRef.", migrated2.get("formRef"), is(expectedFormRef));
    Map<String, Object> current2 = getEmbeddedMap(migrated2, List.of("status", "current"));
    assertThat("Unexpected snapshot state.", current2.get("state"), is(SUBMITTED));
    assertThat("Unexpected snapshot revision.", current2.get("revision"), is(1));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSnapshotSubmittedMetadataFromS3Versions(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", DELETED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(DELETED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_2_VERSION).build(),
                ObjectVersion.builder().versionId(UNSUBMITTED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenAnswer(inv -> {
          Consumer<HeadObjectRequest.Builder> builder = inv.getArgument(0);
          HeadObjectRequest request = HeadObjectRequest.builder().applyMutation(builder).build();

          LifecycleState lifecycleState = switch (request.versionId()) {
            case DELETED_VERSION -> DELETED;
            case SUBMITTED_1_VERSION, SUBMITTED_2_VERSION -> SUBMITTED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          Instant lastModified = switch (request.versionId()) {
            case DELETED_VERSION -> DELETED_MODIFIED;
            case SUBMITTED_1_VERSION -> SUBMITTED_1_MODIFIED;
            case SUBMITTED_2_VERSION -> SUBMITTED_2_MODIFIED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED_MODIFIED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          return HeadObjectResponse.builder()
              .lastModified(lastModified)
              .metadata(Map.of(METADATA_STATE, lifecycleState.toString()))
              .build();
        });

    String s3Content1 = """
            {
              "_id": "%s",
              "traineeTisId": "trainee1",
              "lifecycleState": "SUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "2024-03-06T16:43:10.413",
              "lastModifiedDate": "2025-04-07T16:43:10.413"
            }
        """.formatted(FORM_ID);
    String s3Content2 = """
            {
              "_id": "%s",
              "traineeTisId": "trainee2",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "2026-05-08T16:43:10.413",
              "lastModifiedDate": "2027-06-09T16:43:10.413"
            }
        """.formatted(FORM_ID);
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content1.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content2.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(2)).save(documentCaptor.capture(),
        eq(formClass == FormRPartA.class ? PART_A_HISTORY_COLLECTION : PART_B_HISTORY_COLLECTION));

    List<Document> migrated = documentCaptor.getAllValues();
    assertThat("Unexpected snapshot count.", migrated, hasSize(2));

    Document migrated1 = migrated.get(0);
    assertThat("Unexpected trainee ID.", migrated1.get("traineeTisId"), is("trainee1"));
    assertThat("Unexpected snapshot formRef.", migrated1.get("lastModified"),
        is(Instant.parse("2025-04-07T16:43:10.413Z")));
    Map<String, Object> status1 = getEmbeddedMap(migrated1, List.of("status"));
    assertThat("Unxpected submission date.", status1.get("submitted"),
        is(Instant.parse("2024-03-06T16:43:10.413Z")));

    Document migrated2 = migrated.get(1);
    assertThat("Unexpected trainee ID.", migrated2.get("traineeTisId"), is("trainee2"));
    assertThat("Unexpected snapshot formRef.", migrated2.get("lastModified"),
        is(Instant.parse("2027-06-09T16:43:10.413Z")));
    Map<String, Object> status2 = getEmbeddedMap(migrated2, List.of("status"));
    assertThat("Unxpected submission date.", status2.get("submitted"),
        is(Instant.parse("2026-05-08T16:43:10.413Z")));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSnapshotSubmittedContentFromS3Versions(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", DELETED.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(DELETED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_2_VERSION).build(),
                ObjectVersion.builder().versionId(UNSUBMITTED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenAnswer(inv -> {
          Consumer<HeadObjectRequest.Builder> builder = inv.getArgument(0);
          HeadObjectRequest request = HeadObjectRequest.builder().applyMutation(builder).build();

          LifecycleState lifecycleState = switch (request.versionId()) {
            case DELETED_VERSION -> DELETED;
            case SUBMITTED_1_VERSION, SUBMITTED_2_VERSION -> SUBMITTED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          Instant lastModified = switch (request.versionId()) {
            case DELETED_VERSION -> DELETED_MODIFIED;
            case SUBMITTED_1_VERSION -> SUBMITTED_1_MODIFIED;
            case SUBMITTED_2_VERSION -> SUBMITTED_2_MODIFIED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED_MODIFIED;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          return HeadObjectResponse.builder()
              .lastModified(lastModified)
              .metadata(Map.of(METADATA_STATE, lifecycleState.toString()))
              .build();
        });

    String s3Content1 = """
            {
              "_id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "2024-03-06T16:43:10.413",
              "lastModifiedDate": "2025-04-07T16:43:10.413"
            }
        """.formatted(FORM_ID);
    String s3Content2 = """
            {
              "_id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "2026-05-08T16:43:10.413",
              "lastModifiedDate": "2027-06-09T16:43:10.413"
            }
        """.formatted(FORM_ID);
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content1.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3Content2.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(2)).save(documentCaptor.capture(),
        eq(formClass == FormRPartA.class ? PART_A_HISTORY_COLLECTION : PART_B_HISTORY_COLLECTION));

    List<Document> migrated = documentCaptor.getAllValues();
    assertThat("Unexpected snapshot count.", migrated, hasSize(2));

    Document migrated1 = migrated.get(0);
    Map<String, Object> content1 = getEmbeddedMap(migrated1, List.of("content"));
    assertThat("Unexpected forename.", content1.get("forename"), is("Tony"));
    assertThat("Unexpected surname.", content1.get("surname"), is("Gill"));
    assertThat("Unexpected email.", content1.get("email"), is("tony.gill@example.com"));

    Document migrated2 = migrated.get(1);
    Map<String, Object> content2 = getEmbeddedMap(migrated2, List.of("content"));
    assertThat("Unexpected forename.", content2.get("forename"), is("Anthony"));
    assertThat("Unexpected surname.", content2.get("surname"), is("Gilliam"));
    assertThat("Unexpected email.", content2.get("email"), is("anthony.gilliam@example.com"));
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(mongoTemplate);
    verifyNoInteractions(s3Client);
  }

  /**
   * Get an embedded Map<String, Object> from a Document by traversing the specified keys.
   *
   * @param document The document to get an embedded map from.
   * @param keys     A list of keys, each item represents a level of nesting to traverse to get the
   *                 target embedded map.
   * @return The embedded map.
   */
  private Map<String, Object> getEmbeddedMap(Document document, List<String> keys) {
    Map<String, Object> current = document;

    for (String key : keys) {
      current = (Map<String, Object>) current.get(key);
    }

    return current;
  }
}
