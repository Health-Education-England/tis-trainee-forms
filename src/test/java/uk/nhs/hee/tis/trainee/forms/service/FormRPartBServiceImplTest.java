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

package uk.nhs.hee.tis.trainee.forms.service;

import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.util.Lists;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.impl.FormRPartBServiceImpl;

@ExtendWith(MockitoExtension.class)
public class FormRPartBServiceImplTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";

  private static final String DEFAULT_TYPE_OF_WORK = "DEFAULT_TYPE_OF_WORK";
  private static final LocalDate DEFAULT_WORK_START_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final LocalDate DEFAULT_WORk_END_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final String DEFAULT_WORK_TRAINING_POST = "DEFAULT_WORK_TRAINING_POST";
  private static final String DEFAULT_WORK_SITE = "DEFAULT_WORK_SITE";
  private static final String DEFAULT__WORK_SITE_LOCATION = "DEFAULT__WORK_SITE_LOCATION";
  private static final Integer DEFAULT_TOTAL_LEAVE = 10;

  @InjectMocks
  private FormRPartBServiceImpl formRPartBServiceImpl;

  @Mock
  private FormRPartBMapper formRPartBMapperMock;

  @Mock
  private FormRPartBRepository formRPartBRepositoryMock;

  private FormRPartBDto formRPartBDto;
  private FormRPartB formRPartB;
  private WorkDto workDto;
  private Work work;

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    setupWorkData();

    formRPartBDto = new FormRPartBDto();
    formRPartBDto.setId(DEFAULT_ID);
    formRPartBDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartBDto.setForename(DEFAULT_FORENAME);
    formRPartBDto.setSurname(DEFAULT_SURNAME);
    formRPartBDto.setWork(Lists.newArrayList(workDto));
    formRPartBDto.setTotalLeave(DEFAULT_TOTAL_LEAVE);

    formRPartB = new FormRPartB();
    formRPartB.setId(DEFAULT_ID);
    formRPartB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB.setForename(DEFAULT_FORENAME);
    formRPartB.setSurname(DEFAULT_SURNAME);
    formRPartB.setWork(Lists.newArrayList(work));
    formRPartB.setTotalLeave(DEFAULT_TOTAL_LEAVE);
  }

  /**
   * Set up data for work.
   */
  public void setupWorkData() {
    workDto = new WorkDto();
    workDto.setTypeOfWork(DEFAULT_TYPE_OF_WORK);
    workDto.setStartDate(DEFAULT_WORK_START_DATE);
    workDto.setEndDate(DEFAULT_WORk_END_DATE);
    workDto.setTrainingPost(DEFAULT_WORK_TRAINING_POST);
    workDto.setSite(DEFAULT_WORK_SITE);
    workDto.setSiteLocation(DEFAULT__WORK_SITE_LOCATION);

    work = new Work();
    work.setTypeOfWork(DEFAULT_TYPE_OF_WORK);
    work.setStartDate(DEFAULT_WORK_START_DATE);
    work.setEndDate(DEFAULT_WORk_END_DATE);
    work.setTrainingPost(DEFAULT_WORK_TRAINING_POST);
    work.setSite(DEFAULT_WORK_SITE);
    work.setSiteLocation(DEFAULT__WORK_SITE_LOCATION);
  }

  @Test
  public void shouldSaveFormRPartB() {
    formRPartB.setId(null);
    formRPartBDto.setId(null);

    FormRPartB formRPartBSaved = new FormRPartB();
    formRPartBSaved.setId(DEFAULT_ID);
    formRPartBSaved.setTraineeTisId(formRPartB.getTraineeTisId());
    formRPartBSaved.setForename(formRPartB.getForename());
    formRPartBSaved.setSurname(formRPartB.getSurname());
    formRPartBSaved.setWork(formRPartB.getWork());
    formRPartBSaved.setTotalLeave(formRPartB.getTotalLeave());

    FormRPartBDto formRPartBDtoSaved = new FormRPartBDto();
    formRPartBDtoSaved.setId(DEFAULT_ID);
    formRPartBDtoSaved.setTraineeTisId(formRPartBDto.getTraineeTisId());
    formRPartBDtoSaved.setForename(formRPartBDto.getForename());
    formRPartBDtoSaved.setSurname(formRPartBDto.getSurname());
    formRPartBDtoSaved.setWork(formRPartBDto.getWork());
    formRPartBDtoSaved.setTotalLeave(formRPartB.getTotalLeave());

    when(formRPartBMapperMock.toEntity(formRPartBDto)).thenReturn(formRPartB);
    when(formRPartBMapperMock.toDto(formRPartBSaved)).thenReturn(formRPartBDtoSaved);
    when(formRPartBRepositoryMock.save(formRPartB)).thenReturn(formRPartBSaved);

    FormRPartBDto formRPartBDtoReturn = formRPartBServiceImpl.save(formRPartBDto);

    MatcherAssert.assertThat("The id of returned formRPartB Dto should not be null",
        formRPartBDtoReturn.getId(), CoreMatchers.notNullValue());
  }

  @Test
  public void shouldGetFormRPartBByTraineeTisId() {
    List<FormRPartB> formRPartBList = Arrays.asList(formRPartB);
    when(formRPartBRepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(formRPartBList);
    when(formRPartBMapperMock.toDtos(formRPartBList))
        .thenReturn(Arrays.asList(formRPartBDto));

    List<FormRPartBDto> formRPartBDtoList = formRPartBServiceImpl
        .getFormRPartBsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

    MatcherAssert.assertThat("The size of returned formRPartB list do not match the expected value",
        formRPartBDtoList.size(), CoreMatchers.equalTo(formRPartBList.size()));
    MatcherAssert.assertThat("The returned formRPartB list doesn't not contain the expected item",
        formRPartBDtoList, CoreMatchers.hasItem(formRPartBDto));
  }
}
