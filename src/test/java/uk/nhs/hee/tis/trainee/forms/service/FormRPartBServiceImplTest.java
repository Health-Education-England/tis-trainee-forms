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
import uk.nhs.hee.tis.trainee.forms.dto.DeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.Declaration;
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
  private static final String DEFAULT_WORK_SITE_LOCATION = "DEFAULT_WORK_SITE_LOCATION";
  private static final Integer DEFAULT_TOTAL_LEAVE = 10;

  private static final Boolean DEFAULT_IS_HONEST = true;
  private static final Boolean DEFAULT_IS_HEALTHY = true;
  private static final String DEFAULT_HEALTHY_STATEMENT = "DEFAULT_HEALTHY_STATEMENT";

  private static final Boolean DEFAULT_HAVE_PREVIOUS_DECLARATIONS = true;
  private static final String DEFAULT_PREVIOUS_DECLARATION_TYPE = "Signification event";
  private static final LocalDate DEFAULT_PREVIOUS_DATE_OF_ENTRY = LocalDate
      .now(ZoneId.systemDefault());
  private static final String DEFAULT_PREVIOUS_DECLARATION_SUMMARY = "DEFAULT_PREVIOUS_DECLARATION_SUMMARY";

  private static final Boolean DEFAULT_HAVE_CURRENT_DECLARATIONS = true;
  private static final String DEFAULT_CURRENT_DECLARATION_TYPE = "Signification event";
  private static final LocalDate DEFAULT_CURRENT_DATE_OF_ENTRY = LocalDate
      .now(ZoneId.systemDefault());
  private static final String DEFAULT_CURRENT_DECLARATION_SUMMARY = "DEFAULT_CURRENT_DECLARATION_SUMMARY";

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
  private DeclarationDto previousDeclarationDto;
  private Declaration previousDeclaration;
  private DeclarationDto currentDeclarationDto;
  private Declaration currentDeclaration;

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    setupWorkData();
    setupPreviousDeclarationData();
    setupCurrentDeclarationData();

    formRPartBDto = new FormRPartBDto();
    formRPartBDto.setId(DEFAULT_ID);
    formRPartBDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartBDto.setForename(DEFAULT_FORENAME);
    formRPartBDto.setSurname(DEFAULT_SURNAME);
    formRPartBDto.setWork(Lists.newArrayList(workDto));
    formRPartBDto.setTotalLeave(DEFAULT_TOTAL_LEAVE);
    formRPartBDto.setIsHonest(DEFAULT_IS_HONEST);
    formRPartBDto.setIsHealthy(DEFAULT_IS_HEALTHY);
    formRPartBDto.setHealthStatement(DEFAULT_HEALTHY_STATEMENT);
    formRPartBDto.setHavePreviousDeclarations(DEFAULT_HAVE_PREVIOUS_DECLARATIONS);
    formRPartBDto.setPreviousDeclarations(Lists.newArrayList(previousDeclarationDto));
    formRPartBDto.setPreviousDeclarationSummary(DEFAULT_PREVIOUS_DECLARATION_SUMMARY);
    formRPartBDto.setHaveCurrentDeclarations(DEFAULT_HAVE_CURRENT_DECLARATIONS);
    formRPartBDto.setCurrentDeclarations(Lists.newArrayList(currentDeclarationDto));
    formRPartBDto.setCurrentDeclarationSummary(DEFAULT_CURRENT_DECLARATION_SUMMARY);

    formRPartB = new FormRPartB();
    formRPartB.setId(DEFAULT_ID);
    formRPartB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB.setForename(DEFAULT_FORENAME);
    formRPartB.setSurname(DEFAULT_SURNAME);
    formRPartB.setWork(Lists.newArrayList(work));
    formRPartB.setTotalLeave(DEFAULT_TOTAL_LEAVE);
    formRPartB.setIsHonest(DEFAULT_IS_HONEST);
    formRPartB.setIsHealthy(DEFAULT_IS_HEALTHY);
    formRPartB.setHealthStatement(DEFAULT_HEALTHY_STATEMENT);
    formRPartB.setHavePreviousDeclarations(DEFAULT_HAVE_PREVIOUS_DECLARATIONS);
    formRPartB.setPreviousDeclarations(Lists.newArrayList(previousDeclaration));
    formRPartB.setPreviousDeclarationSummary(DEFAULT_PREVIOUS_DECLARATION_SUMMARY);
    formRPartB.setHaveCurrentDeclarations(DEFAULT_HAVE_CURRENT_DECLARATIONS);
    formRPartB.setCurrentDeclarations(Lists.newArrayList(currentDeclaration));
    formRPartB.setCurrentDeclarationSummary(DEFAULT_CURRENT_DECLARATION_SUMMARY);
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
    workDto.setSiteLocation(DEFAULT_WORK_SITE_LOCATION);

    work = new Work();
    work.setTypeOfWork(DEFAULT_TYPE_OF_WORK);
    work.setStartDate(DEFAULT_WORK_START_DATE);
    work.setEndDate(DEFAULT_WORk_END_DATE);
    work.setTrainingPost(DEFAULT_WORK_TRAINING_POST);
    work.setSite(DEFAULT_WORK_SITE);
    work.setSiteLocation(DEFAULT_WORK_SITE_LOCATION);
  }

  /**
   * Set up data for previous declaration.
   */
  public void setupPreviousDeclarationData() {
    previousDeclarationDto = new DeclarationDto();
    previousDeclarationDto.setDeclarationType(DEFAULT_PREVIOUS_DECLARATION_TYPE);
    previousDeclarationDto.setDateOfEntry(DEFAULT_PREVIOUS_DATE_OF_ENTRY);

    previousDeclaration = new Declaration();
    previousDeclaration.setDeclarationType(DEFAULT_PREVIOUS_DECLARATION_TYPE);
    previousDeclaration.setDateOfEntry(DEFAULT_PREVIOUS_DATE_OF_ENTRY);
  }

  /**
   * Set up data for current declaration.
   */
  public void setupCurrentDeclarationData() {
    currentDeclarationDto = new DeclarationDto();
    currentDeclarationDto.setDeclarationType(DEFAULT_CURRENT_DECLARATION_TYPE);
    currentDeclarationDto.setDateOfEntry(DEFAULT_CURRENT_DATE_OF_ENTRY);

    currentDeclaration = new Declaration();
    currentDeclaration.setDeclarationType(DEFAULT_CURRENT_DECLARATION_TYPE);
    currentDeclaration.setDateOfEntry(DEFAULT_CURRENT_DATE_OF_ENTRY);
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
    formRPartBSaved.setIsHonest(formRPartB.getIsHonest());
    formRPartBSaved.setIsHealthy(formRPartB.getIsHealthy());
    formRPartBSaved.setHealthStatement(formRPartB.getHealthStatement());
    formRPartBSaved.setHavePreviousDeclarations(formRPartB.getHavePreviousDeclarations());
    formRPartBSaved.setPreviousDeclarations(formRPartB.getPreviousDeclarations());
    formRPartBSaved.setPreviousDeclarationSummary(formRPartB.getPreviousDeclarationSummary());
    formRPartBSaved.setHaveCurrentDeclarations(formRPartB.getHaveCurrentDeclarations());
    formRPartBSaved.setCurrentDeclarations(formRPartB.getCurrentDeclarations());
    formRPartBSaved.setCurrentDeclarationSummary(formRPartB.getCurrentDeclarationSummary());

    FormRPartBDto formRPartBDtoSaved = new FormRPartBDto();
    formRPartBDtoSaved.setId(DEFAULT_ID);
    formRPartBDtoSaved.setTraineeTisId(formRPartBDto.getTraineeTisId());
    formRPartBDtoSaved.setForename(formRPartBDto.getForename());
    formRPartBDtoSaved.setSurname(formRPartBDto.getSurname());
    formRPartBDtoSaved.setWork(formRPartBDto.getWork());
    formRPartBDtoSaved.setTotalLeave(formRPartB.getTotalLeave());
    formRPartBDtoSaved.setIsHonest(formRPartBDto.getIsHonest());
    formRPartBDtoSaved.setIsHealthy(formRPartBDto.getIsHealthy());
    formRPartBDtoSaved.setHealthStatement(formRPartBDto.getHealthStatement());
    formRPartBDtoSaved.setHavePreviousDeclarations(formRPartBDto.getHavePreviousDeclarations());
    formRPartBDtoSaved.setPreviousDeclarations(formRPartBDto.getPreviousDeclarations());
    formRPartBDtoSaved.setPreviousDeclarationSummary(formRPartBDto.getPreviousDeclarationSummary());
    formRPartBDtoSaved.setHaveCurrentDeclarations(formRPartBDto.getHaveCurrentDeclarations());
    formRPartBDtoSaved.setCurrentDeclarations(formRPartBDto.getCurrentDeclarations());
    formRPartBDtoSaved.setCurrentDeclarationSummary(formRPartBDto.getCurrentDeclarationSummary());

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
