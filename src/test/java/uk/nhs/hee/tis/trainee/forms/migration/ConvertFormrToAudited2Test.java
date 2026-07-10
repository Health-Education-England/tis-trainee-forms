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
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.mongodb.client.result.DeleteResult;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;

class ConvertFormrToAudited2Test {

  private static final UUID FORM_ID = UUID.randomUUID();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final Instant SUBMISSION_DATE = Instant.now().minus(10, DAYS);

  private static final String PART_B_COLLECTION = "form-r-part-b";
  private static final String PART_B_HISTORY_COLLECTION = "form-r-part-b-history";

  private ConvertFormrToAudited2 migration;

  private MongoTemplate mongoTemplate;

  @BeforeEach
  void setUp() {
    mongoTemplate = mock(MongoTemplate.class);
    migration = new ConvertFormrToAudited2(mongoTemplate);

    when(mongoTemplate.getCollectionName(FormRPartB.class)).thenReturn(PART_B_COLLECTION);
    when(mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class)).thenReturn(
        PART_B_HISTORY_COLLECTION);

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(0));
  }

  @Test
  void shouldStopMigrationWhenUnhandledExceptionOccurs() {
    Document before1 = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    Document before2 = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(before1, before2));

    when(mongoTemplate.remove(any(), any(String.class)))
        .thenReturn(DeleteResult.acknowledged(1))
        .thenThrow(RuntimeException.class);

    assertThrows(RuntimeException.class, () -> migration.migrateFormrPartb());

    // First form should still save.
    verify(mongoTemplate, times(1)).save(any(), eq(PART_B_COLLECTION));
  }

  @Test
  void shouldMigrateToAuditedStructure() {
    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(before));

    migration.migrateFormrPartb();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_B_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected root keys.", migrated.keySet(),
        containsInAnyOrder("_id", "traineeTisId", "formRef", "revision", "content", "status",
            "created", "lastModified", "_class"));

    Map<String, Object> migratedStatus = getEmbeddedMap(migrated, List.of("status"));
    assertThat("Unexpected status keys.", migratedStatus.keySet(),
        containsInAnyOrder("current", "submitted", "history"));
  }

  @Test
  void shouldSetFormMetadata() {
    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(before));

    migration.migrateFormrPartb();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_B_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected ID.", migrated.get("_id", UUID.class), is(FORM_ID));
    assertThat("Unexpected trainee ID.", migrated.getString("traineeTisId"), is(TRAINEE_ID));

    assertThat("Unexpected created timestamp.", migrated.get("created", Instant.class),
        is(SUBMISSION_DATE.truncatedTo(MILLIS)));
    assertThat("Unexpected modified timestamp.", migrated.get("lastModified", Instant.class),
        is(SUBMISSION_DATE.truncatedTo(MILLIS)));
    assertThat("Unexpected document class.", migrated.getString("_class"),
        is(FormRPartB.class.getName()));
  }

  @Test
  void shouldSetFormRef() {
    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(before));

    migration.migrateFormrPartb();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_B_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected form reference.", migrated.get("formRef"),
        is("formr_partb_" + TRAINEE_ID + "_001"));
  }

  @Test
  void shouldCleanUpHistory() {

    Document before = new Document(Map.of(
        "_id", UUID.randomUUID(),
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(before));

    when(mongoTemplate.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateFormrPartb();

    verify(mongoTemplate).remove(any(), eq(PART_B_HISTORY_COLLECTION));
  }

  @Test
  void shouldSetRevision() {
    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(before));

    migration.migrateFormrPartb();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_B_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    assertThat("Unexpected revision.", migrated.get("revision"), is(0));
  }

  @Test
  void shouldSetContent() {
    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(before));

    migration.migrateFormrPartb();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_B_COLLECTION));

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

  @Test
  void shouldSetSubmitted() {
    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION)))
        .thenReturn(List.of(before));

    migration.migrateFormrPartb();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_B_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> migratedStatus = getEmbeddedMap(migrated, List.of("status"));
    assertThat("Unexpected status keys.", migratedStatus.keySet(),
        containsInAnyOrder("current", "submitted", "history"));

    var submitted = migratedStatus.get("submitted");
    assertThat("Unexpected submitted date.", submitted, is(SUBMISSION_DATE.truncatedTo(MILLIS)));
  }

  @Test
  void shouldSetCurrentStatus() {
    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "contentKey", "contentValue",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION))).thenReturn(
        List.of(before));

    migration.migrateFormrPartb();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_B_COLLECTION));

    Document migrated = documentCaptor.getValue();
    assertThat("Unexpected migrated form.", migrated, notNullValue());

    Map<String, Object> migratedCurrent = getEmbeddedMap(migrated, List.of("status", "current"));
    assertThat("Unexpected current status keys.", migratedCurrent.keySet(),
        containsInAnyOrder("state", "modifiedBy", "timestamp", "revision"));
    assertThat("Unexpected lifecycle state.", migratedCurrent.get("state"), is(SUBMITTED));
    assertThat("Unexpected current status timestamp.", migratedCurrent.get("timestamp"),
        is(SUBMISSION_DATE.truncatedTo(MILLIS)));
    assertThat("Unexpected current status revision.", migratedCurrent.get("revision"), is(0));
  }

  @Test
  void shouldSetCurrentModifiedByToTrainee() {
    Document before = new Document(Map.of(
        "_id", FORM_ID,
        "traineeTisId", TRAINEE_ID,
        "forename", "Anthony",
        "surname", "Gilliam",
        "email", "anthony.gilliam@example.com",
        "lifecycleState", "SUBMITTED",
        "submissionDate", Date.from(SUBMISSION_DATE),
        "_class", "uk.tis.nhs.trainee.forms.model.MyForm"
    ));
    when(mongoTemplate.find(any(), eq(Document.class), eq(PART_B_COLLECTION))).thenReturn(
        List.of(before));

    migration.migrateFormrPartb();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).save(documentCaptor.capture(), eq(PART_B_COLLECTION));

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

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(mongoTemplate);
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
}
