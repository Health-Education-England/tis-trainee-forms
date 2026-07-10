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

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;

/**
 * Mongock change unit to migrate remaining Part B forms to the audited form structure. This
 * performs similarly to {@link ConvertFormrToAudited} but with a narrowed scope to address a
 * handful of remaining forms and their unique scenario.
 */
@Slf4j
@ChangeUnit(id = "convertFormrToAudited2", order = "010")
public class ConvertFormrToAudited2 {

  private static final String FIELD_ID = "_id";
  private static final String FIELD_TRAINEE_ID = "traineeTisId";
  private static final String FIELD_LIFECYCLE_STATE = "lifecycleState";
  private static final String FIELD_FORENAME = "forename";
  private static final String FIELD_SURNAME = "surname";
  private static final String FIELD_EMAIL = "email";
  private static final String FIELD_SUBMISSION_DATE = "submissionDate";
  private static final String FIELD_LAST_MODIFIED_DATE = "lastModifiedDate";
  private static final String FIELD_CLASS = "_class";

  private static final String FIELD_FORM_REF = "formRef";
  private static final String FIELD_REVISION = "revision";
  private static final String FIELD_CONTENT = "content";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_CURRENT = "current";
  private static final String FIELD_HISTORY = "history";
  private static final String FIELD_STATE = "state";
  private static final String FIELD_MODIFIED_BY = "modifiedBy";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_ROLE = "role";
  private static final String FIELD_TIMESTAMP = "timestamp";
  private static final String FIELD_SUBMITTED = "submitted";
  private static final String FIELD_CREATED = "created";
  private static final String FIELD_LAST_MODIFIED = "lastModified";

  private final MongoTemplate mongoTemplate;

  /**
   * Constructor for ConvertFormrToAudited2.
   */
  public ConvertFormrToAudited2(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Migrate the remaining Form-R Part B forms to the audited form structure.
   */
  @Execution
  public void migrateFormrPartb() {
    String collectionName = mongoTemplate.getCollectionName(FormRPartB.class);
    Query query = Query.query(
        Criteria.where(FIELD_CONTENT).exists(false)
            .and(FIELD_LIFECYCLE_STATE).is(SUBMITTED));
    List<Document> forms = mongoTemplate.find(query, Document.class, collectionName);
    log.info("Found {} forms of type {} in state SUBMITTED", forms.size(), collectionName);

    for (Document form : forms) {
      // The target forms are all known to be 001.
      String formRef = "formr_partb_%s_001".formatted(form.getString(FIELD_TRAINEE_ID));

      // Delete any built submission history so it can be retried without duplication.
      deleteSubmissionHistory(formRef);

      List<Map<String, Object>> history = rebuildHistory(formRef, form);

      Document migratedForm = transformForm(true, formRef, form, history);
      mongoTemplate.save(migratedForm, collectionName);
    }

    log.info("Successfully migrated {} forms of type {}", forms.size(), collectionName);
  }

  /**
   * Rebuild the status history of the form based on the database data. Submitted forms will be
   * snapshotted and saved in the form's submission history.
   *
   * @param formRef The form reference for the form.
   * @param form    The form to rebuild the history for.
   * @return A list of statuses representing the form history.
   */
  private List<Map<String, Object>> rebuildHistory(String formRef, Document form) {
    List<Map<String, Object>> history = new ArrayList<>();
    Instant submitted = ((Date) form.remove(FIELD_SUBMISSION_DATE)).toInstant();

    // Create a dummy DRAFT version.
    history.add(buildStatusInfo(DRAFT, form, submitted));

    // Create the SUBMITTED version.
    history.add(buildStatusInfo(SUBMITTED, form, submitted));

    // Create submission snapshot.
    Document document = transformForm(false, formRef, form, history);
    String historyCollection = mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class);
    mongoTemplate.save(document, historyCollection);

    return history;
  }

