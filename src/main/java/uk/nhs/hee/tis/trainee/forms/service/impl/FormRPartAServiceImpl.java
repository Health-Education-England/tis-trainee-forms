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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartASimpleDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;

@Slf4j
@Service
@Transactional
public class FormRPartAServiceImpl implements FormRPartAService {

  private final FormRPartAMapper formRPartAMapper;

  private final FormRPartARepository formRPartARepository;

  public FormRPartAServiceImpl(FormRPartARepository formRPartARepository,
      FormRPartAMapper formRPartAMapper) {
    this.formRPartARepository = formRPartARepository;
    this.formRPartAMapper = formRPartAMapper;
  }

  /**
   * save FormRPartA.
   */
  public FormRPartADto save(FormRPartADto formRPartADto) {
    log.info("Request to save FormRPartA : {}", formRPartADto);
    FormRPartA formRPartA = formRPartAMapper.toEntity(formRPartADto);
    formRPartA = formRPartARepository.save(formRPartA);
    return formRPartAMapper.toDto(formRPartA);
  }

  /**
   * get FormRPartAs by traineeTisId.
   */
  public List<FormRPartASimpleDto> getFormRPartAsByTraineeTisId(String traineeTisId) {
    log.info("Request to get FormRPartA list by trainee profileId : {}", traineeTisId);
    List<FormRPartA> formRPartAList = formRPartARepository.findByTraineeTisId(traineeTisId);
    return formRPartAMapper.toSimpleDtos(formRPartAList);
  }

  /**
   * get FormRPartA by id
   */
  public FormRPartADto getFormRPartAById(String id) {
    log.info("Request to get FormRPartA list by id : {}", id);
    FormRPartA formRPartA = formRPartARepository.findById(id).orElse(null);
    return formRPartAMapper.toDto(formRPartA);
  }
}
