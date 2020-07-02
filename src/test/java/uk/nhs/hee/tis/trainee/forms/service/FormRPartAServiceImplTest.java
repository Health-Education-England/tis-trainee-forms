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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.impl.FormRPartAServiceImpl;

@ExtendWith(MockitoExtension.class)
public class FormRPartAServiceImplTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";

  @InjectMocks
  private FormRPartAServiceImpl formRPartAServiceImpl;

  @Mock
  private FormRPartAMapper formRPartAMapperMock;

  @Mock
  private FormRPartARepository formRPartARepositoryMock;

  private FormRPartADto formRPartADto;
  private FormRPartA formRPartA;
  private FormRPartSimpleDto formRPartSimpleDto;

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    formRPartADto = new FormRPartADto();
    formRPartADto.setId(DEFAULT_ID);
    formRPartADto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartADto.setForename(DEFAULT_FORENAME);
    formRPartADto.setSurname(DEFAULT_SURNAME);

    formRPartA = new FormRPartA();
    formRPartA.setId(DEFAULT_ID);
    formRPartA.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA.setForename(DEFAULT_FORENAME);
    formRPartA.setSurname(DEFAULT_SURNAME);

    formRPartSimpleDto = new FormRPartSimpleDto();
    formRPartSimpleDto.setId(DEFAULT_ID);
    formRPartSimpleDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
  }

  @Test
  public void shouldSaveFormRPartA() {
    formRPartA.setId(null);
    formRPartADto.setId(null);

    FormRPartA formRPartASaved = new FormRPartA();
    formRPartASaved.setId(DEFAULT_ID);
    formRPartASaved.setTraineeTisId(formRPartA.getTraineeTisId());
    formRPartASaved.setForename(formRPartA.getForename());
    formRPartASaved.setSurname(formRPartA.getSurname());

    FormRPartADto formRPartADtoSaved = new FormRPartADto();
    formRPartADtoSaved.setId(DEFAULT_ID);
    formRPartADtoSaved.setTraineeTisId(formRPartA.getTraineeTisId());
    formRPartADtoSaved.setForename(formRPartA.getForename());
    formRPartADtoSaved.setSurname(formRPartA.getSurname());

    when(formRPartAMapperMock.toEntity(formRPartADto)).thenReturn(formRPartA);
    when(formRPartAMapperMock.toDto(formRPartASaved)).thenReturn(formRPartADtoSaved);
    when(formRPartARepositoryMock.save(formRPartA)).thenReturn(formRPartASaved);

    FormRPartADto formRPartADtoReturn = formRPartAServiceImpl.save(formRPartADto);

    MatcherAssert.assertThat("The id of returned formRPartA Dto should not be null",
        formRPartADtoReturn.getId(), CoreMatchers.notNullValue());
  }

  @Test
  public void shouldGetFormRPartAsByTraineeTisId() {
    List<FormRPartA> formRPartAList = Arrays.asList(formRPartA);
    when(formRPartARepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(formRPartAList);
    when(formRPartAMapperMock.toSimpleDtos(formRPartAList))
        .thenReturn(Arrays.asList(formRPartSimpleDto));

    List<FormRPartSimpleDto> formRPartASimpleDtoList = formRPartAServiceImpl
        .getFormRPartAsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

    MatcherAssert.assertThat("The size of returned formRPartA list do not match the expected value",
        formRPartASimpleDtoList.size(), CoreMatchers.equalTo(formRPartAList.size()));
    MatcherAssert.assertThat("The returned formRPartA list doesn't not contain the expected item",
        formRPartASimpleDtoList, CoreMatchers.hasItem(formRPartSimpleDto));
  }

  @Test
  public void shouldGetFormRPartAById() {
    when(formRPartARepositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(formRPartA));
    when(formRPartAMapperMock.toDto(formRPartA)).thenReturn(formRPartADto);

    FormRPartADto formRPartADtoReturn = formRPartAServiceImpl.getFormRPartAById(DEFAULT_ID);

    MatcherAssert.assertThat("The returned formRPartA doesn't not match the expected one",
        formRPartADtoReturn, CoreMatchers.is(formRPartADto));
  }
}