  /**
   * Transform the structure of the form to fit the audited form structure. Any non-recognized
   * fields will be considered content.
   *
   * @param copyFormId Whether to copy the form ID to the new version. Generally true for actual
   *                   forms and false for history snapshots.
   * @param formRef    The form reference for the form.
   * @param form       The form to transform.
   * @param history    The pre-built history to inject in to the audit history of the form. The
   *                   current status will be pulled from the latest history item.
   * @return The transformed form as a {@link Document}.
   */
  private Document transformForm(boolean copyFormId, String formRef, Document form,
      List<Map<String, Object>> history) {
    Map<String, Object> content = new HashMap<>(form);

    // Remove non-content fields.
    content.remove(FIELD_CLASS);
    content.remove(FIELD_LAST_MODIFIED_DATE);
    content.remove(FIELD_LIFECYCLE_STATE);
    content.remove(FIELD_SUBMISSION_DATE);

    UUID formId = UUID.fromString(content.remove(FIELD_ID).toString());
    String traineeId = (String) content.remove(FIELD_TRAINEE_ID);

    Document migratedForm = new Document();
    migratedForm.put(FIELD_ID, copyFormId ? formId : UUID.randomUUID());
    migratedForm.put(FIELD_TRAINEE_ID, traineeId);
    migratedForm.put(FIELD_FORM_REF, formRef);
    migratedForm.put(FIELD_REVISION, 0);
    migratedForm.put(FIELD_CONTENT, content);

    // Build status.
    Map<String, Object> status = new LinkedHashMap<>();
    Map<String, Object> latestHistory = history.get(history.size() - 1);
    status.put(FIELD_CURRENT, latestHistory);
    status.put(FIELD_HISTORY, history);
    migratedForm.put(FIELD_STATUS, status);

    // Each of the targeted forms only has a single timestamp available.
    Instant timestamp = (Instant) latestHistory.get(FIELD_TIMESTAMP);
    status.put(FIELD_SUBMITTED, timestamp);
    migratedForm.put(FIELD_CREATED, timestamp);
    migratedForm.put(FIELD_LAST_MODIFIED, timestamp);

    migratedForm.put(FIELD_CLASS, FormRPartB.class.getName());
    return migratedForm;
  }

  /**
   * Get the details of the modifying user.
   *
   * @param form The form content to get details from.
   * @return A map containing the details of the modifying user.
   */
  private Map<String, String> getModifiedBy(Document form) {
    Map<String, String> modifiedBy = new LinkedHashMap<>();
    modifiedBy.put(FIELD_NAME, "%s %s".formatted(
        form.getString(FIELD_FORENAME),
        form.getString(FIELD_SURNAME)
    ).trim());
    modifiedBy.put(FIELD_EMAIL, form.getString(FIELD_EMAIL));
    modifiedBy.put(FIELD_ROLE, "TRAINEE");
    return modifiedBy;
  }

  /**
   * Build a status info map for the given lifecycle state and form details.
   *
   * @param state        The lifecycle state to build the status info for.
   * @param form         The form document, will be used to retrieve name and email details.
   * @param lastModified The timestamp of the last modification.
   * @return The built status information.
   */
  private Map<String, Object> buildStatusInfo(LifecycleState state, Document form,
      Instant lastModified) {
    Map<String, Object> statusInfo = new LinkedHashMap<>();
    statusInfo.put(FIELD_STATE, state);
    statusInfo.put(FIELD_MODIFIED_BY, getModifiedBy(form));
    statusInfo.put(FIELD_TIMESTAMP, lastModified);
    statusInfo.put(FIELD_REVISION, 0);

    return statusInfo;
  }

  /**
   * Delete submission history for the given form reference. This is used to clean up any history
   * created for forms that fail to migrate, to avoid orphaned history documents remaining after the
   * migration is complete.
   *
   * @param formRef The form reference to delete for.
   */
  private void deleteSubmissionHistory(String formRef) {
    log.debug("Rolling back form ref {}", formRef);
    String collectionName = mongoTemplate.getCollectionName(FormrPartbSubmissionHistory.class);

    Query query = Query.query(Criteria.where(FIELD_FORM_REF).is(formRef));
    DeleteResult result = mongoTemplate.remove(query, collectionName);
    log.debug("Removed {} submission history documents for form ref {}", result.getDeletedCount(),
        formRef);
  }

  /**
   * Do not attempt rollback, any successfully migrated forms should stay updated.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'ConvertFormrToAudited2' migration.");
  }
}
