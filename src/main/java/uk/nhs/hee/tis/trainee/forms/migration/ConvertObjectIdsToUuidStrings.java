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

import static org.springframework.data.mongodb.core.schema.JsonSchemaObject.Type.OBJECT_ID;

import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

/**
 * Convert existing ObjectID based form IDs to UUID strings.
 */
@Slf4j
@ChangeUnit(id = "convertObjectIdsToUuidStrings", order = "005")
public class ConvertObjectIdsToUuidStrings {

  private static final String ID_FIELD = "_id";

  private final MongoTemplate mongoTemplate;

  /**
   * Convert existing ObjectID based form IDs to UUID strings.
   */
  public ConvertObjectIdsToUuidStrings(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Generate a new UUID for each ObjectId form.
   */
  @Execution
  public void migrateCollections() {
    migrateCollection(FormRPartA.class);
    migrateCollection(FormRPartB.class);
  }

  /**
   * Migrate a collection of forms from ObjectId to UUID string.
   *
   * @param formClass The class of the forms to migrate.
   */
  private void migrateCollection(Class<? extends AbstractForm> formClass) {
    String collectionName = mongoTemplate.getCollectionName(formClass);
    log.info("Generating UUID strings for forms in collection {}.", collectionName);

    int total = 0;
    int batchSize;

    // Filter to only ObjectId forms, so that retries can skip migrated forms.
    Query query = Query.query(Criteria.where(ID_FIELD).type(OBJECT_ID))
        .with(Sort.by(ID_FIELD))
        .limit(1000);

    do {
      List<Document> forms = mongoTemplate.find(query, Document.class, collectionName);
      batchSize = forms.size();
      total += batchSize;
      log.debug("Found batch of {} form(s) that require UUID strings generating.", batchSize);

      for (Document form : forms) {
        migrateForm(form, collectionName);
      }
    } while (batchSize > 0);

    log.info("Generated UUID strings for {} form(s) in collection {}.", total, collectionName);
  }

  /**
   * Migrate a single form from ObjectId to UUID string.
   *
   * @param form           The form to migrate.
   * @param collectionName The collection the form belongs to.
   */
  private void migrateForm(Document form, String collectionName) {
    ObjectId originalId = form.getObjectId(ID_FIELD);

    // Generate a UUID string for the form.
    String uuid = UUID.randomUUID().toString();
    Document newForm = new Document(form);
    newForm.put(ID_FIELD, uuid);
    log.debug("UUID {} was generated for form {}.", uuid, originalId);

    // Insert the updated form to the database.
    mongoTemplate.insert(newForm, collectionName);

    // Remove the original form.
    DeleteResult result = mongoTemplate.remove(Query.query(Criteria.where(ID_FIELD).is(originalId)),
        collectionName);

    if (result.getDeletedCount() != 1) {
      // Log an error, but do not fail the migration so that other forms can still be migrated.
      log.error("Unexpected delete count of {} for form ID {}.", result.getDeletedCount(),
          originalId);
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
