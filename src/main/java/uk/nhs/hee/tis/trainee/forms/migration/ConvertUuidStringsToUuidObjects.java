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

import com.mongodb.client.result.DeleteResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

/**
 * Convert existing UUID string based form IDs to UUID objects.
 */
@Slf4j
@ChangeUnit(id = "ConvertUuidStringsToUuidObjects", order = "6")
public class ConvertUuidStringsToUuidObjects {

  private static final String ID_FIELD = "_id";
  private final MongoTemplate mongoTemplate;

  /**
   * Convert existing UUID string based form IDs to UUID objects.
   */
  public ConvertUuidStringsToUuidObjects(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Generate a new UUID object for each UUID string form.
   */
  @Execution
  public void migrateCollections() {
    migrateCollection(FormRPartA.class);
    migrateCollection(FormRPartB.class);
  }

  private void migrateCollection(Class<? extends AbstractForm> formClass) {
    String collectionName = mongoTemplate.getCollectionName(formClass);
    log.info("Converting UUID strings to objects for forms in collection {}.", collectionName);

    // Filter to only non UUID objects, so that retries can skip migrated forms on retry.
    List<Document> allDocuments = mongoTemplate.findAll(Document.class, collectionName);
    List<Document> uuidStringDocuments = allDocuments.stream()
        .filter(document -> document.get(ID_FIELD) instanceof String)
        .toList();
    log.info("Found {} form(s) that require UUID conversion.", uuidStringDocuments.size());

    for (Document document : uuidStringDocuments) {
      String originalId = document.getString(ID_FIELD);
      log.info("Converting form {}.", originalId);

      UUID newId = UUID.fromString(originalId);
      document.put(ID_FIELD, newId);

      log.info("Saving updated form {}.", newId);
      mongoTemplate.insert(document, collectionName);
      log.info("Saved updated form {}.", newId);

      deleteOriginalForm(originalId, collectionName);
    }
  }

  /**
   * Delete the original forms from the database.
   *
   * @param originalId     The original ID of the form to delete.
   * @param collectionName The name of the collection to delete from.
   */
  private void deleteOriginalForm(String originalId, String collectionName) {
    log.info("Deleting previous form {} from the database.", originalId);
    Criteria originalFormCriteria = Criteria.where(ID_FIELD).is(originalId);
    Query originalFormQuery = Query.query(originalFormCriteria);
    DeleteResult result = mongoTemplate.remove(originalFormQuery, collectionName);

    if (result.getDeletedCount() != 1) {
      // Log an error, but do not fail the migration so that other forms can still be migrated.
      log.error("Unexpected delete count of {} for form ID {}.", result.getDeletedCount(),
          originalId);
    } else {
      log.info("Deleted previous form {} from the database.", originalId);
    }
  }

  /**
   * Do not attempt rollback, any successfully migrated forms should stay updated.
   */
  @RollbackExecution
  public void rollback() {
    log.warn(
        "Rollback requested but not available for 'ConvertUuidStringsToUuidObjects' migration.");
  }
}
