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

package uk.nhs.hee.tis.trainee.forms.service;

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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartBRepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@Slf4j
@Service
@Transactional
@XRayEnabled
public class FormRPartBService {

  public static final String FORM_TYPE = "formr-b";

  private static final String ATTRIBUTE_NAME_LIFE_CYCLE_STATE = "lifecycleState";

  private final FormRPartBMapper formRPartBMapper;

  private final FormRPartBRepository formRPartBRepository;

  private final S3FormRPartBRepositoryImpl s3ObjectRepository;

  private final ObjectMapper objectMapper;

  @Value("${application.file-store.always-store}")
  private boolean alwaysStoreFiles;


  /**
   * Constructor for a FormR PartB service.
   *
   * @param formRPartBRepository spring data repository
   * @param s3ObjectRepository   S3 Repository for forms
   * @param formRPartBMapper     maps between the form entity and dto
   */
  public FormRPartBService(FormRPartBRepository formRPartBRepository,
      S3FormRPartBRepositoryImpl s3ObjectRepository,
      FormRPartBMapper formRPartBMapper,
      ObjectMapper objectMapper) {
    this.formRPartBRepository = formRPartBRepository;
    this.formRPartBMapper = formRPartBMapper;
    this.s3ObjectRepository = s3ObjectRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * save FormRPartB.
   */
  public FormRPartBDto save(FormRPartBDto formRPartBDto) {
    log.info("Request to save FormRPartB : {}", formRPartBDto);
    FormRPartB formRPartB = formRPartBMapper.toEntity(formRPartBDto);
    if (alwaysStoreFiles || formRPartB.getLifecycleState() == LifecycleState.SUBMITTED
        || formRPartB.getLifecycleState() == LifecycleState.UNSUBMITTED) {
      s3ObjectRepository.save(formRPartB);
    }

    // Forms stored in cloud are still stored to Mongo for backwards compatibility.
    formRPartB = formRPartBRepository.save(formRPartB);
    return formRPartBMapper.toDto(formRPartB);
  }

  /**
   * get FormRPartBs by traineeTisId.
   */
  public List<FormRPartSimpleDto> getFormRPartBsByTraineeTisId(String traineeTisId) {
    log.info("Request to get FormRPartB list by trainee profileId : {}", traineeTisId);
    List<FormRPartB> storedFormRPartBs = s3ObjectRepository.findByTraineeTisId(traineeTisId);
    List<FormRPartB> formRPartBList = formRPartBRepository
        .findByTraineeTisIdAndLifecycleState(traineeTisId, LifecycleState.DRAFT);
    storedFormRPartBs.addAll(formRPartBList);
    return formRPartBMapper.toSimpleDtos(storedFormRPartBs);
  }

  /**
   * get FormRPartB by id.
   */
  public FormRPartBDto getFormRPartBById(String id, String traineeTisId) {
    log.info("Request to get FormRPartB by id : {}", id);
    FormRPartB formRPartB = s3ObjectRepository.findByIdAndTraineeTisId(id, traineeTisId)
        .or(() -> formRPartBRepository.findByIdAndTraineeTisId(UUID.fromString(id), traineeTisId))
        .orElse(null);
    return formRPartBMapper.toDto(formRPartB);
  }

  /**
   * Partial delete a form by id.
   */
  public FormRPartBDto partialDeleteFormRPartBById(
      String id, String traineeTisId, Set<String> fixedFields) {
    log.info("Request to partial delete FormRPartB by id : {}", id);

    try {
      FormRPartB formRPartB = formRPartBRepository
          .findByIdAndTraineeTisId(UUID.fromString(id), traineeTisId)
          .orElse(null);

      if (formRPartB != null) {
        JsonNode jsonForm = objectMapper.valueToTree(formRPartB);

        for (Iterator<String> fieldIterator = jsonForm.fieldNames(); fieldIterator.hasNext(); ) {
          String fieldName = fieldIterator.next();

          if (!fixedFields.contains(fieldName)) {
            fieldIterator.remove();
          }
        }
        ((ObjectNode) jsonForm).put(ATTRIBUTE_NAME_LIFE_CYCLE_STATE,
            LifecycleState.DELETED.name());
        formRPartB = objectMapper.convertValue(jsonForm, FormRPartB.class);

        formRPartBRepository.save(formRPartB);
        log.info("Partial delete successfully for trainee {} with form Id {} (FormRPartB)",
            traineeTisId, id);
      } else {
        log.error("FormR PartB with ID '{}' not found", id);
      }

      return formRPartBMapper.toDto(formRPartB);

    } catch (Exception e) {
      log.error("Fail to partial delete FormR PartB: {}", e);
      throw new ApplicationException("Fail to partial delete FormR PartB:", e);
    }
  }
}
