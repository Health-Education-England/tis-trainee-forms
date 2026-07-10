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
import static org.mockito.ArgumentMatchers.anyString;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest.Builder;
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
  private static final Instant SUBMISSION_DATE_1 = Instant.now().minus(20, DAYS);
  private static final Instant SUBMISSION_DATE_2 = Instant.now().minus(10, DAYS);
  private static final Instant LAST_MODIFIED = Instant.now().minus(5, DAYS);

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
    Environment env = mock(Environment.class);
    when(env.getProperty("application.file-store.bucket")).thenReturn(S3_BUCKET);
    migration = new ConvertFormrToAudited(mongoTemplate, s3Client, env);

    when(mongoTemplate.getCollectionName(any())).thenReturn("");
    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(0));

    when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "DELETED"})
  void shouldSkipFormWhenNotDraftAndNotFoundInS3(LifecycleState state) {
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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before2), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before2), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before1), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before1), new Document()));

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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before2), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before2), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before1), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before1), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder().build());

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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before2), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before2), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before1), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before1), new Document()));

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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before2), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before1), new Document()));

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
  void shouldStopMigrationWhenUnhandledExceptionOccurs(Class<?> formClass) {
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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before2), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before1), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenThrow(NoSuchBucketException.class);

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    assertThrows(RuntimeException.class, () -> migration.migrateCollections());

    // Draft should still save.
    verify(mongoTemplate, times(1)).save(any(), eq(collectionName));
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
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE_1),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    mockS3Endpoints(1);

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected root keys.", migrated.keySet(),
        containsInAnyOrder("_id", "traineeTisId", "formRef", "revision", "content", "status",
            "created", "lastModified", "_class"));

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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected ID.", migrated.get("_id", UUID.class), is(FORM_ID));
    assertThat("Unexpected trainee ID.", migrated.getString("traineeTisId"), is(TRAINEE_ID));

    assertThat("Unexpected created timestamp.", migrated.get("created", Instant.class),
        is(LAST_MODIFIED.truncatedTo(MILLIS)));
    assertThat("Unexpected modified timestamp.", migrated.get("lastModified", Instant.class),
        is(LAST_MODIFIED.truncatedTo(MILLIS)));
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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected form reference.", migrated.get("formRef"), nullValue());
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldNotCleanUpHistoryWhenNoFormRefGenerated(Class<?> formClass) {
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
        "lifecycleState", DRAFT.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenThrow(NoSuchKeyException.class);

    migration.migrateCollections();

    verify(mongoTemplate, never()).remove(any(), eq(PART_A_HISTORY_COLLECTION));
    verify(mongoTemplate, never()).remove(any(), eq(PART_B_HISTORY_COLLECTION));
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
        "submissionDate", Date.from(SUBMISSION_DATE_2),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(PART_A_COLLECTION), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_A_COLLECTION)))
        .thenReturn(List.of(
            new Document(Map.of(
                "_id", UUID.randomUUID(),
                "submissionDate", Date.from(SUBMISSION_DATE_1)
            )),
            new Document(Map.of(
                "_id", UUID.randomUUID(),
                "submissionDate", Date.from(SUBMISSION_DATE_1)
            )),
            before
        ));

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
            .metadata(Map.of(METADATA_STATE, state.toString()))
            .build())
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build());

    String s3ContentTemplate = """
            {
              "id": "%s",
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
                LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
                LocalDateTime.ofInstant(LAST_MODIFIED, UTC)).getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3ContentTemplate.formatted(FORM_ID, TRAINEE_ID, state,
                LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
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
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "DELETED"})
  void shouldCleanUpHistoryWhenFormRefGenerated(LifecycleState state) {
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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(state == UNSUBMITTED ? List.of(before) : List.of(),
            new Document()))
        .thenReturn(new AggregationResults<>(state == UNSUBMITTED ? List.of(before) : List.of(),
            new Document()))
        .thenReturn(new AggregationResults<>(state != UNSUBMITTED ? List.of(before) : List.of(),
            new Document()))
        .thenReturn(new AggregationResults<>(state != UNSUBMITTED ? List.of(before) : List.of(),
            new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenThrow(NoSuchKeyException.class);

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    verify(mongoTemplate).remove(any(), eq(PART_A_HISTORY_COLLECTION));
    verify(mongoTemplate).remove(any(), eq(PART_B_HISTORY_COLLECTION));
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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

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
        "submissionDate", Date.from(SUBMISSION_DATE_1),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    mockS3Endpoints(3);

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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

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
        "submissionDate", Date.from(SUBMISSION_DATE_2),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(PART_A_COLLECTION), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_A_COLLECTION)))
        .thenReturn(List.of(
            new Document(Map.of(
                "_id", UUID.randomUUID(),
                "submissionDate", Date.from(SUBMISSION_DATE_1)
            )),
            before
        ));

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
            .metadata(Map.of(METADATA_STATE, state.toString()))
            .build())
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build());

    String s3ContentTemplate = """
            {
              "id": "%s",
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
                LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
                LocalDateTime.ofInstant(LAST_MODIFIED, UTC)).getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(s3ContentTemplate.formatted(FORM_ID, TRAINEE_ID, state,
                LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
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
    assertThat("Unexpected submitted date.", submitted, is(SUBMISSION_DATE_1));
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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

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
        is(LAST_MODIFIED.truncatedTo(MILLIS)));
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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(PART_A_COLLECTION), eq(Document.class)))
        .thenReturn(
            new AggregationResults<>(state == DRAFT ? List.of(before) : List.of(), new Document()))
        .thenReturn(new AggregationResults<>(state == SUBMITTED ? List.of(before) : List.of(),
            new Document()));

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
              "id": "%s",
              "traineeTisId": "%s",
              "lifecycleState": "%s",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, TRAINEE_ID, state, LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
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
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(PART_A_COLLECTION), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

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
            .metadata(Map.of(METADATA_STATE, state.toString()))
            .build())
        .thenReturn(HeadObjectResponse.builder()
            .lastModified(LAST_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build());

    String submittedContent = """
            {
              "id": "%s",
              "traineeTisId": "%s",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "lifecycleState": "SUBMITTED",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, TRAINEE_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
        LocalDateTime.ofInstant(LAST_MODIFIED, UTC));
    String transitionedContent = """
            {
              "id": "%s",
              "traineeTisId": "%s",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "lifecycleState": "%s",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, TRAINEE_ID, state, LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
        LocalDateTime.ofInstant(LAST_MODIFIED, UTC));
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(submittedContent.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(transitionedContent.getBytes())));

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
  void shouldSetCurrentModifiedByToDummyTraineeWhenVersionsDeletedAndNullFields(
      Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Map<String, Object> beforeFields = new HashMap<>();
    beforeFields.put("_id", FORM_ID);
    beforeFields.put("traineeTisId", TRAINEE_ID);
    beforeFields.put("forename", null);
    beforeFields.put("surname", null);
    beforeFields.put("email", null);
    beforeFields.put("lifecycleState", DELETED.toString());
    beforeFields.put("lastModifiedDate", Date.from(LAST_MODIFIED));
    beforeFields.put("_class", "uk.tis.nhs.trainee.forms.model.MyForm");

    Document before = new Document(beforeFields);
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(DELETED_VERSION).build())
            .build());

    mockHeadObject();

    String content = """
            {
              "id": "%s",
              "forename": null,
              "surname": null,
              "email": null,
              "lifecycleState": "DELETED",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_2, UTC),
        LocalDateTime.ofInstant(DELETED_MODIFIED, UTC));
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(content.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> status = getEmbeddedMap(migrated, List.of("status"));
    List<Map<String, Object>> history = (List<Map<String, Object>>) status.get("history");
    assertThat("Unexpected history size.", history, hasSize(3));

    Map<String, Object> modifiedBy = (Map<String, Object>) history.get(0).get("modifiedBy");
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Name Deleted"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"),
        is("no-reply@trainee.tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("TRAINEE"));

    modifiedBy = (Map<String, Object>) history.get(1).get("modifiedBy");
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Name Deleted"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"),
        is("no-reply@trainee.tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("TRAINEE"));

    modifiedBy = (Map<String, Object>) history.get(2).get("modifiedBy");
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Unknown Admin"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"), is("no-reply@tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("ADMIN"));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSetCurrentModifiedByToDummyTraineeWhenVersionsDeletedAndEmptyFields(
      Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "",
        "surname", "",
        "email", "",
        "lifecycleState", DELETED.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(DELETED_VERSION).build())
            .build());

    mockHeadObject();

    String content = """
            {
              "id": "%s",
              "forename": "",
              "surname": "",
              "email": "",
              "lifecycleState": "DELETED",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_2, UTC),
        LocalDateTime.ofInstant(DELETED_MODIFIED, UTC));
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(content.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> status = getEmbeddedMap(migrated, List.of("status"));
    List<Map<String, Object>> history = (List<Map<String, Object>>) status.get("history");
    assertThat("Unexpected history size.", history, hasSize(3));

    Map<String, Object> modifiedBy = (Map<String, Object>) history.get(0).get("modifiedBy");
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Name Deleted"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"),
        is("no-reply@trainee.tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("TRAINEE"));

    modifiedBy = (Map<String, Object>) history.get(1).get("modifiedBy");
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Name Deleted"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"),
        is("no-reply@trainee.tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("TRAINEE"));

    modifiedBy = (Map<String, Object>) history.get(2).get("modifiedBy");
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Unknown Admin"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"), is("no-reply@tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("ADMIN"));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSetCurrentModifiedByToDummyTraineeWhenVersionsDeletedAndMissingFields(
      Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", DELETED.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(DELETED_VERSION).build())
            .build());

    mockHeadObject();

    String content = """
            {
              "id": "%s",
              "lifecycleState": "DELETED",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_2, UTC),
        LocalDateTime.ofInstant(DELETED_MODIFIED, UTC));
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(content.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> status = getEmbeddedMap(migrated, List.of("status"));
    List<Map<String, Object>> history = (List<Map<String, Object>>) status.get("history");
    assertThat("Unexpected history size.", history, hasSize(3));

    Map<String, Object> modifiedBy = (Map<String, Object>) history.get(0).get("modifiedBy");
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Name Deleted"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"),
        is("no-reply@trainee.tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("TRAINEE"));

    modifiedBy = (Map<String, Object>) history.get(1).get("modifiedBy");
    assertThat("Unexpected modifiedBy keys.", modifiedBy.keySet(),
        containsInAnyOrder("name", "email", "role"));
    assertThat("Unexpected modifiedBy name.", modifiedBy.get("name"), is("Name Deleted"));
    assertThat("Unexpected modifiedBy email.", modifiedBy.get("email"),
        is("no-reply@trainee.tis.nhs.uk"));
    assertThat("Unexpected modifiedBy role.", modifiedBy.get("role"), is("TRAINEE"));

    modifiedBy = (Map<String, Object>) history.get(2).get("modifiedBy");
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
        "submissionDate", Date.from(SUBMISSION_DATE_1),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    mockS3Endpoints(4);

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
        is(SUBMISSION_DATE_1));

    historyItem = history.get(1);
    assertThat("Unexpected history state.", historyItem.get("state"), is(SUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMISSION_DATE_1));

    historyItem = history.get(2);
    assertThat("Unexpected history state.", historyItem.get("state"), is(UNSUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(UNSUBMITTED_MODIFIED));

    historyItem = history.get(3);
    assertThat("Unexpected history state.", historyItem.get("state"), is(SUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMISSION_DATE_2));

    historyItem = history.get(4);
    assertThat("Unexpected history state.", historyItem.get("state"), is(DELETED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(DELETED_MODIFIED));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldRebuildStatusHistoryWhenS3VersionsDeleted(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", DELETED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE_1),
        "lastModifiedDate", Date.from(DELETED_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().versionId(DELETED_VERSION).build())
            .build());

    mockHeadObject();
    mockGetObject();

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> status = getEmbeddedMap(migrated, List.of("status"));

    Map<String, Object> current = (Map<String, Object>) status.get("current");
    assertThat("Unexpected current state.", current.get("state"), is(DELETED));

    List<Map<String, Object>> history = (List<Map<String, Object>>) status.get("history");
    assertThat("Unexpected history size.", history, hasSize(3));

    Map<String, Object> historyItem = history.get(0);
    assertThat("Unexpected history state.", historyItem.get("state"), is(DRAFT));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMISSION_DATE_2));

    historyItem = history.get(1);
    assertThat("Unexpected history state.", historyItem.get("state"), is(SUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMISSION_DATE_2));

    historyItem = history.get(2);
    assertThat("Unexpected history state.", historyItem.get("state"), is(DELETED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(DELETED_MODIFIED));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldIgnoreRepeatedStatesInS3Versions(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", SUBMITTED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE_2),
        "lastModifiedDate", Date.from(SUBMITTED_2_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(SUBMITTED_2_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    mockHeadObject();
    mockGetObject();

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> status = getEmbeddedMap(migrated, List.of("status"));

    Map<String, Object> current = (Map<String, Object>) status.get("current");
    assertThat("Unexpected current state.", current.get("state"), is(SUBMITTED));

    List<Map<String, Object>> history = (List<Map<String, Object>>) status.get("history");
    assertThat("Unexpected history size.", history, hasSize(2));

    Map<String, Object> historyItem = history.get(0);
    assertThat("Unexpected history state.", historyItem.get("state"), is(DRAFT));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMISSION_DATE_2));

    historyItem = history.get(1);
    assertThat("Unexpected history state.", historyItem.get("state"), is(SUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(SUBMISSION_DATE_2));

    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldFixOutOfOrderTimestamps(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Instant earlierModified = SUBMISSION_DATE_1.minus(Duration.ofDays(1000));

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", UNSUBMITTED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE_1),
        "lastModifiedDate", Date.from(earlierModified),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(
                ObjectVersion.builder().versionId(UNSUBMITTED_VERSION).build(),
                ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build()
            )
            .build());

    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenReturn(HeadObjectResponse.builder()
            .versionId(UNSUBMITTED_VERSION)
            .lastModified(earlierModified)
            .metadata(Map.of(METADATA_STATE, UNSUBMITTED.toString()))
            .build())
        .thenReturn(HeadObjectResponse.builder()
            .versionId(SUBMITTED_1_VERSION)
            .lastModified(SUBMITTED_1_MODIFIED)
            .metadata(Map.of(METADATA_STATE, SUBMITTED.toString()))
            .build());

    String submittedContent = """
            {
              "id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "2026-01-01T00:00:00",
              "lastModifiedDate": "2026-02-02T00:00:00"
            }
        """.formatted(FORM_ID);
    String unsubmittedContent = """
            {
              "id": "%s",
              "lifecycleState": "UNSUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "1970-01-01T00:00:00",
              "lastModifiedDate": "1970-01-01T00:00:00"
            }
        """.formatted(FORM_ID);
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(submittedContent.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(unsubmittedContent.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> status = getEmbeddedMap(migrated, List.of("status"));

    Map<String, Object> current = (Map<String, Object>) status.get("current");
    assertThat("Unexpected current state.", current.get("state"), is(UNSUBMITTED));

    List<Map<String, Object>> history = (List<Map<String, Object>>) status.get("history");
    assertThat("Unexpected history size.", history, hasSize(3));

    Map<String, Object> historyItem = history.get(0);
    assertThat("Unexpected history state.", historyItem.get("state"), is(DRAFT));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(Instant.parse("2026-01-01T00:00:00Z")));

    historyItem = history.get(1);
    assertThat("Unexpected history state.", historyItem.get("state"), is(SUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(Instant.parse("2026-01-01T00:00:00Z")));

    historyItem = history.get(2);
    assertThat("Unexpected history state.", historyItem.get("state"), is(UNSUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(Instant.parse("2026-01-01T00:00:00Z")));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldHandleTimelessDatesInS3Versions(Class<?> formClass) {
    String collectionName = formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
    when(mongoTemplate.getCollectionName(formClass)).thenReturn(collectionName);

    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "lifecycleState", SUBMITTED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE_2),
        "lastModifiedDate", Date.from(SUBMITTED_2_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    mockListObjectVersions(1);
    mockHeadObject();

    String submittedContent = """
            {
              "id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "2026-01-01",
              "lastModifiedDate": "2026-02-02"
            }
        """.formatted(FORM_ID);
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(
        new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(submittedContent.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(collectionName));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> status = getEmbeddedMap(migrated, List.of("status"));

    Map<String, Object> current = (Map<String, Object>) status.get("current");
    assertThat("Unexpected current state.", current.get("state"), is(SUBMITTED));

    List<Map<String, Object>> history = (List<Map<String, Object>>) status.get("history");
    assertThat("Unexpected history size.", history, hasSize(2));

    Map<String, Object> historyItem = history.get(0);
    assertThat("Unexpected history state.", historyItem.get("state"), is(DRAFT));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(Instant.parse("2026-01-01T00:00:00Z")));

    historyItem = history.get(1);
    assertThat("Unexpected history state.", historyItem.get("state"), is(SUBMITTED));
    assertThat("Unexpected history last modified.", historyItem.get("timestamp"),
        is(Instant.parse("2026-01-01T00:00:00Z")));
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
        "lifecycleState", SUBMITTED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE_1),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    when(mongoTemplate.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(before));

    mockS3Endpoints(3);

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
        "lifecycleState", SUBMITTED.toString(),
        "submissionDate", Date.from(SUBMISSION_DATE_1),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    mockListObjectVersions(3);
    mockHeadObject();

    String submitted1Content = """
            {
              "id": "%s",
              "traineeTisId": "trainee1",
              "lifecycleState": "SUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
        LocalDateTime.ofInstant(SUBMITTED_1_MODIFIED, UTC));
    String unsubmittedContent = """
            {
              "id": "%s",
              "traineeTisId": "trainee1",
              "lifecycleState": "UNSUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
        LocalDateTime.ofInstant(UNSUBMITTED_MODIFIED, UTC));
    String submitted2Content = """
            {
              "id": "%s",
              "traineeTisId": "trainee2",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_2, UTC),
        LocalDateTime.ofInstant(SUBMITTED_2_MODIFIED, UTC));
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(submitted1Content.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(unsubmittedContent.getBytes())))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),
            new ByteArrayInputStream(submitted2Content.getBytes())));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(2)).save(documentCaptor.capture(),
        eq(formClass == FormRPartA.class ? PART_A_HISTORY_COLLECTION : PART_B_HISTORY_COLLECTION));

    List<Document> migrated = documentCaptor.getAllValues();
    assertThat("Unexpected snapshot count.", migrated, hasSize(2));

    Document migrated1 = migrated.get(0);
    assertThat("Unexpected trainee ID.", migrated1.get("traineeTisId"), is("trainee1"));
    assertThat("Unexpected snapshot modified timestamp.", migrated1.get("lastModified"),
        is(SUBMISSION_DATE_1));
    Map<String, Object> status1 = getEmbeddedMap(migrated1, List.of("status"));
    assertThat("Unexpected submission date.", status1.get("submitted"),
        is(SUBMISSION_DATE_1));

    Document migrated2 = migrated.get(1);
    assertThat("Unexpected trainee ID.", migrated2.get("traineeTisId"), is("trainee2"));
    assertThat("Unexpected snapshot modified timestamp.", migrated2.get("lastModified"),
        is(SUBMISSION_DATE_2));
    Map<String, Object> status2 = getEmbeddedMap(migrated2, List.of("status"));
    assertThat("Unexpected submission date.", status2.get("submitted"),
        is(SUBMISSION_DATE_2));
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
        "lifecycleState", SUBMITTED.toString(),
        "lastModifiedDate", Date.from(LAST_MODIFIED),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionName), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()))
        .thenReturn(new AggregationResults<>(List.of(before), new Document()));

    mockS3Endpoints(3);

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
   * Get an embedded {@code Map<String, Object>} from a Document by traversing the specified keys.
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

  /**
   * Mock S3 endpoints for getting and processing version history data. A number of object versions
   * will be returned based on the given {@code count}. LifecycleState will transition through
   * SUBMITTED, UNSUBMITTED, SUBMITTED, DELETED, with the first {@code count} versions returned.
   *
   * @param count The number of versions to return, must be 1-4
   */
  private void mockS3Endpoints(int count) {
    mockListObjectVersions(count);
    mockHeadObject();
    mockGetObject();
  }

  /**
   * Mock the ListObjectVersions endpoint with sensible defaults. LifecycleState will transition
   * through SUBMITTED, UNSUBMITTED, SUBMITTED, DELETED, with the first {@code count} versions
   * returned.
   *
   * @param count The number of versions to return, must be 1-4
   */
  private void mockListObjectVersions(int count) {
    List<ObjectVersion> versions = new ArrayList<>();
    versions.add(ObjectVersion.builder().versionId(DELETED_VERSION).build());
    versions.add(ObjectVersion.builder().versionId(SUBMITTED_2_VERSION).build());
    versions.add(ObjectVersion.builder().versionId(UNSUBMITTED_VERSION).build());
    versions.add(ObjectVersion.builder().versionId(SUBMITTED_1_VERSION).build());

    when(s3Client.listObjectVersions(
        ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any()))
        .thenReturn(ListObjectVersionsResponse.builder()
            .versions(versions.subList(4 - count, 4))
            .build());
  }

  /**
   * Mock HeadObject API response with defaults based on given version.
   */
  private void mockHeadObject() {
    when(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .thenAnswer(inv -> {
          Consumer<HeadObjectRequest.Builder> builder = inv.getArgument(0);
          HeadObjectRequest request = HeadObjectRequest.builder().applyMutation(builder).build();
          String versionId = request.versionId();

          LifecycleState lifecycleState = switch (versionId) {
            case DELETED_VERSION -> DELETED;
            case SUBMITTED_1_VERSION, SUBMITTED_2_VERSION -> SUBMITTED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED;
            default -> throw new IllegalStateException("Unexpected value: " + versionId);
          };

          Instant lastModified = switch (versionId) {
            case DELETED_VERSION -> DELETED_MODIFIED;
            case SUBMITTED_1_VERSION -> SUBMITTED_1_MODIFIED;
            case SUBMITTED_2_VERSION -> SUBMITTED_2_MODIFIED;
            case UNSUBMITTED_VERSION -> UNSUBMITTED_MODIFIED;
            default -> throw new IllegalStateException("Unexpected value: " + versionId);
          };

          return HeadObjectResponse.builder()
              .versionId(versionId)
              .lastModified(lastModified)
              .metadata(Map.of(METADATA_STATE, lifecycleState.toString()))
              .build();
        });
  }

  /**
   * Mock GetObject API response with defaults based on given version.
   */
  private void mockGetObject() {
    String submitted1Content = """
            {
              "id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
        LocalDateTime.ofInstant(SUBMITTED_1_MODIFIED, UTC));
    String unsubmittedContent = """
            {
              "id": "%s",
              "lifecycleState": "UNSUBMITTED",
              "forename": "Tony",
              "surname": "Gill",
              "email": "tony.gill@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_1, UTC),
        LocalDateTime.ofInstant(UNSUBMITTED_MODIFIED, UTC));
    String submitted2Content = """
            {
              "id": "%s",
              "lifecycleState": "SUBMITTED",
              "forename": "Anthony",
              "surname": "Gilliam",
              "email": "anthony.gilliam@example.com",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_2, UTC),
        LocalDateTime.ofInstant(SUBMITTED_2_MODIFIED, UTC));
    String deletedContent = """
            {
              "id": "%s",
              "lifecycleState": "DELETED",
              "submissionDate": "%s",
              "lastModifiedDate": "%s"
            }
        """.formatted(FORM_ID, LocalDateTime.ofInstant(SUBMISSION_DATE_2, UTC),
        LocalDateTime.ofInstant(DELETED_MODIFIED, UTC));
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenAnswer(inv -> {
          GetObjectRequest request = inv.getArgument(0);

          String content = switch (request.versionId()) {
            case DELETED_VERSION -> deletedContent;
            case SUBMITTED_1_VERSION -> submitted1Content;
            case SUBMITTED_2_VERSION -> submitted2Content;
            case UNSUBMITTED_VERSION -> unsubmittedContent;
            default -> throw new IllegalStateException("Unexpected value: " + request.versionId());
          };

          return new ResponseInputStream<>(GetObjectResponse.builder().build(),
              new ByteArrayInputStream(content.getBytes()));
        });
  }
}
