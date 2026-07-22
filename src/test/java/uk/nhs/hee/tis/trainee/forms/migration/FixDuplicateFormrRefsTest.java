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

import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartaSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

class FixDuplicateFormrRefsTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();

  private static final String PART_A_COLLECTION = "form-r-part-a";
  private static final String PART_B_COLLECTION = "form-r-part-b";

  private static final String PART_A_HISTORY_COLLECTION = "form-r-part-a-history";
  private static final String PART_B_HISTORY_COLLECTION = "form-r-part-b-history";

  private FixDuplicateFormrRefs migration;

  private MongoTemplate mongoTemplate;
  private FormRPartAService partaService;
  private FormRPartBService partbService;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    partaService = mock(FormRPartAService.class);
    partbService = mock(FormRPartBService.class);
    migration = new FixDuplicateFormrRefs(mongoTemplate, partaService, partbService);

    when(mongoTemplate.getCollectionName(FormRPartA.class)).thenReturn(PART_A_COLLECTION);
    when(mongoTemplate.getCollectionName(FormRPartB.class)).thenReturn(PART_B_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartaSubmissionHistory.class)).thenReturn(
        PART_A_HISTORY_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));
    when(partaService.getFormRPartAById(anyString())).thenReturn(Optional.empty());
    when(partbService.getFormRPartBById(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();

    verifyNoInteractions(mongoTemplate, partaService, partbService);
  }

  @Test
  void shouldDoNothingWhenNoDuplicateFormRefsFound() {
    when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), eq(Document.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    migration.migrateCollections();

    verify(mongoTemplate, never()).save(any(Document.class), anyString());
    verifyNoInteractions(partaService, partbService);
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldUpdateLatestFormRefWhenDuplicateExists(Class<?> formClass) {
    String formCollection = getCollectionName(formClass);
    String historyCollection = getHistoryCollectionName(formClass);
    String formRefPrefix = getFormRefPrefix(formClass);
    UUID latestFormId = UUID.randomUUID();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(formCollection), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID));

    when(mongoTemplate.find(any(), eq(Document.class), eq(formCollection)))
        .thenReturn(List.of(
            createForm(latestFormId, formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        ));

    when(mongoTemplate.exists(any(), eq(historyCollection))).thenReturn(false);

    migration.migrateCollections();

    ArgumentCaptor<Document> formCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(formCaptor.capture(), eq(formCollection));

    Document migratedForm = formCaptor.getValue();
    assertThat("Unexpected ID.", migratedForm.get("_id", UUID.class),
        is(latestFormId));
    assertThat("Unexpected updated form reference.", migratedForm.getString("formRef"),
        is(formRefPrefix + "002"));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldUpdateNonLatestFormRefWhenDuplicateExists(Class<?> formClass) {
    String formCollection = getCollectionName(formClass);
    String historyCollection = getHistoryCollectionName(formClass);
    String formRefPrefix = getFormRefPrefix(formClass);
    UUID nonLatestFormId = UUID.randomUUID();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(formCollection), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID));

    when(mongoTemplate.find(any(), eq(Document.class), eq(formCollection)))
        .thenReturn(List.of(
            createForm(UUID.randomUUID(), formRefPrefix + "004", 0, Instant.now()),
            createForm(nonLatestFormId, formRefPrefix + "002", 0, Instant.now().minus(1, DAYS)),
            createForm(UUID.randomUUID(), formRefPrefix + "002", 0, Instant.now().minus(2, DAYS)),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(3, DAYS))
        ));

    when(mongoTemplate.exists(any(), eq(historyCollection))).thenReturn(false);

    migration.migrateCollections();

    ArgumentCaptor<Document> formCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(formCaptor.capture(), eq(formCollection));

    Document migratedForm = formCaptor.getValue();
    assertThat("Unexpected ID.", migratedForm.get("_id", UUID.class),
        is(nonLatestFormId));
    assertThat("Unexpected updated form reference.", migratedForm.getString("formRef"),
        is(formRefPrefix + "003"));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldCreateHistoryWhenDuplicateExists(Class<?> formClass) {
    String formCollection = getCollectionName(formClass);
    String historyCollection = getHistoryCollectionName(formClass);
    String formRefPrefix = getFormRefPrefix(formClass);
    UUID latestFormId = UUID.randomUUID();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(formCollection), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID));

    when(mongoTemplate.find(any(), eq(Document.class), eq(formCollection)))
        .thenReturn(List.of(
            createForm(latestFormId, formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        ));

    when(mongoTemplate.exists(any(), eq(historyCollection))).thenReturn(false);

    migration.migrateCollections();

    ArgumentCaptor<Document> historyCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(historyCaptor.capture(), eq(historyCollection));

    Document history = historyCaptor.getValue();
    assertThat("Unexpected history form reference.", history.getString("formRef"),
        is(formRefPrefix + "002"));
    assertThat("Expected a new history ID.", history.get("_id", UUID.class), is(not(latestFormId)));

    var historyClass = formClass == FormRPartA.class
        ? FormrPartaSubmissionHistory.class
        : FormrPartbSubmissionHistory.class;
    assertThat("Unexpected history class.", history.getString("_class"),
        is(historyClass.getName()));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldUpdateAllTraineesWithDuplicates(Class<?> formClass) {
    String formCollection = getCollectionName(formClass);
    String historyCollection = getHistoryCollectionName(formClass);
    String formRefPrefix = getFormRefPrefix(formClass);
    UUID latestFormId1 = UUID.randomUUID();
    UUID latestFormId2 = UUID.randomUUID();
    UUID latestFormId3 = UUID.randomUUID();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(formCollection), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID, "123", "456"));

    when(mongoTemplate.find(any(), eq(Document.class), eq(formCollection)))
        .thenReturn(List.of(
            createForm(latestFormId1, formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        ))
        .thenReturn(List.of(
            createForm(latestFormId2, formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        )).thenReturn(List.of(
            createForm(latestFormId3, formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        ));

    when(mongoTemplate.exists(any(), eq(historyCollection))).thenReturn(false);

    migration.migrateCollections();

    ArgumentCaptor<Document> formCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(3)).save(formCaptor.capture(), eq(formCollection));

    List<Document> migratedForms = formCaptor.getAllValues();
    Document migratedForm = migratedForms.get(0);
    assertThat("Unexpected ID.", migratedForm.get("_id", UUID.class),
        is(latestFormId1));

    migratedForm = migratedForms.get(1);
    assertThat("Unexpected ID.", migratedForm.get("_id", UUID.class),
        is(latestFormId2));

    migratedForm = migratedForms.get(2);
    assertThat("Unexpected ID.", migratedForm.get("_id", UUID.class),
        is(latestFormId3));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldUpdateFormRefWhenNotLatestRevision(Class<?> formClass) {
    String formCollection = getCollectionName(formClass);
    String historyCollection = getHistoryCollectionName(formClass);
    String formRefPrefix = getFormRefPrefix(formClass);
    UUID latestFormId = UUID.randomUUID();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(formCollection), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID));

    when(mongoTemplate.find(any(), eq(Document.class), eq(formCollection)))
        .thenReturn(List.of(
            createForm(latestFormId, formRefPrefix + "001", 5, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        ));

    when(mongoTemplate.exists(any(), eq(historyCollection))).thenReturn(false);

    migration.migrateCollections();

    ArgumentCaptor<Document> formCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(formCaptor.capture(), eq(formCollection));

    Document migratedForm = formCaptor.getValue();
    assertThat("Unexpected updated form reference.", migratedForm.getString("formRef"),
        is(formRefPrefix + "002"));
    assertThat("Unexpected revision.", migratedForm.getInteger("revision"), is(5));

    ArgumentCaptor<Document> historyCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(historyCaptor.capture(), eq(historyCollection));

    Document history = historyCaptor.getValue();
    assertThat("Unexpected history form reference.", history.getString("formRef"),
        is(formRefPrefix + "002"));
    assertThat("Expected a new history ID.", history.get("_id", UUID.class), is(not(latestFormId)));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSkipUpdateWhenGeneratedFormRefAlreadyInHistory(Class<?> formClass) {
    String formCollection = getCollectionName(formClass);
    String historyCollection = getHistoryCollectionName(formClass);
    String formRefPrefix = getFormRefPrefix(formClass);

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(formCollection), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID));

    when(mongoTemplate.find(any(), eq(Document.class), eq(formCollection)))
        .thenReturn(List.of(
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        ));

    when(mongoTemplate.exists(any(), eq(historyCollection))).thenReturn(true);

    migration.migrateCollections();

    verify(mongoTemplate, never()).save(any(Document.class), eq(formCollection));
    verify(mongoTemplate, never()).save(any(Document.class), eq(historyCollection));
    verifyNoInteractions(partaService, partbService);
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldRecalculateAllMismatchedFormRefsForTrainee(Class<?> formClass) {
    String formCollection = getCollectionName(formClass);
    String historyCollection = getHistoryCollectionName(formClass);
    String formRefPrefix = getFormRefPrefix(formClass);

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(formCollection), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID));

    when(mongoTemplate.find(any(), eq(Document.class), eq(formCollection)))
        .thenReturn(List.of(
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS)),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(2, DAYS))
        ));

    when(mongoTemplate.exists(any(), eq(historyCollection))).thenReturn(false);

    migration.migrateCollections();

    ArgumentCaptor<Document> formCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(2)).save(formCaptor.capture(), eq(formCollection));

    List<String> savedFormRefs = formCaptor.getAllValues().stream()
        .map(document -> document.getString("formRef"))
        .toList();
    assertThat("Unexpected updated form references.", savedFormRefs,
        containsInAnyOrder(formRefPrefix + "003", formRefPrefix + "002"));

    ArgumentCaptor<Document> historyCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate, times(2)).save(historyCaptor.capture(), eq(historyCollection));

    List<String> savedHistoryRefs = historyCaptor.getAllValues().stream()
        .map(document -> document.getString("formRef"))
        .toList();
    assertThat("Unexpected history form references.", savedHistoryRefs,
        containsInAnyOrder(formRefPrefix + "003", formRefPrefix + "002"));
  }

  @Test
  void shouldPublishUpdateForPartA() {
    String formRefPrefix = getFormRefPrefix(FormRPartA.class);
    UUID latestFormId = UUID.randomUUID();
    FormRPartADto form = new FormRPartADto();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(PART_A_COLLECTION), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_A_COLLECTION)))
        .thenReturn(List.of(
            createForm(latestFormId, formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        ));
    when(mongoTemplate.exists(any(), eq(PART_A_HISTORY_COLLECTION))).thenReturn(false);
    when(partaService.getAdminsFormRPartAById(latestFormId.toString()))
        .thenReturn(Optional.of(form));

    migration.migrateCollections();

    verify(partaService).getAdminsFormRPartAById(latestFormId.toString());
    verify(partaService).publishFormRUpdateEvent(form);
    verifyNoInteractions(partbService);
  }

  @Test
  void shouldPublishUpdateForPartB() {
    String formRefPrefix = getFormRefPrefix(FormRPartB.class);
    UUID latestFormId = UUID.randomUUID();
    FormRPartBDto form = new FormRPartBDto();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(PART_B_COLLECTION), eq(Document.class)))
        .thenReturn(duplicateTrainees(TRAINEE_ID));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(
            createForm(latestFormId, formRefPrefix + "001", 0, Instant.now()),
            createForm(UUID.randomUUID(), formRefPrefix + "001", 0, Instant.now().minus(1, DAYS))
        ));
    when(mongoTemplate.exists(any(), eq(PART_B_HISTORY_COLLECTION))).thenReturn(false);
    when(partbService.getAdminsFormRPartBById(latestFormId.toString()))
        .thenReturn(Optional.of(form));

    migration.migrateCollections();

    verify(partbService).getAdminsFormRPartBById(latestFormId.toString());
    verify(partbService).publishFormRUpdateEvent(form);
    verifyNoInteractions(partaService);
  }

  /**
   * Get a list of trainee IDs that have duplicate form references in the given collection.
   *
   * @param traineeIds The trainee IDs to get duplicates for.
   * @return An aggregation result containing the trainee IDs with duplicate form references.
   */
  private AggregationResults<Document> duplicateTrainees(String... traineeIds) {
    List<Document> results = java.util.Arrays.stream(traineeIds)
        .map(traineeId -> new Document(Map.of("traineeTisId", traineeId)))
        .toList();
    return new AggregationResults<>(results, new Document());
  }

  /**
   * Create a form document with the given parameters.
   *
   * @param id       The ID of the form.
   * @param formRef  The form reference number.
   * @param revision The revision of the form.
   * @param created  The created timestamp.
   * @return The created form.
   */
  private Document createForm(UUID id, String formRef, int revision, Instant created) {
    return new Document(Map.of(
        "_id", id,
        "traineeTisId", TRAINEE_ID,
        "formRef", formRef,
        "revision", revision,
        "created", Date.from(created)
    ));
  }

  /**
   * Get the collection name for the given form class.
   *
   * @param formClass The form class to get the collection name for.
   * @return The collection name.
   */
  private String getCollectionName(Class<?> formClass) {
    return formClass == FormRPartA.class ? PART_A_COLLECTION : PART_B_COLLECTION;
  }

  /**
   * Get the history collection name for the given form class.
   *
   * @param formClass The form class to get the history collection name for.
   * @return The history collection name.
   */
  private String getHistoryCollectionName(Class<?> formClass) {
    return formClass == FormRPartA.class ? PART_A_HISTORY_COLLECTION : PART_B_HISTORY_COLLECTION;
  }

  /**
   * Get the form reference prefix for the given form class.
   *
   * @param formClass The form class to get the prefix for.
   * @return The form reference prefix.
   */
  private String getFormRefPrefix(Class<?> formClass) {
    return (formClass == FormRPartA.class ? "formr_parta_" : "formr_partb_") + TRAINEE_ID + "_";
  }
}
