/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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

import com.amazonaws.services.s3.AmazonS3;
import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.schema.JsonSchemaObject.Type;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

/**
 * Convert existing ObjectID based form IDs to UUID strings.
 */
@Slf4j
@ChangeUnit(id = "convertObjectIdsToUuidStrings", order = "5")
public class ConvertObjectIdsToUuidStrings {

  private static final String ID_FIELD = "_id";
  private final MongoTemplate mongoTemplate;
  private final AmazonS3 amazonS3;
  private final String bucketName;
  private final FormRPartAService formAService;
  private final FormRPartAMapper formAMapper;
  private final FormRPartBService formBService;
  private final FormRPartBMapper formBMapper;

  /**
   * Convert existing ObjectID based form IDs to UUID strings.
   */
  public ConvertObjectIdsToUuidStrings(MongoTemplate mongoTemplate, AmazonS3 amazonS3,
      Environment env,
      FormRPartAService formAService, FormRPartAMapper formAMapper,
      FormRPartBService formBService, FormRPartBMapper formBMapper) {
    this.mongoTemplate = mongoTemplate;
    this.amazonS3 = amazonS3;
    this.bucketName = env.getProperty("application.file-store.bucket");
    this.formAService = formAService;
    this.formAMapper = formAMapper;
    this.formBService = formBService;
    this.formBMapper = formBMapper;
  }

  /**
   * Generate a new UUID for each ObjectId form.
   */
  @Execution
  public void migrateCollections() {
    migrateCollection(FormRPartA.class);
    migrateCollection(FormRPartB.class);
  }

  private void migrateCollection(Class<? extends AbstractForm> formClass) {
    String collectionName = mongoTemplate.getCollectionName(formClass);
    log.info("Generating UUID strings for forms in collection {}.", collectionName);

    // Querying for only ObjectId forms, so that retries can skip migrated forms.
    Criteria objectIdCriteria = Criteria.where(ID_FIELD).type(Type.OBJECT_ID);
    Query objectIdQuery = Query.query(objectIdCriteria);
    List<? extends AbstractForm> forms = mongoTemplate.find(objectIdQuery, formClass,
        collectionName);
    log.info("Found {} form(s) that require UUID strings generating.", forms.size());

    for (AbstractForm form : forms) {
      String originalId = form.getId();

      // Generate a UUID string for the form.
      String uuid = UUID.randomUUID().toString();
      form.setId(uuid);
      log.info("UUID {} was generated for form {}.", uuid, originalId);

      // Save the updated form to the database and, if applicable, S3.
      saveNewForm(form);

      // Delete the existing form from the database and, if the ID format changed, S3.
      deleteOriginalForms(originalId, form);
    }
  }

  /**
   * Save the new form to both the database and, if applicable, S3.
   *
   * @param abstractForm The form to save.
   */
  private void saveNewForm(AbstractForm abstractForm) {
    log.info("Saving updated form {}.", abstractForm.getId());

    // Select the correct mapper and service based on the form class.
    if (abstractForm instanceof FormRPartA form) {
      FormRPartADto dto = formAMapper.toDto(form);
      formAService.save(dto);
    } else if (abstractForm instanceof FormRPartB form) {
      FormRPartBDto dto = formBMapper.toDto(form);
      formBService.save(dto);
    }

    log.info("Saved updated form {}.", abstractForm.getId());
  }

  /**
   * Delete the original forms from the database and S3.
   *
   * @param originalId  The original ID of the form to delete.
   * @param updatedForm The updated version of the form (will not be deleted).
   */
  private void deleteOriginalForms(String originalId, AbstractForm updatedForm) {
    log.info("Deleting previous form {} from the database.", originalId);
    Criteria originalFormCriteria = Criteria.where(ID_FIELD).is(originalId);
    Query originalFormQuery = Query.query(originalFormCriteria);
    DeleteResult result = mongoTemplate.remove(originalFormQuery, updatedForm.getClass());

    if (result.getDeletedCount() != 1) {
      // Log an error, but do not fail the migration so that other forms can still be migrated.
      log.error("Unexpected delete count of {} for form ID {}.", result.getDeletedCount(),
          originalId);
    } else {
      log.info("Deleted previous form {} from the database.", originalId);
    }

    log.info("Deleting previous form {} from S3.", originalId);
    String objectKey = String.format("%s/forms/%s/%s.json", updatedForm.getTraineeTisId(),
        updatedForm.getFormType(), originalId);
    try {
      amazonS3.deleteObject(bucketName, objectKey);
      log.info("Deleted previous form {} from S3.", originalId);
    } catch (Exception e) {
      String message = String.format("Failed to delete form ID %s from S3.", originalId);
      log.error(message, e);
    }
  }

  /**
   * Do not attempt rollback, any successfully migrated forms should stay updated.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'convertObjectIdsToUuidStrings' migration.");
  }
}
