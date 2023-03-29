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

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartARepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@Slf4j
@Service
@XRayEnabled
@Transactional
public class FormRPartAServiceImpl implements FormRPartAService {

  private static final String ATTRIBUTE_NAME_LIFE_CYCLE_STATE = "lifecycleState";

  private final FormRPartAMapper mapper;

  private final FormRPartARepository repository;

  private final S3FormRPartARepositoryImpl cloudObjectRepository;

  private final ObjectMapper objectMapper;

  @Value("${application.file-store.always-store}")
  private boolean alwaysStoreFiles;

  @Value("${application.file-store.bucket}")
  private String bucketName;

  /**
   * Constructor for a FormR PartA service.
   *
   * @param repository            spring data repository
   * @param cloudObjectRepository repository to storage form in the cloud
   * @param mapper                maps between the form entity and dto
   */
  public FormRPartAServiceImpl(FormRPartARepository repository,
      S3FormRPartARepositoryImpl cloudObjectRepository,
      FormRPartAMapper mapper,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.cloudObjectRepository = cloudObjectRepository;
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  /**
   * save FormRPartA.
   */
  @Override
  public FormRPartADto save(FormRPartADto formRPartADto) {
    log.info("Request to save FormRPartA : {}", formRPartADto);
    FormRPartA formRPartA = mapper.toEntity(formRPartADto);
    if (alwaysStoreFiles || formRPartA.getLifecycleState() == LifecycleState.SUBMITTED
        || formRPartA.getLifecycleState() == LifecycleState.UNSUBMITTED) {
      cloudObjectRepository.save(formRPartA);
    }

    // Forms stored in cloud are still stored to Mongo for backwards compatibility.
    formRPartA = repository.save(formRPartA);
    return mapper.toDto(formRPartA);
  }

  /**
   * get FormRPartAs by traineeTisId.
   */
  @Override
  public List<FormRPartSimpleDto> getFormRPartAsByTraineeTisId(String traineeTisId) {
    log.info("Request to get FormRPartA list by trainee profileId : {}", traineeTisId);
    List<FormRPartA> storedFormRPartAs = cloudObjectRepository.findByTraineeTisId(traineeTisId);
    List<FormRPartA> formRPartAList = repository
        .findByTraineeTisIdAndLifecycleState(traineeTisId, LifecycleState.DRAFT);
    storedFormRPartAs.addAll(formRPartAList);
    return mapper.toSimpleDtos(storedFormRPartAs);
  }

  /**
   * get FormRPartA by id.
   */
  @Override
  public FormRPartADto getFormRPartAById(String id, String traineeTisId) {
    log.info("Request to get FormRPartA by id : {}", id);
    FormRPartA formRPartA = cloudObjectRepository.findByIdAndTraineeTisId(id, traineeTisId)
        .or(() -> repository.findByIdAndTraineeTisId(UUID.fromString(id), traineeTisId))
        .orElse(null);
    return mapper.toDto(formRPartA);
  }

  /**
   * Partial delete a form by id.
   */
  @Override
  public FormRPartADto partialDeleteFormRPartAById(
      String id, String traineeTisId, Set<String> fixedFields) {
    log.info("Request to partial delete FormRPartA by id : {}", id);

    try {
      FormRPartA formRPartA = repository.findByIdAndTraineeTisId(UUID.fromString(id), traineeTisId)
          .orElse(null);

      if (formRPartA != null) {
        JsonNode jsonForm = objectMapper.valueToTree(formRPartA);

        for (Iterator<String> fieldIterator = jsonForm.fieldNames(); fieldIterator.hasNext(); ) {
          String fieldName = fieldIterator.next();

          if (!fixedFields.contains(fieldName)) {
            fieldIterator.remove();
          }
        }
        ((ObjectNode) jsonForm).put(ATTRIBUTE_NAME_LIFE_CYCLE_STATE,
            LifecycleState.DELETED.name());
        formRPartA = objectMapper.convertValue(jsonForm, FormRPartA.class);

        repository.save(formRPartA);
        log.info("Partial delete successfully for trainee {} with form Id {} (FormRPartA)",
            traineeTisId, id);
      } else {
        log.error("FormR PartB with ID '{}' not found", id);
      }

      return mapper.toDto(formRPartA);

    } catch (Exception e) {
      log.error("Fail to partial delete FormR PartA: {}", e);
      throw new ApplicationException("Fail to partial delete FormR PartA:", e);
    }
  }
}
