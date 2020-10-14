/*
 * The MIT License (MIT)
 *
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
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.exception.ApplicationException;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;

@Slf4j
@Service
@Transactional
public class FormRPartAServiceImpl implements FormRPartAService {

  private final FormRPartAMapper mapper;

  private final FormRPartARepository repository;

  private final ObjectMapper objectMapper;

  private final AmazonS3 amazonS3;

  @Value("${application.file-store.always-store}")
  private boolean alwaysStoreFiles;

  @Value("${application.file-store.bucket}")
  private String bucketName;

  /**
   * Constructor for a FormR PartA service.
   *
   * @param repository   spring data repository
   * @param mapper       maps between the form entity and dto
   * @param objectMapper parses and serializes between json and the object
   * @param amazonS3     object repository cloud storage
   */
  public FormRPartAServiceImpl(FormRPartARepository repository, FormRPartAMapper mapper,
      ObjectMapper objectMapper, AmazonS3 amazonS3) {
    this.repository = repository;
    this.mapper = mapper;
    this.objectMapper = objectMapper;
    this.amazonS3 = amazonS3;
  }

  /**
   * save FormRPartA.
   */
  @Override
  public FormRPartADto save(FormRPartADto formRPartADto) {
    log.info("Request to save FormRPartA : {}", formRPartADto);
    FormRPartA formRPartA = mapper.toEntity(formRPartADto);
    if (alwaysStoreFiles || formRPartA.getLifecycleState() == LifecycleState.SUBMITTED) {
      persistInS3(formRPartA);
      //Save in mongo for backward compatibility
      repository.save(formRPartA);
    } else {
      formRPartA = repository.save(formRPartA);
    }
    return mapper.toDto(formRPartA);
  }

  /**
   * get FormRPartAs by traineeTisId.
   */
  @Override
  public List<FormRPartSimpleDto> getFormRPartAsByTraineeTisId(String traineeTisId) {
    log.info("Request to get FormRPartA list by trainee profileId : {}", traineeTisId);
    List<FormRPartA> formRPartAList = repository.findByTraineeTisId(traineeTisId);
    return mapper.toSimpleDtos(formRPartAList);
  }

  /**
   * get FormRPartA by id.
   */
  @Override
  public FormRPartADto getFormRPartAById(String id, String traineeTisId) {
    log.info("Request to get FormRPartA by id : {}", id);
    FormRPartA formRPartA = repository.findByIdAndTraineeTisId(id, traineeTisId).orElse(null);
    return mapper.toDto(formRPartA);
  }

  private FormRPartA persistInS3(FormRPartA formRPartA) {
    if (StringUtils.isEmpty(formRPartA.getId())) {
      formRPartA.setId(UUID.randomUUID().toString());
    }
    String fileName = formRPartA.getId() + ".json";
    try {
      String key = String.join("/", formRPartA.getTraineeTisId(), "forms", "formr-a", fileName);
      ObjectMetadata metadata = new ObjectMetadata();
      metadata.addUserMetadata("name", fileName);
      metadata.addUserMetadata("type", "json");
      metadata.addUserMetadata("formtype", "formr-a");
      metadata.addUserMetadata("lifecyclestate", formRPartA.getLifecycleState().name());
      metadata.addUserMetadata("submissiondate",
          formRPartA.getSubmissionDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
      metadata.addUserMetadata("traineeid", formRPartA.getTraineeTisId());

      PutObjectRequest request = new PutObjectRequest(bucketName, key,
          new ByteArrayInputStream(objectMapper.writeValueAsBytes(formRPartA)), metadata);
      log.info("uploading file: {} to bucket: {} with key: {}", fileName, bucketName, key);
      amazonS3.putObject(request);
    } catch (Exception e) {
      log.error("Failed to save form for trainee: {} in bucket: {}", formRPartA.getTraineeTisId(),
          bucketName, e);
      throw new ApplicationException("Unable to save file to s3", e);
    }
    return formRPartA;
  }
}
