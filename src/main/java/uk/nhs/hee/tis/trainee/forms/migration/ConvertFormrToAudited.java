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
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartaSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;

/**
 * Mongock change unit to migrate Form-R Part A and Part B collections to the audited form
 * structure. This change unit will read all documents from the Form-R Part A and Part B
 * collections, transform them to the new audited form structure, and save them back to the
 * database.
 */
@Slf4j
@ChangeUnit(id = "convertFormrToAudited", order = "9")
public class ConvertFormrToAudited {

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

  private static final String METADATA_LIFECYCLE_STATE = "lifecyclestate";

  private final MongoTemplate mongoTemplate;
  private final S3Client s3Client;

  private final String bucket;

  private int lastLoggedPercent = 0;

  /**
   * Constructor for ConvertFormrToAudited.
   */
  public ConvertFormrToAudited(MongoTemplate mongoTemplate, S3Client s3Client, Environment env) {
    this.mongoTemplate = mongoTemplate;
    this.s3Client = s3Client;
    this.bucket = env.getProperty("application.file-store.bucket");
  }

  /**
   * Migrate Form-R Part A and Part B collections to the audited form structure.
   */
  @Execution
  public void migrateCollections() {
    migrateFormr(FormRPartA.class, FormrPartaSubmissionHistory.class, "%s/forms/formr-a/%s.json");
    migrateFormr(FormRPartB.class, FormrPartbSubmissionHistory.class, "%s/forms/formr-b/%s.json");
  }

  /**
   * Migrate Form-R collection to the audited form structure.
   *
   * @param formClass              The class of the form to migrate.
   * @param submissionHistoryClass The class of the form's submission history.
   * @param bucketKeyTemplate      The key template to use with this form.
   */
  private void migrateFormr(Class<?> formClass, Class<?> submissionHistoryClass,
      String bucketKeyTemplate) {
    String collectionName = mongoTemplate.getCollectionName(formClass);

    // Content will only be present on the new Form-R Part A and Part B documents, so we can use its
    // presence to identify documents that need to be migrated.
    Query query = Query.query(Criteria.where(FIELD_CONTENT).exists(false))
        .with(Sort.by(Sort.Direction.ASC, FIELD_LAST_MODIFIED_DATE));
    List<Document> forms = mongoTemplate.find(query, Document.class, collectionName);
    log.info("Found {} forms of type {}", forms.size(), collectionName);

    int successCount = 0;
    int failureCount = 0;

    for (Document form : forms) {
      String traineeId = form.getString(FIELD_TRAINEE_ID);
      LifecycleState lifecycleState = LifecycleState.valueOf(form.getString(FIELD_LIFECYCLE_STATE));
      String formRef = lifecycleState != DRAFT ? getFormRef(traineeId, formClass) : null;

      // Delete any built submission history so it can be retried without duplication.
      if (formRef != null) {
        deleteSubmissionHistory(formRef, submissionHistoryClass);
      }

      List<Map<String, Object>> history;

      if (lifecycleState == DRAFT) {
        Instant lastModified = getInstantFromLocalDateTime(form.get(FIELD_LAST_MODIFIED_DATE));
        history = List.of(buildStatusInfo(DRAFT.toString(), form, lastModified, 0));
      } else {
        UUID formId = UUID.fromString(form.get(FIELD_ID).toString());
        String formKey = bucketKeyTemplate.formatted(traineeId, formId);
        try {
          history = rebuildHistoryFromS3(formRef, formKey, submissionHistoryClass,
              lifecycleState != DELETED);
        } catch (Exception e) {
          logRecoverableExceptions(e, formId, formKey, lifecycleState);
          failureCount++;
          continue;
        }
      }

      Document migratedForm = transformForm(true, formRef, form, formClass, history);
      mongoTemplate.save(migratedForm, collectionName);
      successCount++;
      logProgress(successCount, failureCount, forms.size());
    }

    log.info("Successfully migrated {} forms of type {}, with {} failures", successCount,
        collectionName, failureCount);
  }

  /**
   * Log the progress every 10 percent.
   *
   * @param successCount The number of successfully migrated forms.
   * @param failureCount The number of failed migrations.
   * @param total        The total number of forms to be migrated.
   */
  private void logProgress(int successCount, int failureCount, int total) {
    int processed = successCount + failureCount;
    int percent = processed * 100 / total;

    if (percent > lastLoggedPercent) {
      log.info("Progress: {}% ({}/{}) (success={} failure={})", percent, processed, total,
          successCount, failureCount);
      lastLoggedPercent = percent;
    }
  }

