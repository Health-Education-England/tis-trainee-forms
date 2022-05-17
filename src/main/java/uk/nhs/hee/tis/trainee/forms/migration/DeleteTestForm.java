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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

/**
 * This form was submitted to validate that there was no problem with the user's account/data.
 */
@Slf4j
@ChangeUnit(id = "deleteTestForm", order = "3")
public class DeleteTestForm {

  private final MongoTemplate mongoTemplate;
  private final AmazonS3 amazonS3;
  private final String bucketName;

  public DeleteTestForm(MongoTemplate mongoTemplate, AmazonS3 amazonS3,
      @Value("${application.file-store.bucket}") String bucketName) {
    this.mongoTemplate = mongoTemplate;
    this.amazonS3 = amazonS3;
    this.bucketName = bucketName;
  }

  /**
   * Run a find and delete for the document's ID.
   */
  @Execution
  public void migrate() {

    final var deletedFormRPartB = mongoTemplate.findAndRemove(
        Query.query(Criteria.where("_id").is("f874c846-623d-478c-8937-7595afbc969a")),
        FormRPartB.class, "FormRPartB");
    amazonS3
        .deleteObject(bucketName, "256060/forms/formr-b/f874c846-623d-478c-8937-7595afbc969a.json");
    if (deletedFormRPartB == null) {
      log.info("Changelog did not remove a document");
    } else {
      log.info("Deleted form id [{}] for trainee [{}]", deletedFormRPartB.getId(),
          deletedFormRPartB.getTraineeTisId());
    }
  }

  /**
   * Do not attempt rollback, any successfully deleted record should stay deleted.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'deleteTestForm' migration.");
  }
}
