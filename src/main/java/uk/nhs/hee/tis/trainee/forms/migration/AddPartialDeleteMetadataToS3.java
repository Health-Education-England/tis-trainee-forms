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
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.DeleteType;

/**
 * Add partial delete related metadata to existing forms on S3.
 */
@Slf4j
@ChangeUnit(id = "AddPartialDeleteMetadataToS3", order = "7")
public class AddPartialDeleteMetadataToS3 {

  private final AmazonS3 amazonS3;
  private final String bucketName;

  private static final String FIXED_FIELDS =
      "id,traineeTisId,lifecycleState,submissionDate,lastModifiedDate";

  /**
   * Add partial delete related metadata to existing forms on S3.
   */
  public AddPartialDeleteMetadataToS3(AmazonS3 amazonS3, Environment env) {
    this.amazonS3 = amazonS3;
    this.bucketName = env.getProperty("application.file-store.bucket");
  }

  /**
   * Find all object on S3 and add metadata.
   */
  @Execution
  public void migrate() {
    log.info("Starting migration to add partial delete related metadata to existing forms on S3.");

    try {
      List<S3ObjectSummary> keyList = new ArrayList<>();
      var objects = amazonS3.listObjects(bucketName);
      keyList.addAll(objects.getObjectSummaries());

      while (objects.isTruncated()) {
        objects = amazonS3.listNextBatchOfObjects(objects);
        keyList.addAll(objects.getObjectSummaries());
      }
      log.info("Updating {} objects in the bucket {}", keyList.size(), bucketName);

      for (var obj : keyList) {
        final var object = amazonS3.getObject(bucketName, obj.getKey());

        var metadata = object.getObjectMetadata();
        metadata.addUserMetadata("deletetype", DeleteType.PARTIAL.name());
        metadata.addUserMetadata("fixedfields", FIXED_FIELDS);

        final var request = new PutObjectRequest(
            bucketName, obj.getKey(), object.getObjectContent(), metadata);
        amazonS3.putObject(request);
      }

      log.info("Finish migration");
    }
    catch (Exception e) {
      log.error("Fail to add metadata to existing forms in bucket " + bucketName + ": ", e);
    }
  }

  /**
   * Do not attempt rollback, any successfully added metadata should stay.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'AddPartialDeleteMetadataToS3' migration.");
  }
}