  /**
   * Rebuild the status history of the form based on the S3 version history. Submitted forms will be
   * snapshotted and saved in the form's submission history.
   *
   * @param formRef                    The form reference for the form.
   * @param formKey                    The S3 key for the form.
   * @param formSubmissionHistoryClass The class of the submission history for the form.
   * @param createSnapshots            Whether to create snapshots of the form for submitted
   *                                   versions.
   * @return A list of statuses representing the form history.
   * @throws IOException        If submitted form content could not be parsed.
   * @throws NoSuchKeyException If the form did not exist in S3.
   */
  private List<Map<String, Object>> rebuildHistoryFromS3(String formRef, String formKey,
      Class<?> formSubmissionHistoryClass, boolean createSnapshots)
      throws IOException, NoSuchKeyException {
    var versionResponse = s3Client.listObjectVersions(req -> req.bucket(bucket)
        .prefix(formKey)
        .build());
    List<ObjectVersion> versions = versionResponse.versions();

    if (versions.isEmpty()) {
      throw NoSuchKeyException.builder().message("No versions found for key: " + formKey).build();
    }

    int revision = 0;
    List<Map<String, Object>> history = new ArrayList<>();
    AtomicReference<String> lastState = new AtomicReference<>();

    List<HeadObjectResponse> versionHeads = versions.stream()
        .map(version -> s3Client.headObject(req -> req
            .bucket(bucket)
            .key(formKey)
            .versionId(version.versionId())
            .build()))
        // Filter out historical versions with no metadata, they are not reliable for rebuild.
        .filter(head -> !head.metadata().isEmpty())
        // Filter versions to remove duplicate states e.g. changes made by migrators.
        .filter(versionHead -> {
          String versionLifecycleState = versionHead.metadata().get(METADATA_LIFECYCLE_STATE);
          boolean isLatestUnique = !Objects.equals(versionLifecycleState, lastState.get());
          lastState.set(versionLifecycleState);
          return isLatestUnique;
        })
        .toList();

    for (var versionHeadIterator = versionHeads.listIterator(versionHeads.size());
        versionHeadIterator.hasPrevious(); ) {
      boolean firstVersion = !versionHeadIterator.hasNext();
      HeadObjectResponse versionHead = versionHeadIterator.previous();
      String versionId = versionHead.versionId();

      String versionLifecycleState = versionHead.metadata().get(METADATA_LIFECYCLE_STATE);
      List<String> allowedFirstStates = List.of(SUBMITTED.toString(), DELETED.toString());

      if (firstVersion && !allowedFirstStates.contains(versionLifecycleState)) {
        // The first S3 version should always be a SUBMITTED or DELETED form.
        throw new IllegalArgumentException(
            "Unexpected lifecycle state %s for first version of form %s".formatted(
                versionLifecycleState, formKey));
      }

      Document versionDocument = getVersionDocument(formKey, versionId);
      Instant submitted = getInstantFromLocalDateTime(versionDocument.get(FIELD_SUBMISSION_DATE));

      if (firstVersion) {
        // Create a dummy DRAFT version based on first available version details.
        history.add(
            buildStatusInfo(DRAFT.toString(), versionDocument, submitted, 0));

        if (versionLifecycleState.equals(DELETED.toString())) {
          // If the first version is DELETED there is no history, create a dummy SUBMITTED version.
          history.add(
              buildStatusInfo(SUBMITTED.toString(), versionDocument, submitted, 0));
        }
      }

      // Revision is incremented on each unsubmission.
      if (versionLifecycleState.equals(UNSUBMITTED.toString())) {
        revision++;
      }

      // If the version is SUBMITTED, use the known submission date instead of modified timestamp.
      Instant versionLastModified = versionLifecycleState.equals(SUBMITTED.toString()) ? submitted
          : versionHead.lastModified();
      history.add(
          buildStatusInfo(versionLifecycleState, versionDocument, versionLastModified, revision));
      fixLatestTimestamp(history);

      if (createSnapshots && versionLifecycleState.equals(SUBMITTED.toString())) {
        Document document = transformForm(false, formRef, versionDocument,
            formSubmissionHistoryClass,
            history);
        String historyCollection = mongoTemplate.getCollectionName(formSubmissionHistoryClass);
        mongoTemplate.save(document, historyCollection);
      }
    }

    return history;
  }

  /**
   * Get a document representing the content of the version, will be dummied for non-submitted.
   *
   * @param formKey   The S3 key for the form.
   * @param versionId The S3 version ID for the form.
   * @return A document representing the form version.
   * @throws IOException If the form content could not be parsed.
   */
  public Document getVersionDocument(String formKey, String versionId) throws IOException {
    // Get content and capture current trainee details.
    GetObjectRequest request = GetObjectRequest.builder()
        .bucket(bucket)
        .key(formKey)
        .versionId(versionId)
        .build();

    try (var content = s3Client.getObject(request)) {
      JsonNode versionContent = new ObjectMapper().registerModule(new JavaTimeModule())
          .readTree(content);
      return Document.parse(versionContent.toString());
    }
  }

