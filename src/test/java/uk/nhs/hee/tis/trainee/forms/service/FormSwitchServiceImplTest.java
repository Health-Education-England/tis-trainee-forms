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

import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.hee.tis.trainee.forms.dto.FormSwitchDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormSwitchMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormSwitch;
import uk.nhs.hee.tis.trainee.forms.repository.FormSwitchRepository;
import uk.nhs.hee.tis.trainee.forms.service.impl.FormSwitchServiceImpl;

@ExtendWith(MockitoExtension.class)
public class FormSwitchServiceImplTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_FORM_NAME = "DEFAULT_FORM_NAME";

  @InjectMocks
  private FormSwitchServiceImpl formSwitchServiceImpl;

  @Mock
  private FormSwitchMapper formSwitchMapperMock;

  @Mock
  private FormSwitchRepository formSwitchRepositoryMock;

  private FormSwitchDto formSwitchDto;
  private FormSwitch formSwitch;

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    formSwitchDto = new FormSwitchDto();
    formSwitchDto.setId(DEFAULT_ID);
    formSwitchDto.setName(DEFAULT_FORM_NAME);
    formSwitchDto.setEnabled(true);

    formSwitch = new FormSwitch();
    formSwitch.setId(DEFAULT_ID);
    formSwitch.setName(DEFAULT_FORM_NAME);
    formSwitch.setEnabled(true);
  }

  @Test
  public void shouldGetFormSwitches() {
    List<FormSwitch> formSwitchesList = Collections.singletonList(formSwitch);
    when(formSwitchRepositoryMock.findAll()).thenReturn(formSwitchesList);
    when(formSwitchMapperMock.toDtos(formSwitchesList))
        .thenReturn(Collections.singletonList(formSwitchDto));

    List<FormSwitchDto> formSwitchDtoList = formSwitchServiceImpl.getFormSwitches();
    MatcherAssert.assertThat("The size of returned formSwitch list do not match the expected value",
        formSwitchDtoList.size(), CoreMatchers.equalTo(1));
    MatcherAssert.assertThat("The returned formSwitch list doesn't not contain the expected item",
        formSwitchDtoList, CoreMatchers.hasItem(formSwitchDto));
  }
}
