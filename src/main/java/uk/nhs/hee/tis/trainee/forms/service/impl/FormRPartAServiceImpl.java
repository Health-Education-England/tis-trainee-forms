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

import java.util.List;
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

@Slf4j
@Service
@Transactional
public class FormRPartAServiceImpl implements FormRPartAService {

  private final FormRPartAMapper mapper;

  private final FormRPartARepository repository;

  private final S3FormRPartARepositoryImpl cloudObjectRepository;

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
      FormRPartAMapper mapper) {
    this.repository = repository;
    this.cloudObjectRepository = cloudObjectRepository;
    this.mapper = mapper;
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

}