  /**
   * Due to missing time information legacy timestamps are set to start of day, if this puts the
   * timestamp before the previous history item then it will instead copy the previous timestamp.
   *
   * @param history The history to fix.
   */
  private void fixLatestTimestamp(List<Map<String, Object>> history) {
    Instant latestTimestamp = (Instant) history.get(history.size() - 1).get(FIELD_TIMESTAMP);
    Instant previousTimestamp = (Instant) history.get(history.size() - 2).get(FIELD_TIMESTAMP);

    if (latestTimestamp.isBefore(previousTimestamp)) {
      history.get(history.size() - 1).put(FIELD_TIMESTAMP, previousTimestamp);
    }
  }

  /**
   * Transform the structure of the form to fit the audited form structure. Any non-recognized
   * fields will be considered content.
   *
   * @param copyFormId Whether to copy the form ID to the new version. Generally true for actual
   *                   forms and false for history snapshots.
   * @param formRef    The form reference for the form.
   * @param form       The form to transform.
   * @param formClass  The class of the form to be transformed.
   * @param history    The pre-built history to inject in to the audit history of the form. The
   *                   current status will be pulled from the latest history item.
   * @return The transformed form as a {@link Document}.
   */
  private Document transformForm(boolean copyFormId, String formRef, Document form,
      Class<?> formClass, List<Map<String, Object>> history) {
    Map<String, Object> content = new HashMap<>(form);

    // Remove non-content fields.
    content.remove(FIELD_CLASS);
    content.remove(FIELD_LAST_MODIFIED_DATE);
    content.remove(FIELD_LIFECYCLE_STATE);
    content.remove(FIELD_SUBMISSION_DATE);

    String idKey = content.containsKey(FIELD_ID) ? FIELD_ID : "id"; // S3 uses "id".
    UUID formId = UUID.fromString(content.remove(idKey).toString());
    String traineeId = (String) content.remove(FIELD_TRAINEE_ID);

    Document migratedForm = new Document();
    migratedForm.put(FIELD_ID, copyFormId ? formId : UUID.randomUUID());
    migratedForm.put(FIELD_TRAINEE_ID, traineeId);

    if (formRef != null) {
      migratedForm.put(FIELD_FORM_REF, formRef);
    }

    Map<String, Object> latestState = history.get(history.size() - 1);
    migratedForm.put(FIELD_REVISION, latestState.get(FIELD_REVISION));
    migratedForm.put(FIELD_CONTENT, content);

    // Build status.
    Map<String, Object> status = new LinkedHashMap<>();
    status.put(FIELD_CURRENT, latestState);

    // Get the latest submitted timestamp.
    history.stream()
        .filter(h -> h.get(FIELD_STATE) == SUBMITTED)
        .map(h -> (Instant) h.get(FIELD_TIMESTAMP))
        .max(Comparator.comparing(Instant::toEpochMilli))
        .ifPresent(date -> status.put(FIELD_SUBMITTED, date));
    status.put(FIELD_HISTORY, history);
    migratedForm.put(FIELD_STATUS, status);

    migratedForm.put(FIELD_CREATED, history.get(0).get(FIELD_TIMESTAMP));
    migratedForm.put(FIELD_LAST_MODIFIED, history.get(history.size() - 1).get(FIELD_TIMESTAMP));
    migratedForm.put(FIELD_CLASS, formClass.getName());

    return migratedForm;
  }

  /**
   * Get an Instant from a LocalDateTime, will handle the type being a {@link Date} from the
   * database or a String from S3.
   *
   * @param dateObj A database or S3 JSON representation of a LocalDateTime.
   * @return The corresponding Instant.
   */
  private Instant getInstantFromLocalDateTime(Object dateObj) {
    if (dateObj instanceof Date date) {
      return date.toInstant();
    }

    try {
      return LocalDateTime.parse(dateObj.toString()).toInstant(UTC);
    } catch (DateTimeParseException e) {
      log.debug("Failed to parse date {} as LocalDateTime, trying as LocalDate", dateObj, e);
      // Use end of day to avoid any ordering issues where unsubmit and resubmit were same day.
      return LocalDate.parse(dateObj.toString()).atStartOfDay().toInstant(UTC);
    }
  }

