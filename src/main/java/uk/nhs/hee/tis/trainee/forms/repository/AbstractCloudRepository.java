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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@Slf4j
@Transactional
public abstract class AbstractCloudRepository<T extends AbstractForm> {

  protected final AmazonS3 amazonS3;

  protected final ObjectMapper objectMapper;

  private LocalDateTime localDateTime;
  private static final String SUBMISSION_DATE = "submissiondate";

  protected String bucketName;

  protected AbstractCloudRepository(AmazonS3 amazonS3,
      ObjectMapper objectMapper, String bucketName) {
    this.amazonS3 = amazonS3;
    this.objectMapper = objectMapper;
    this.bucketName = bucketName;
  }

  /**
   * Save a form.
   *
   * @param form the form to save
   * @return the saved entity
   */
  public T save(T form) {
    if (!StringUtils.hasText(form.getId())) {
      form.setId(UUID.randomUUID().toString());
    }
    String fileName = form.getId() + ".json";
    try {
      String key = String.format(getObjectKeyTemplate(), form.getTraineeTisId(), form.getId());
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.addUserMetadata("id", form.getId());
      metadata.addUserMetadata("name", fileName);
      metadata.addUserMetadata("type", "json");
      metadata.addUserMetadata("formtype", form.getFormType());
      metadata.addUserMetadata("lifecyclestate", form.getLifecycleState().name());
      metadata.addUserMetadata(SUBMISSION_DATE,
          form.getSubmissionDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
      metadata.addUserMetadata("traineeid", form.getTraineeTisId());

      PutObjectRequest request = new PutObjectRequest(bucketName, key,
          new ByteArrayInputStream(objectMapper.writeValueAsBytes(form)), metadata);
      log.info("uploading file: {} to bucket: {} with key: {}", fileName, bucketName, key);
      amazonS3.putObject(request);
    } catch (Exception e) {
      log.error("Failed to save form for trainee: {} in bucket: {}", form.getTraineeTisId(),
          bucketName, e);
      throw new ApplicationException("Unable to save file to s3", e);
    }
    return form;
  }

  /**
   * Find forms for the trainee.
   *
   * @param traineeTisId the id of the trainee assigned by TIS
   * @return a list of forms
   */
  public List<T> findByTraineeTisId(String traineeTisId) {
    ObjectListing listing = amazonS3
        .listObjects(bucketName, String.format(getObjectPrefixTemplate(), traineeTisId));
    return listing.getObjectSummaries().stream().map(summary -> {
      try {
        ObjectMetadata metadata = amazonS3.getObjectMetadata(bucketName, summary.getKey());
        T form = getTypeClass().getConstructor().newInstance();
        form.setId(metadata.getUserMetaDataOf("id"));
        form.setTraineeTisId(metadata.getUserMetaDataOf("traineeid"));
        try {
          form.setSubmissionDate(LocalDateTime.parse(metadata.getUserMetaDataOf(SUBMISSION_DATE)));
        } catch (DateTimeParseException e) {
          log.debug("Existing date {} not in latest format, trying as LocalDate.",
              e.getParsedString());
          localDateTime = LocalDate.parse(metadata.getUserMetaDataOf(SUBMISSION_DATE))
              .atStartOfDay();
          form.setSubmissionDate(localDateTime);
        }
        form.setLifecycleState(
            LifecycleState.valueOf(metadata.getUserMetaDataOf("lifecyclestate")
                .toUpperCase()));
        return form;
      } catch (Exception e) {
        log.error("Problem reading form [{}] from S3 bucket [{}]", summary.getKey(), bucketName, e);
        return null;
      }
    }).filter(Objects::nonNull).collect(Collectors.toList());
  }

  /**
   * Get the form.
   *
   * @param id           The id of the form
   * @param traineeTisId the id of the trainee assigned by TIS
   * @return the form if it exists and can be read as a valid form, otherwise empty
   */
  public Optional<T> findByIdAndTraineeTisId(String id,
      String traineeTisId) {
    try {
      //Not using amazonS3.doesObjectExist() because of the additional calls involved
      S3ObjectInputStream content = amazonS3
          .getObject(bucketName, String.format(getObjectKeyTemplate(), traineeTisId, id))
          .getObjectContent();

      T form = objectMapper.readValue(content, getTypeClass());
      return Optional.of(form);
    } catch (AmazonServiceException e) {
      log.debug("Unable to get file from Cloud Storage", e);
      if (e.getStatusCode() == 404) {
        return Optional.empty();
      }
      log.error("An error occurred getting form {} for trainee {}.", id, traineeTisId, e);
      throw new ApplicationException("An error occurred getting object from S3.", e);
    } catch (Exception e) {
      log.error("Unable to get form {} for trainee {} from Cloud Storage", id, traineeTisId, e);
      throw new ApplicationException("An error occurred getting form.", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Class<T> getTypeClass() {
    return (Class<T>) ((ParameterizedType) this.getClass()
        .getGenericSuperclass()).getActualTypeArguments()[0];
  }

  protected abstract String getObjectKeyTemplate();

  protected abstract String getObjectPrefixTemplate();

}
