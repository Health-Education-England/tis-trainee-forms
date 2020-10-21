/*
 * The MIT License (MIT)
 * Copyright 2020 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.repository;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@Slf4j
@Repository
@Transactional
public class S3ObjectRepositoryImpl {

  private static final String OBJECT_PREFIX_TEMPLATE = "%s/forms/formr-b";
  private static final String OBJECT_KEY_TEMPLATE = "%s/forms/formr-b/%s.json";

  private final AmazonS3 amazonS3;

  private final ObjectMapper objectMapper;

  private String bucketName;

  /**
   * Instantiate an object repository.
   *
   * @param amazonS3     client for S3 service
   * @param objectMapper mapper handles Json (de)serialisation
   * @param bucketName   the bucket that this repository provides persistence with
   */
  public S3ObjectRepositoryImpl(AmazonS3 amazonS3,
      ObjectMapper objectMapper, @Value("${application.file-store.bucket}") String bucketName) {
    this.amazonS3 = amazonS3;
    this.objectMapper = objectMapper;
    this.bucketName = bucketName;
  }

  /**
   * Save a form.
   *
   * @param formRPartB the form to save
   * @return the saved entity
   */
  public FormRPartB save(FormRPartB formRPartB) {
    if (StringUtils.isEmpty(formRPartB.getId())) {
      formRPartB.setId(UUID.randomUUID().toString());
    }
    String fileName = formRPartB.getId() + ".json";
    try {
      String key = String.join("/", formRPartB.getTraineeTisId(), "forms", "formr-b", fileName);
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.addUserMetadata("id", formRPartB.getId());
      metadata.addUserMetadata("name", fileName);
      metadata.addUserMetadata("type", "json");
      metadata.addUserMetadata("formtype", "formr-b");
      metadata.addUserMetadata("lifecyclestate", formRPartB.getLifecycleState().name());
      metadata.addUserMetadata("submissiondate",
          formRPartB.getSubmissionDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
      metadata.addUserMetadata("traineeid", formRPartB.getTraineeTisId());

      PutObjectRequest request = new PutObjectRequest(bucketName, key,
          new ByteArrayInputStream(objectMapper.writeValueAsBytes(formRPartB)), metadata);
      log.info("uploading file: {} to bucket: {} with key: {}", fileName, bucketName, key);
      amazonS3.putObject(request);
    } catch (Exception e) {
      log.error("Failed to save form for trainee: {} in bucket: {}", formRPartB.getTraineeTisId(),
          bucketName, e);
      throw new ApplicationException("Unable to save file to s3", e);
    }
    return formRPartB;
  }

  /**
   * Get the form.
   *
   * @param id           The id of the form
   * @param traineeTisId the id of the trainee assigned by TIS
   * @return the form if it exists and can be read as a valid form, otherwise empty
   */
  public Optional<FormRPartB> findByIdAndTraineeTisId(String id,
      String traineeTisId) {
    try {
      //Not using amazonS3.doesObjectExist() because of the additional calls involved
      S3ObjectInputStream content = amazonS3
          .getObject(bucketName, String.format(OBJECT_KEY_TEMPLATE, traineeTisId, id))
          .getObjectContent();
      FormRPartB form = objectMapper.readValue(content, FormRPartB.class);
      return Optional.of(form);
    } catch (Exception e) {
      log.error("Unable to get file from Cloud Storage", e);
      return Optional.empty();
    }
  }

  /**
   * Find forms for the trainee.
   *
   * @param traineeTisId the id of the trainee assigned by TIS
   * @return a list of forms
   */
  public List<FormRPartB> findByTraineeTisId(String traineeTisId) {
    ObjectListing listing = amazonS3
        .listObjects(bucketName, String.format(OBJECT_PREFIX_TEMPLATE, traineeTisId));
    return listing.getObjectSummaries().stream().map(summary -> {
      try {
        ObjectMetadata metadata = amazonS3.getObjectMetadata(bucketName, summary.getKey());
        FormRPartB form = new FormRPartB();
        form.setId(metadata.getUserMetaDataOf("id"));
        form.setTraineeTisId(metadata.getUserMetaDataOf("traineeid"));
        form.setSubmissionDate(LocalDate.parse(metadata.getUserMetaDataOf("submissiondate")));
        try {
          form.setLifecycleState(
              LifecycleState.valueOf(metadata.getUserMetaDataOf("lifecyclestate").toUpperCase()));
        } catch (NullPointerException e) {
          log.warn("Form in S3 [{}] contains a bad value for lifecyclestate.", summary.getKey(), e);
        }
        return form;
      } catch (Exception e) {
        log.error("Problem reading form [{}] from S3 bucket [{}]", summary.getKey(), bucketName, e);
        return null;
      }
    }).collect(Collectors.toList());
  }
}
