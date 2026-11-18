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
 */

package uk.nhs.hee.tis.trainee.forms.migration;

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;

/**
 * Standardise the LTFT start dates of existing LTFT forms and their submission history snapshots,
 * so that the change start date is always 16-week compliant, the exceptional start date holds any
 * earlier date requested by the trainee, and the legacy alternative start date is removed.
 *
 * <p>The start dates are resolved using a fixed copy of the rules as they stood when this migration
 * was written, see {@link #resolve}. Records are read as raw documents, so the migration does not
 * depend on the alternative start date remaining in the entity model.
 *
 * <p>Partial updates are used, which do not modify the last modified timestamp. No events are
 * published, to avoid triggering downstream processing for every existing form.
 */
@Slf4j
@ChangeUnit(id = "migrateLtftStartDates", order = "013")
public class MigrateLtftStartDates {

  /**
   * The minimum notice period for a compliant start date, 16 weeks in days.
   */
  static final int MINIMUM_NOTICE_DAYS = 7 * 16;

  /**
   * The timezone used to calculate the submission date, matching the application timezone.
   */
  static final ZoneId TIMEZONE = ZoneId.of("Europe/London");

  /**
   * The entity types migrated by this migration, each mapping to its own collection.
   */
  static final List<Class<?>> ENTITY_TYPES = List.of(LtftForm.class,
      LtftSubmissionHistory.class);

  private static final String FIELD_ID = "_id";
  private static final String FIELD_CONTENT = "content";
  private static final String FIELD_CHANGE = "change";
  private static final String FIELD_START_DATE = "startDate";
  private static final String FIELD_ALT_START_DATE = "altStartDate";
  private static final String FIELD_EXCEPTIONAL_REASONS = "exceptionalReasons";
  private static final String FIELD_EXCEPTIONAL = "exceptional";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_CURRENT = "current";
  private static final String FIELD_STATE = "state";
  private static final String FIELD_SUBMITTED = "submitted";

  private static final List<String> PATH_CHANGE_START_DATE = List.of(FIELD_CONTENT, FIELD_CHANGE,
      FIELD_START_DATE);
  private static final List<String> PATH_ALT_START_DATE = List.of(FIELD_CONTENT, FIELD_CHANGE,
      FIELD_ALT_START_DATE);
  private static final List<String> PATH_EXCEPTIONAL_START_DATE = List.of(FIELD_CONTENT,
      FIELD_EXCEPTIONAL_REASONS, FIELD_START_DATE);
  private static final List<String> PATH_EXCEPTIONAL = List.of(FIELD_CONTENT,
      FIELD_EXCEPTIONAL_REASONS, FIELD_EXCEPTIONAL);
  private static final List<String> PATH_STATE = List.of(FIELD_STATUS, FIELD_CURRENT,
      FIELD_STATE);
  private static final List<String> PATH_SUBMITTED = List.of(FIELD_STATUS, FIELD_SUBMITTED);

  private final MongoTemplate mongoTemplate;

  /**
   * Constructor for MigrateLtftStartDates.
   */
  public MigrateLtftStartDates(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Migrate the start dates of LTFT forms and submission history snapshots.
   */
  @Execution
  public void migrate() {
    ENTITY_TYPES.forEach(entityType -> {
      String collectionName = mongoTemplate.getCollectionName(entityType);

      List<Document> documents = mongoTemplate.find(new Query(), Document.class, collectionName);
      log.info("Found {} documents of type {} to migrate start dates.", documents.size(),
          collectionName);

      long modifiedCount = 0;
      for (Document document : documents) {
        modifiedCount += migrateDocument(document, collectionName);
      }

      log.info("Migrated start dates for {} of {} documents of type {}.", modifiedCount,
          documents.size(), collectionName);
    });
  }

  /**
   * Migrate the start dates of a single LTFT form or submission history document.
   *
   * @param document       The document to migrate.
   * @param collectionName The collection the document belongs to.
   * @return The number of modified documents.
   * @throws IllegalStateException If no compliant start date can be resolved for the document.
   */
  private long migrateDocument(Document document, String collectionName) {
    Object id = document.get(FIELD_ID);
    String state = document.getEmbedded(PATH_STATE, String.class);

    StartDates startDates;
    try {
      startDates = resolve(
          state == null ? null : LifecycleState.valueOf(state),
          toLocalDate(document.getEmbedded(PATH_CHANGE_START_DATE, Date.class)),
          toLocalDate(document.getEmbedded(PATH_ALT_START_DATE, Date.class)),
          toLocalDate(document.getEmbedded(PATH_EXCEPTIONAL_START_DATE, Date.class)),
          toSubmittedDate(document.getEmbedded(PATH_SUBMITTED, Date.class)));
    } catch (IllegalStateException e) {
      throw new IllegalStateException(
          "Unable to migrate start dates for %s document %s".formatted(collectionName, id), e);
    }

    Update update = new Update();
    setOrUnset(update, String.join(".", PATH_CHANGE_START_DATE),
        startDates.compliantStartDate());
    setOrUnset(update, String.join(".", PATH_EXCEPTIONAL_START_DATE),
        startDates.exceptionalStartDate());

    if (startDates.isExceptional()) {
      update.set(String.join(".", PATH_EXCEPTIONAL), true);
    }

    update.unset(String.join(".", PATH_ALT_START_DATE));

    Query query = Query.query(Criteria.where(FIELD_ID).is(id));
    return mongoTemplate.updateFirst(query, update, collectionName).getModifiedCount();
  }

  /**
   * Set the field to the given value, or unset the field if the value is null.
   *
   * @param update The update to add to.
   * @param field  The field to set or unset.
   * @param value  The value to set, may be null.
   */
  private static void setOrUnset(Update update, String field, LocalDate value) {
    if (value == null) {
      update.unset(field);
    } else {
      update.set(field, value);
    }
  }

  /**
   * Convert a stored {@link LocalDate} field back to a {@link LocalDate}. The stored date is
   * converted using the system default timezone, matching how it was written.
   *
   * @param date The stored date, may be null.
   * @return The converted date, or null if no date was stored.
   */
  static LocalDate toLocalDate(Date date) {
    return date == null ? null : LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault());
  }

