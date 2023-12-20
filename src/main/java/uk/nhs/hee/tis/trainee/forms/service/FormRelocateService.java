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

package uk.nhs.hee.tis.trainee.forms.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;


@Slf4j
@Service
@Transactional
@XRayEnabled
public class FormRelocateService {

  private final FormRPartARepository formRPartARepository;
  private final FormRPartBRepository formRPartBRepository;
  protected final AmazonS3 amazonS3;

  protected final ObjectMapper objectMapper;

  @Value("${application.file-store.bucket}")
  private String bucketName;

  /**
   * Constructor for Form Relocate service.
   *
   * @param formRPartARepository  spring data repository (Form R Part A)
   * @param formRPartBRepository  spring data repository (Form R Part B)
   * @param amazonS3              client for S3 service
   */
  public FormRelocateService(FormRPartARepository formRPartARepository,
                             FormRPartBRepository formRPartBRepository,
                             AmazonS3 amazonS3,
                             ObjectMapper objectMapper) {
    this.formRPartARepository = formRPartARepository;
    this.formRPartBRepository = formRPartBRepository;
    this.amazonS3 = amazonS3;
    this.objectMapper = objectMapper;
  }

  /**
   * Relocate Form R.
   */
  public void relocateFormR(String formId, String targetTrainee) throws IOException {

    // Get Form R from MongoDB by FormId
    AbstractForm formR = getMoveFormInfoInDb(formId);

    if (formR == null) {
      log.error("Cannot find FormR with ID " + formId + " from DB.");
      throw new ApplicationException("Cannot find FormR with ID " + formId + " from DB.");
    }
    else {
      String formType = formR.getFormType();
      String sourceTrainee = formR.getTraineeTisId();
      try {
        updateTargetTraineeInDb(formR, targetTrainee);
        if (formR.getLifecycleState() != LifecycleState.DRAFT) {
          relocateFormInS3(formType, formId, sourceTrainee, targetTrainee);
        }
      } catch (Exception e) {
        log.error("Fail to relocate FormR to target trainee: " + e + ". Rolling back...");
        try {
          updateTargetTraineeInDb(formR, sourceTrainee);
          if (formR.getLifecycleState() != LifecycleState.DRAFT) {
            relocateFormInS3(formType, formId, targetTrainee, sourceTrainee);
          }
        } catch (Exception ex) {
          log.error("Fail to roll back: " + ex);
        }
        throw new ApplicationException("Fail to relocate FormR to target trainee: " + e.toString());
      }
    }
  }

  private AbstractForm getMoveFormInfoInDb(String formId) {
    try {
      Optional<FormRPartA> optionalFormRPartA =
          formRPartARepository.findById(UUID.fromString(formId));
      Optional<FormRPartB> optionalFormRPartB =
          formRPartBRepository.findById(UUID.fromString(formId));

      if (optionalFormRPartA.isPresent()) {
        return optionalFormRPartA.get();
      }
      else if (optionalFormRPartB.isPresent()) {
        return optionalFormRPartB.get();
      }
      return null;
    } catch (Exception e) {
      log.error("Fail to get FormR with ID " + formId + ": " + e);
      throw new ApplicationException("Fail to get FormR with ID " + formId + ": " + e.toString());
    }
  }

  private void updateTargetTraineeInDb(AbstractForm abstractForm, String targetTrainee) {
    abstractForm.setTraineeTisId(targetTrainee);

    if (abstractForm instanceof FormRPartA formRPartA) {
      formRPartARepository.save(formRPartA);
    } else if (abstractForm instanceof FormRPartB formRPartB) {
      formRPartBRepository.save(formRPartB);
    }
    log.info("Form R with ID " + abstractForm.getId()
        + " moved under " + targetTrainee + " in DB ");
  }

  private void relocateFormInS3(
      String formType, String formId, String sourceTrainee, String targetTrainee)
      throws IOException {

    String sourceKey = sourceTrainee + "/forms/" + formType + "/" + formId + ".json";
    String targetKey = targetTrainee + "/forms/" + formType + "/" + formId + ".json";

    log.info("sourceKey : " + sourceKey);
    log.info("targetKey : " + targetKey);

    // Get Form R from S3
    S3Object object = amazonS3.getObject(bucketName, sourceKey);

    if (object != null) {
      // Set target trainee Id to S3 metadata
      ObjectMetadata metadata = object.getObjectMetadata();
      metadata.addUserMetadata("traineeid", targetTrainee);

      // Set target trainee Id to S3 content
      S3ObjectInputStream content = object.getObjectContent();
      ObjectMapper mapper = new ObjectMapper();
      Map<String, Object> formr = mapper.readValue(content, Map.class);
      formr.put("traineeTisId", targetTrainee);

      // Save the form under target trainee
      final PutObjectRequest request = new PutObjectRequest(
          bucketName,
          targetKey,
          new ByteArrayInputStream(objectMapper.writeValueAsBytes(formr)),
          metadata);
      amazonS3.putObject(request);
      log.info("Form R in S3 relocated from " + sourceKey + " to " + targetKey);

      // Delete Form R from source trainee
      amazonS3.deleteObject(bucketName, sourceKey);
      log.info("Form R in S3 " + sourceKey + " deleted.");
    }
  }
}
