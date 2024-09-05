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

import static java.util.Map.entry;

import com.amazonaws.AmazonServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.DeleteType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@Slf4j
@Transactional
public abstract class AbstractCloudRepository<T extends AbstractForm> {

  protected final S3Client s3Client;

  protected final ObjectMapper objectMapper;

  private LocalDateTime localDateTime;
  private static final String SUBMISSION_DATE = "submissiondate";
  private static final String FIXED_FIELDS =
      "id,traineeTisId,lifecycleState,submissionDate,lastModifiedDate";

  protected String bucketName;

  protected AbstractCloudRepository(S3Client s3Client, ObjectMapper objectMapper,
      String bucketName) {
    this.s3Client = s3Client;
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
    if (form.getId() == null) {
      form.setId(UUID.randomUUID());
    }
    String fileName = form.getId() + ".json";
    try {
      String key = String.format(getObjectKeyTemplate(), form.getTraineeTisId(), form.getId());

      // Base metadata entries
      Map<String, String> metadata = Map.ofEntries(
          entry("id", form.getId().toString()),
          entry("name", fileName),
          entry("type", "json"),
          entry("formtype", form.getFormType()),
          entry("lifecyclestate", form.getLifecycleState().name()),
          entry(SUBMISSION_DATE, form.getSubmissionDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
          entry("traineeid", form.getTraineeTisId()),
          entry("deletetype", DeleteType.PARTIAL.name()),
          entry("fixedfields", FIXED_FIELDS)
      );

      if (form instanceof FormRPartA || form instanceof FormRPartB) {

        UUID programmeMembershipId = null;
        Boolean isArcp = null;

        if (form instanceof FormRPartA) {
          FormRPartA formRPartA = (FormRPartA) form;
          programmeMembershipId = formRPartA.getProgrammeMembershipId();
          isArcp = formRPartA.getIsArcp();
        } else if (form instanceof FormRPartB) {
          FormRPartB formRPartB = (FormRPartB) form;
          programmeMembershipId = formRPartB.getProgrammeMembershipId();
          isArcp = formRPartB.getIsArcp();
        }

        metadata = Map.ofEntries(
            entry("id", form.getId().toString()),
            entry("name", fileName),
            entry("type", "json"),
            entry("isarcp", isArcp != null ? isArcp.toString() : ""),
            entry("programmemembershipid", programmeMembershipId != null ? programmeMembershipId.toString() : ""),
            entry("formtype", form.getFormType()),
            entry("lifecyclestate", form.getLifecycleState().name()),
            entry(SUBMISSION_DATE, form.getSubmissionDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
            entry("traineeid", form.getTraineeTisId()),
            entry("deletetype", DeleteType.PARTIAL.name()),
            entry("fixedfields", FIXED_FIELDS)
        );
      } else {

      }

      PutObjectRequest request = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(key)
          .metadata(metadata)
          .build();

      log.info("uploading file: {} to bucket: {} with key: {}", fileName, bucketName, key);
      s3Client.putObject(request, RequestBody.fromBytes(objectMapper.writeValueAsBytes(form)));
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
    ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
        .bucket(bucketName)
        .prefix(String.format(getObjectPrefixTemplate(), traineeTisId))
        .build();
    ListObjectsV2Response listing = s3Client.listObjectsV2(listRequest);
    return listing.contents().stream().map(s3Object -> {
      try {
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Object.key())
            .build();
        HeadObjectResponse headObject = s3Client.headObject(headObjectRequest);
        Map<String, String> metadata = headObject.metadata();
        T form = getTypeClass().getConstructor().newInstance();
        form.setId(UUID.fromString(metadata.get("id")));
        form.setTraineeTisId(metadata.get("traineeid"));
        try {
          form.setSubmissionDate(LocalDateTime.parse(metadata.get(SUBMISSION_DATE)));
        } catch (DateTimeParseException e) {
          log.debug("Existing date {} not in latest format, trying as LocalDate.",
              e.getParsedString());
          localDateTime = LocalDate.parse(metadata.get(SUBMISSION_DATE))
              .atStartOfDay();
          form.setSubmissionDate(localDateTime);
        }
        form.setLifecycleState(
            LifecycleState.valueOf(metadata.get("lifecyclestate")
                .toUpperCase()));
        return form;
      } catch (Exception e) {
        log.error("Problem reading form [{}] from S3 bucket [{}]", s3Object.key(), bucketName, e);
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
      GetObjectRequest request = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(String.format(getObjectKeyTemplate(), traineeTisId, id))
          .build();
      ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);

      T form = objectMapper.readValue(response, getTypeClass());
      return Optional.of(form);
    } catch (NoSuchKeyException e) {
      log.debug("Form not found in cloud storage.");
      return Optional.empty();
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

  /**
   * Hard Delete the form.
   *
   * @param id           The id of the form
   * @param traineeTisId the id of the trainee assigned by TIS
   */
  public void delete(String id, String traineeTisId) {
    try {
      DeleteObjectRequest request = DeleteObjectRequest.builder()
          .bucket(bucketName)
          .key(String.format(getObjectKeyTemplate(), traineeTisId, id))
          .build();
      s3Client.deleteObject(request);
    } catch (Exception e) {
      log.error("Unable to delete form {} for trainee {} from Cloud Storage", id, traineeTisId, e);
      throw new ApplicationException("An error occurred deleting form.", e);
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
