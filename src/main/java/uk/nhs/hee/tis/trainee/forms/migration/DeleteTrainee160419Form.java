/*
 * The MIT License (MIT)
 *
 * Copyright 2022 Crown Copyright (Health Education England)
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
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;

/**
 * This form was submitted in error.
 */
@Slf4j
@ChangeUnit(id = "deleteTrainee160419Form", order = "5")
public class DeleteTrainee160419Form {

  private final MongoTemplate mongoTemplate;
  private final AmazonS3 amazonS3;
  private final String bucketName;

  /**
   * Constructs the Mongock change unit.
   *
   * @param mongoTemplate The interface for operations on the DB
   * @param amazonS3      The interface for interacting with S3
   * @param env           The environment for access to a property with the file-store bucket name
   */
  public DeleteTrainee160419Form(MongoTemplate mongoTemplate, AmazonS3 amazonS3, Environment env) {
    this.mongoTemplate = mongoTemplate;
    this.amazonS3 = amazonS3;
    this.bucketName = env.getProperty("application.file-store.bucket");
  }

  /**
   * Run a find and delete for the document's ID.
   */
  @Execution
  public void migrate() {

    final var deletedFormRPartA = mongoTemplate.findAndRemove(
        Query.query(Criteria.where("_id").is("73240025-eaa4-4d17-b6a3-322e67e047b3")),
        FormRPartA.class, "FormRPartA");
    amazonS3
        .deleteObject(bucketName, "160419/forms/formr-a/73240025-eaa4-4d17-b6a3-322e67e047b3.json");
    if (deletedFormRPartA == null) {
      log.info("Changelog did not remove a document");
    } else {
      log.info("Deleted form id [{}] for trainee [{}]", deletedFormRPartA.getId(),
          deletedFormRPartA.getTraineeTisId());
    }
  }

  /**
   * Do not attempt rollback, any successfully deleted record should stay deleted.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'deleteTrainee160419Form' migration.");
  }
}
