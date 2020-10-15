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

package uk.nhs.hee.tis.trainee.forms.service.impl;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.exception.ApplicationException;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

@Slf4j
@Service
@Transactional
public class FormRPartBServiceImpl implements FormRPartBService {

  private static final String OBJECT_KEY_PREFIX = "%s/forms/formr-b";
  private static final String OBJECT_KEY = "%s/forms/formr-b/%s.json";

  private final FormRPartBMapper formRPartBMapper;

  private final FormRPartBRepository formRPartBRepository;

  private final ObjectMapper objectMapper;

  private final AmazonS3 amazonS3;

  @Value("${application.file-store.always-store}")
  private boolean alwaysStoreFiles;

  @Value("${application.file-store.bucket}")
  private String bucketName;

  /**
   * Constructor for a FormR PartA service.
   *
   * @param formRPartBRepository spring data repository
   * @param formRPartBMapper     maps between the form entity and dto
   * @param objectMapper         parses and serializes between json and the object
   * @param amazonS3             object repository cloud storage
   */
  public FormRPartBServiceImpl(FormRPartBRepository formRPartBRepository,
      FormRPartBMapper formRPartBMapper, ObjectMapper objectMapper,
      AmazonS3 amazonS3) {
    this.formRPartBRepository = formRPartBRepository;
    this.formRPartBMapper = formRPartBMapper;
    this.objectMapper = objectMapper;
    this.amazonS3 = amazonS3;
  }

  /**
   * save FormRPartB.
   */
  @Override
  public FormRPartBDto save(FormRPartBDto formRPartBDto) {
    log.info("Request to save FormRPartB : {}", formRPartBDto);
    FormRPartB formRPartB = formRPartBMapper.toEntity(formRPartBDto);
    if (alwaysStoreFiles || formRPartB.getLifecycleState() == LifecycleState.SUBMITTED) {
      cloudObjectRepository_save(formRPartB);
      //Save in mongo for backward compatibility
      formRPartBRepository.save(formRPartB);
    } else {
      formRPartB = formRPartBRepository.save(formRPartB);
    }
    return formRPartBMapper.toDto(formRPartB);
  }

  /**
   * get FormRPartBs by traineeTisId.
   */
  @Override
  public List<FormRPartSimpleDto> getFormRPartBsByTraineeTisId(String traineeTisId) {
    log.info("Request to get FormRPartB list by trainee profileId : {}", traineeTisId);
    List<FormRPartB> storedFormRPartBs = cloudObjectRepository_findByTraineeTisId(traineeTisId);
    List<FormRPartB> formRPartBList = formRPartBRepository
        .findByTraineeTisIdAndLifecycleState(traineeTisId, LifecycleState.DRAFT);
    storedFormRPartBs.addAll(formRPartBList);
    return formRPartBMapper.toSimpleDtos(storedFormRPartBs);
  }

  /**
   * get FormRPartB by id.
   */
  @Override
  public FormRPartBDto getFormRPartBById(String id, String traineeTisId) {
    log.info("Request to get FormRPartB by id : {}", id);
    FormRPartB formRPartB = cloudObjectRepository_findByIdAndTraineeTisId(id, traineeTisId)
        .or(() -> formRPartBRepository.findByIdAndTraineeTisId(id, traineeTisId))
        .orElse(null);
    return formRPartBMapper.toDto(formRPartB);
  }

  private FormRPartB cloudObjectRepository_save(FormRPartB formRPartB) {
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

  private Optional<FormRPartB> cloudObjectRepository_findByIdAndTraineeTisId(String id,
      String traineeTisId) {
    try {
      //Not using amazonS3.doesObjectExist() because of the additional calls involved
      S3ObjectInputStream content = amazonS3
          .getObject(bucketName, String.format(OBJECT_KEY, traineeTisId, id)).getObjectContent();
      FormRPartB form = objectMapper.readValue(content, FormRPartB.class);
      return Optional.of(form);
    } catch (Exception e) {
      log.error("Unable to get file from Cloud Storage", e);
      return Optional.empty();
    }
  }

  private List<FormRPartB> cloudObjectRepository_findByTraineeTisId(String traineeTisId) {
    ObjectListing listing = amazonS3
        .listObjects(bucketName, String.format(OBJECT_KEY_PREFIX, traineeTisId));
    return listing.getObjectSummaries().stream().map(summary ->
    {
      ObjectMetadata metadata = amazonS3.getObjectMetadata(bucketName, summary.getKey());
      //TODO: Refactor with mapper.toSimpleDto((Map<String,String>)
      FormRPartB form = new FormRPartB();
      form.setId(metadata.getUserMetaDataOf("id"));
      form.setTraineeTisId(metadata.getUserMetaDataOf("traineeid"));
      form.setSubmissionDate(LocalDate.parse(metadata.getUserMetaDataOf("submissiondate")));
      form.setLifecycleState(
          LifecycleState.valueOf(metadata.getUserMetaDataOf("lifecyclestate").toUpperCase()));
      return form;
    }).collect(Collectors.toList());
  }
}