  /**
   * Get the next for reference for the trainee, based on how many forms already have a generated
   * form reference.
   *
   * @param traineeId The trainee ID.
   * @param formClass The class of the form.
   * @return The generated form reference.
   */
  private String getFormRef(String traineeId, Class<?> formClass) {
    String formCollectionName = mongoTemplate.getCollectionName(formClass);
    long existingRefCount = mongoTemplate.count(
        Query.query(Criteria
            .where(FIELD_TRAINEE_ID).is(traineeId)
            .and(FIELD_FORM_REF).exists(true)
        ), formCollectionName);
    return "formr_part%s_%s_%03d".formatted(
        formClass == FormRPartA.class ? "a" : "b",
        traineeId,
        existingRefCount + 1);
  }

  /**
   * Get the details of the modifying user, may be a trainee or admin depending on the action.
   *
   * @param form           The form content to get details from.
   * @param lifecycleState The end lifecycle state of the modification action.
   * @return A map containing the details of the modifying user.
   */
  private Map<String, String> getModifiedBy(Document form, LifecycleState lifecycleState) {
    String name;
    String email;
    String role;

    if (lifecycleState == DRAFT || lifecycleState == SUBMITTED) {
      // When constructing DRAFT and SUBMITTED from DELETED forms name and email is not available.
      String forename = form.containsKey(FIELD_FORENAME) ? form.getString(FIELD_FORENAME) : "";
      String surname = form.containsKey(FIELD_SURNAME) ? form.getString(FIELD_SURNAME) : "";
      String fullName = (forename + " " + surname).trim();
      name = fullName.isBlank() ? "Name Deleted" : fullName;

      email = form.containsKey(FIELD_EMAIL) ? form.get(FIELD_EMAIL).toString()
          : "no-reply@trainee.tis.nhs.uk";
      role = "TRAINEE";
    } else {
      name = "Unknown Admin";
      email = "no-reply@tis.nhs.uk";
      role = "ADMIN";
    }

    Map<String, String> modifiedBy = new LinkedHashMap<>();
    modifiedBy.put(FIELD_NAME, name);
    modifiedBy.put(FIELD_EMAIL, email);
    modifiedBy.put(FIELD_ROLE, role);
    return modifiedBy;
  }

  /**
   * Build a status info map for the given lifecycle state and form details.
   *
   * @param state        The lifecycle state to build the status info for.
   * @param form         The form document, will be used to retrieve name and email details.
   * @param lastModified The timestamp of the last modification.
   * @param revision     The current revision of the form.
   * @return The built status information.
   */
  private Map<String, Object> buildStatusInfo(String state, Document form, Instant lastModified,
      int revision) {
    LifecycleState lifecycleState = LifecycleState.valueOf(state);

    Map<String, Object> statusInfo = new LinkedHashMap<>();
    statusInfo.put(FIELD_STATE, lifecycleState);
    statusInfo.put(FIELD_MODIFIED_BY, getModifiedBy(form, lifecycleState));
    statusInfo.put(FIELD_TIMESTAMP, lastModified);
    statusInfo.put(FIELD_REVISION, revision);

    return statusInfo;
  }

  /**
   * Log an error for recoverable exceptions, which need to be handled manually but do not affect
   * other records.
   *
   * @param e              The exception to log.
   * @param formId         The ID of the form being migrated.
   * @param formKey        The S3 bucket key for the form being migrated.
   * @param lifecycleState The lifecycle state of the form being migrated.
   * @throws RuntimeException If the exception was not one of the known recoverable types.
   */
  private void logRecoverableExceptions(Exception e, UUID formId, String formKey,
      LifecycleState lifecycleState) throws RuntimeException {
    if (e instanceof NoSuchKeyException) {
      log.error(
          "No S3 object found for {} form {}, skipping migration of this form. Form key: {}",
          lifecycleState, formId, formKey);
    } else if (e instanceof IOException) {
      log.error(
          "Unable to parse S3 content of form {}, skipping migration of this form. Form key: {}",
          formId, formKey);
    } else if (e instanceof IllegalArgumentException) {
      log.error("Skipping migration of form: {}", formKey, e);
    } else {
      log.error("Unrecoverable error occurred while migrating form: {}", formKey);
      throw new RuntimeException(e);
    }
  }

  /**
   * Delete submission history for the given form reference. This is used to clean up any history
   * created for forms that fail to migrate, to avoid orphaned history documents remaining after the
   * migration is complete.
   *
   * @param formRef The form reference to delete for.
   */
  private void deleteSubmissionHistory(String formRef, Class<?> submissionHistoryClass) {
    log.debug("Rolling back form ref {}", formRef);
    String collectionName = mongoTemplate.getCollectionName(submissionHistoryClass);

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
        "Rollback requested but not available for 'ConvertFormrToAudited' migration.");
  }
}
