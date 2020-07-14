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
import org.springframework.util.CollectionUtils;
import uk.nhs.hee.tis.trainee.forms.dto.FormSwitchDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormSwitchMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormSwitch;
import uk.nhs.hee.tis.trainee.forms.repository.FormSwitchRepository;
import uk.nhs.hee.tis.trainee.forms.service.FormSwitchService;

@Slf4j
@Service
@Transactional
public class FormSwitchServiceImpl implements FormSwitchService {

  private final FormSwitchRepository formSwitchRepository;

  private final FormSwitchMapper formSwitchMapper;

  private List<FormSwitchDto> formSwitchesCache;

  public FormSwitchServiceImpl(FormSwitchRepository formSwitchRepository,
      FormSwitchMapper formSwitchMapper) {
    this.formSwitchRepository = formSwitchRepository;
    this.formSwitchMapper = formSwitchMapper;
  }

  @Override
  public List<FormSwitchDto> getFormSwitches() {
    if (CollectionUtils.isEmpty(formSwitchesCache)) {
      List<FormSwitch> formSwitches = formSwitchRepository.findAll();
      formSwitchesCache = formSwitchMapper.toDtos(formSwitches);
    }
    return formSwitchesCache;
  }
}