  /**
   * Convert a stored submission timestamp to the date of submission in the application timezone.
   *
   * @param submitted The stored submission timestamp, may be null.
   * @return The date of submission, or null if never submitted.
   */
  static LocalDate toSubmittedDate(Date submitted) {
    return submitted == null ? null : LocalDate.ofInstant(submitted.toInstant(), TIMEZONE);
  }

  /**
   * The migrated start dates for a single LTFT record.
   *
   * @param compliantStartDate   The new change start date, 16-week compliant where possible.
   * @param exceptionalStartDate The new exceptional start date, null if not exceptional.
   */
  record StartDates(LocalDate compliantStartDate, LocalDate exceptionalStartDate) {

    /**
     * Whether the record should be flagged as exceptional.
     *
     * @return true if there is an exceptional start date, otherwise false.
     */
    boolean isExceptional() {
      return exceptionalStartDate != null;
    }
  }

  /**
   * Resolve the migrated start dates for a single LTFT form or submission history record.
   *
   * @param state                The current lifecycle state of the record.
   * @param startDate            The existing change start date, may be null.
   * @param altStartDate         The existing alternative start date, may be null.
   * @param exceptionalStartDate The existing exceptional start date, may be null.
   * @param submittedDate        The date the record was submitted (Europe/London), null if never
   *                             submitted.
   * @return The resolved start dates.
   * @throws IllegalStateException If no compliant start date can be resolved.
   */
  static StartDates resolve(LifecycleState state, LocalDate startDate, LocalDate altStartDate,
      LocalDate exceptionalStartDate, LocalDate submittedDate) {
    LocalDate cutOff = submittedDate == null ? null : submittedDate.plusDays(MINIMUM_NOTICE_DAYS);
    boolean isStartCompliant = startDate != null && cutOff != null && !startDate.isBefore(cutOff);
    boolean hasBothDates = startDate != null && altStartDate != null;

    LocalDate compliant = resolveCompliant(state, startDate, altStartDate, isStartCompliant,
        hasBothDates, cutOff);
    LocalDate exceptional = resolveExceptional(startDate, altStartDate, exceptionalStartDate,
        isStartCompliant, hasBothDates);

    return new StartDates(compliant, exceptional);
  }

  /**
   * Resolve the change start date, using the first matching rule.
   */
  private static LocalDate resolveCompliant(LifecycleState state, LocalDate startDate,
      LocalDate altStartDate, boolean isStartCompliant, boolean hasBothDates, LocalDate cutOff) {
    if (state == APPROVED) {
      return startDate;
    }

    if (hasBothDates) {
      return startDate.isAfter(altStartDate) ? startDate : altStartDate;
    }

    if (isStartCompliant) {
      return startDate;
    }

    if (cutOff != null) {
      return cutOff;
    }

    if (state == DRAFT) {
      return null;
    }

    throw new IllegalStateException(
        "Unable to resolve a compliant start date for an LTFT record in state " + state);
  }

  /**
   * Resolve the exceptional start date, using the first matching rule.
   */
  private static LocalDate resolveExceptional(LocalDate startDate, LocalDate altStartDate,
      LocalDate exceptionalStartDate, boolean isStartCompliant, boolean hasBothDates) {
    if (exceptionalStartDate != null) {
      return exceptionalStartDate;
    }

    if (hasBothDates) {
      return startDate.isBefore(altStartDate) ? startDate : altStartDate;
    }

    if (startDate != null && !isStartCompliant) {
      return startDate;
    }

    return null;
  }

  /**
   * Do not attempt rollback, the alternative start date cannot be restored once removed.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'migrateLtftStartDates' migration.");
  }
}
