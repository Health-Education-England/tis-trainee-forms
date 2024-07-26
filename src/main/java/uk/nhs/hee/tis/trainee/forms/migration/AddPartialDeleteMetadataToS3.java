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

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.DeleteType;

/**
 * Add partial delete related metadata to existing forms on S3.
 */
@Slf4j
@ChangeUnit(id = "AddPartialDeleteMetadataToS3", order = "7")
public class AddPartialDeleteMetadataToS3 {

  private final S3Client s3Client;
  private final String bucketName;

  private static final String FIXED_FIELDS =
      "id,traineeTisId,lifecycleState,submissionDate,lastModifiedDate";

  /**
   * Add partial delete related metadata to existing forms on S3.
   */
  public AddPartialDeleteMetadataToS3(S3Client s3Client, Environment env) {
    this.s3Client = s3Client;
    this.bucketName = env.getProperty("application.file-store.bucket");
  }

  /**
   * Find all object on S3 and add metadata.
   */
  @Execution
  public void migrate() throws IOException {
    log.info("Starting migration to add partial delete related metadata to existing forms on S3.");

    ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
        .bucket(bucketName)
        .build();
    ListObjectsV2Iterable objectsIterable = s3Client.listObjectsV2Paginator(listRequest);
    List<S3Object> keyList = objectsIterable.stream()
        .flatMap(res -> res.contents().stream())
        .toList();

    log.info("Updating {} objects in the bucket {}", keyList.size(), bucketName);

    for (S3Object obj : keyList) {
      GetObjectRequest getRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(obj.key())
          .build();
      final ResponseInputStream<GetObjectResponse> object = s3Client.getObject(getRequest);

      Map<String, String> metadata = object.response().metadata();
      if (!(checkExist(metadata, "deletetype", DeleteType.PARTIAL.name())
          && checkExist(metadata, "fixedfields", FIXED_FIELDS))) {
        Map<String, String> newMetadata = new HashMap<>(metadata);
        newMetadata.put("deletetype", DeleteType.PARTIAL.name());
        newMetadata.put("fixedfields", FIXED_FIELDS);

        final PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(obj.key())
            .metadata(newMetadata)
            .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(object.readAllBytes()));
      }
    }
    log.info("Finish migration");
  }

  /**
   * Do not attempt rollback, any successfully added metadata should stay.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'AddPartialDeleteMetadataToS3' migration.");
  }

  private boolean checkExist(Map<String, String> metadata, String metaName, String metaValue) {
    return (metadata.get(metaName) != null && metadata.get(metaName).equals(metaValue));
  }
}
