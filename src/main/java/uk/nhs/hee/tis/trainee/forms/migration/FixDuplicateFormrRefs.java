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

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.AbstractFormR;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartaSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

/**
 * Mongock change unit to recalculate form refs where they are duplicated.
 */
@Slf4j
@ChangeUnit(id = "fixDuplicateFormrRefs", order = "011")
public class FixDuplicateFormrRefs {

  private static final String FIELD_ID = "_id";
  private static final String FIELD_CLASS = "_class";
  private static final String FIELD_TRAINEE_ID = "traineeTisId";
  private static final String FIELD_FORM_REF = "formRef";
  private static final String FIELD_REVISION = "revision";
  private static final String FIELD_CREATED = "created";

  private static final int FORM_REF_SUFFIX_LENGTH = 3;

  private final MongoTemplate mongoTemplate;

  private final FormRPartAService partaService;
  private final FormRPartBService partbService;

  /**
   * Constructor for FixDuplicateFormrRefs.
   */
  public FixDuplicateFormrRefs(MongoTemplate mongoTemplate, FormRPartAService partaService,
      FormRPartBService partbService) {
    this.mongoTemplate = mongoTemplate;
    this.partaService = partaService;
    this.partbService = partbService;
  }

  /**
   * Recalculate form references for duplicated forms.
   */
  @Execution
  public void migrateCollections() {
    int fixCount = migrateCollection(FormRPartA.class, FormrPartaSubmissionHistory.class);
    fixCount += migrateCollection(FormRPartB.class, FormrPartbSubmissionHistory.class);
    log.info("Fixed a total of {} duplicate formRefs across all collections.", fixCount);
  }

  private int migrateCollection(Class<? extends AbstractFormR<?>> formClass,
      Class<? extends FormSubmissionHistory> historyClass) {
    String formCollection = mongoTemplate.getCollectionName(formClass);
    String historyCollection = mongoTemplate.getCollectionName(historyClass);

    List<String> traineeIds = getTraineeIdsWithDuplicates(formCollection);

    log.info("Found {} trainee(s) with duplicate formRefs in collection {}.", traineeIds.size(),
        formCollection);

    int fixCount = traineeIds.stream()
        .mapToInt(
            traineeId -> fixTraineeForms(traineeId, formClass, historyClass, formCollection,
                historyCollection))
        .sum();

    log.info("Fixed {} duplicate formRefs in collection {}.", fixCount, formCollection);
    return fixCount;
  }

  private List<String> getTraineeIdsWithDuplicates(String collectionName) {
    Aggregation aggregation = Aggregation.newAggregation(
        Aggregation.match(
            Criteria.where(FIELD_FORM_REF).exists(true).ne(null)
        ),
        // Count occurrences of each formRef per trainee
        Aggregation.group(FIELD_TRAINEE_ID, FIELD_FORM_REF)
            .count().as("count"),

        // Keep only duplicate formRefs
        Aggregation.match(
            Criteria.where("count").gt(1)
        ),

        // Collapse back down to one document per trainee
        Aggregation.group("_id." + FIELD_TRAINEE_ID),

        // Return just the traineeTisId
        Aggregation.project()
            .and(FIELD_ID).as(FIELD_TRAINEE_ID)
            .andExclude(FIELD_ID)
    );

    return mongoTemplate.aggregate(aggregation, collectionName, Document.class)
        .getMappedResults()
        .stream()
        .map(document -> document.getString(FIELD_TRAINEE_ID))
        .toList();
  }

  private int fixTraineeForms(String traineeId, Class<? extends AbstractFormR<?>> formClass,
      Class<? extends FormSubmissionHistory> historyClass, String formCollection,
      String historyCollection) {
    Query query = Query.query(
            Criteria.where(FIELD_TRAINEE_ID).is(traineeId)
                .and(FIELD_FORM_REF).exists(true).ne(null))
        .with(Sort.by(
            // Latest form first to avoid creating new overlaps when there are multiple per-trainee.
            Sort.Order.desc(FIELD_CREATED),
            Sort.Order.asc(FIELD_ID)
        ));

    List<Document> forms = mongoTemplate.find(query, Document.class, formCollection);
    log.debug("Found {} forms for trainee {} in collection {}.", forms.size(), traineeId,
        formCollection);
    final int formCount = forms.size();
    int fixCount = 0;

    for (int i = 0; i < formCount; i++) {
      Document form = forms.get(i);
      String formRef = form.getString(FIELD_FORM_REF);
      String formRefPrefix = formRef.substring(0, formRef.length() - FORM_REF_SUFFIX_LENGTH);
      int formRefSuffix = Integer.parseInt(
          formRef.substring(formRef.length() - FORM_REF_SUFFIX_LENGTH));
      int expectedSuffix = formCount - i; // Forms are latest first.

      if (formRefSuffix != expectedSuffix) {
        if (i != 0) {
          // Not a truly significant event, but logging gives better visibility for manual checks.
          log.info("Generating a new formRef for non-latest form {}", form.get(FIELD_ID));
        }

        String newFormRef = formRefPrefix + "%03d".formatted(expectedSuffix);
        boolean historyExists = mongoTemplate.exists(
            Query.query(Criteria.where(FIELD_FORM_REF).is(newFormRef)), historyCollection);

        if (historyExists) {
          log.error(
              "Generated formRef {} already has snapshots, unable to update form {}.",
              newFormRef, formRef);
          continue;
        }

        // Should never be null, but default to 0 to avoid NPEs if it is.
        int revision = form.getInteger(FIELD_REVISION, 0);

        if (revision != 0) {
          log.warn(
              "Form {} for trainee {} has revision {}, earlier snapshots will not be generated.",
              formRef, traineeId, revision);
        }

        form.put(FIELD_FORM_REF, newFormRef);
        log.info("Saving form {} with updated form ref {} (previously {})", form.get(FIELD_ID),
            newFormRef, formRef);
        mongoTemplate.save(form, formCollection);

        Document history = new Document(form);
        history.put(FIELD_ID, UUID.randomUUID());
        history.put(FIELD_CLASS, historyClass.getName());
        log.debug("Saving history for new form ref {}", newFormRef);
        mongoTemplate.save(history, historyCollection);

        publishFormUpdate(form, formClass);
        fixCount++;
      }
    }

    return fixCount;
  }

  private void publishFormUpdate(Document form, Class<? extends AbstractFormR<?>> formClass) {
    if (formClass.equals(FormRPartA.class)) {
      log.debug("Publishing FormRPartA update event for new form ref {}",
          form.getString(FIELD_FORM_REF));
      partaService.getAdminsFormRPartAById(form.get(FIELD_ID, UUID.class).toString()).ifPresent(
          partaService::publishFormRUpdateEvent);
    } else {
      log.debug("Publishing FormRPartB update event for new form ref {}",
          form.getString(FIELD_FORM_REF));
      partbService.getAdminsFormRPartBById(form.get(FIELD_ID, UUID.class).toString()).ifPresent(
          partbService::publishFormRUpdateEvent);
    }
  }

  /**
   * Do not attempt rollback, any successfully migrated forms should stay updated.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'FixDuplicateFormrRefs' migration.");
  }
}
