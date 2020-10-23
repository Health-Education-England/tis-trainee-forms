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

import java.util.List;
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
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

@Slf4j
@Service
@Transactional
public class FormRPartBServiceImpl implements FormRPartBService {

  private final FormRPartBMapper formRPartBMapper;

  private final FormRPartBRepository formRPartBRepository;

  private final S3FormRPartBRepositoryImpl s3ObjectRepository;

  @Value("${application.file-store.always-store}")
  private boolean alwaysStoreFiles;


  /**
   * Constructor for a FormR PartA service.
   *
   * @param formRPartBRepository spring data repository
   * @param s3ObjectRepository   S3 Repository for forms
   * @param formRPartBMapper     maps between the form entity and dto
   */
  public FormRPartBServiceImpl(FormRPartBRepository formRPartBRepository,
      S3FormRPartBRepositoryImpl s3ObjectRepository, FormRPartBMapper formRPartBMapper) {
    this.formRPartBRepository = formRPartBRepository;
    this.formRPartBMapper = formRPartBMapper;
    this.s3ObjectRepository = s3ObjectRepository;
  }

  /**
   * save FormRPartB.
   */
  @Override
  public FormRPartBDto save(FormRPartBDto formRPartBDto) {
    log.info("Request to save FormRPartB : {}", formRPartBDto);
    FormRPartB formRPartB = formRPartBMapper.toEntity(formRPartBDto);
    if (alwaysStoreFiles || formRPartB.getLifecycleState() == LifecycleState.SUBMITTED
        || formRPartB.getLifecycleState() == LifecycleState.UNSUBMITTED) {
      s3ObjectRepository.save(formRPartB);
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
    List<FormRPartB> storedFormRPartBs = s3ObjectRepository.findByTraineeTisId(traineeTisId);
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
    FormRPartB formRPartB = s3ObjectRepository.findByIdAndTraineeTisId(id, traineeTisId)
        .or(() -> formRPartBRepository.findByIdAndTraineeTisId(id, traineeTisId))
        .orElse(null);
    return formRPartBMapper.toDto(formRPartB);
  }
}
