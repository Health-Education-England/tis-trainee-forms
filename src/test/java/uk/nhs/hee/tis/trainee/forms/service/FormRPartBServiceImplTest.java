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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ReflectionUtils;
import uk.nhs.hee.tis.trainee.forms.dto.DeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.CovidDeclarationMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.Declaration;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.impl.FormRPartBServiceImpl;

@ExtendWith(MockitoExtension.class)
class FormRPartBServiceImplTest {

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
  private static final String DEFAULT_PREVIOUS_DECLARATION_SUMMARY =
      "DEFAULT_PREVIOUS_DECLARATION_SUMMARY";

  private static final Boolean DEFAULT_HAVE_CURRENT_DECLARATIONS = true;
  private static final String DEFAULT_CURRENT_DECLARATION_TYPE = "Signification event";
  private static final LocalDate DEFAULT_CURRENT_DATE_OF_ENTRY = LocalDate
      .now(ZoneId.systemDefault());
  private static final String DEFAULT_CURRENT_DECLARATION_SUMMARY =
      "DEFAULT_CURRENT_DECLARATION_SUMMARY";
  private static final LocalDate DEFAULT_SUBMISSION_DATE = LocalDate.of(2020, 8, 29);

  private FormRPartBServiceImpl service;

  @Mock
  private FormRPartBRepository repositoryMock;

  @Mock
  private AmazonS3 s3Mock;

  private FormRPartBMapper mapper;

  private FormRPartB entity;
  private WorkDto workDto;
  private Work work;
  private DeclarationDto previousDeclarationDto;
  private Declaration previousDeclaration;
  private DeclarationDto currentDeclarationDto;
  private Declaration currentDeclaration;

  @BeforeEach
  void setUp() {
    mapper = new FormRPartBMapperImpl();
    Field field = ReflectionUtils.findField(FormRPartBMapperImpl.class, "covidDeclarationMapper");
    field.setAccessible(true);
    ReflectionUtils.setField(field, mapper, new CovidDeclarationMapperImpl());

    service = new FormRPartBServiceImpl(repositoryMock, mapper, new ObjectMapper(), s3Mock);
    initData();
  }

  /**
   * init test data.
   */
  void initData() {
    setupWorkData();
    setupPreviousDeclarationData();
    setupCurrentDeclarationData();

    entity = new FormRPartB();
    entity.setId(DEFAULT_ID);
    entity.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    entity.setForename(DEFAULT_FORENAME);
    entity.setSurname(DEFAULT_SURNAME);
    entity.setWork(Collections.singletonList(work));
    entity.setTotalLeave(DEFAULT_TOTAL_LEAVE);
    entity.setIsHonest(DEFAULT_IS_HONEST);
    entity.setIsHealthy(DEFAULT_IS_HEALTHY);
    entity.setHealthStatement(DEFAULT_HEALTHY_STATEMENT);
    entity.setHavePreviousDeclarations(DEFAULT_HAVE_PREVIOUS_DECLARATIONS);
    entity.setPreviousDeclarations(Collections.singletonList(previousDeclaration));
    entity.setPreviousDeclarationSummary(DEFAULT_PREVIOUS_DECLARATION_SUMMARY);
    entity.setHaveCurrentDeclarations(DEFAULT_HAVE_CURRENT_DECLARATIONS);
    entity.setCurrentDeclarations(Collections.singletonList(currentDeclaration));
    entity.setCurrentDeclarationSummary(DEFAULT_CURRENT_DECLARATION_SUMMARY);
  }

  /**
   * Set up data for work.
   */
  void setupWorkData() {
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
  void setupPreviousDeclarationData() {
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
  void setupCurrentDeclarationData() {
    currentDeclarationDto = new DeclarationDto();
    currentDeclarationDto.setDeclarationType(DEFAULT_CURRENT_DECLARATION_TYPE);
    currentDeclarationDto.setDateOfEntry(DEFAULT_CURRENT_DATE_OF_ENTRY);

    currentDeclaration = new Declaration();
    currentDeclaration.setDeclarationType(DEFAULT_CURRENT_DECLARATION_TYPE);
    currentDeclaration.setDateOfEntry(DEFAULT_CURRENT_DATE_OF_ENTRY);
  }

  @Test
  void shouldSaveUnsubmittedFormRPartB() {
    entity.setId(null);
    FormRPartBDto dto = mapper.toDto(entity);

    when(repositoryMock.save(entity)).thenAnswer(invocation -> {
      FormRPartB entity = invocation.getArgument(0);
      entity.setId(DEFAULT_ID);
      return entity;
    });

    FormRPartBDto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected work.", savedDto.getWork(), is(Collections.singletonList(workDto)));
    assertThat("Unexpected total leave.", savedDto.getTotalLeave(), is(DEFAULT_TOTAL_LEAVE));
    assertThat("Unexpected isHonest flag.", savedDto.getIsHonest(), is(DEFAULT_IS_HONEST));
    assertThat("Unexpected isHealthy flag.", savedDto.getIsHealthy(), is(DEFAULT_IS_HEALTHY));
    assertThat("Unexpected health statement.", savedDto.getHealthStatement(),
        is(DEFAULT_HEALTHY_STATEMENT));
    assertThat("Unexpected havePreviousDeclarations flag.", savedDto.getHavePreviousDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_DECLARATIONS));
    assertThat("Unexpected previous declarations.", savedDto.getPreviousDeclarations(),
        is(Collections.singletonList(previousDeclarationDto)));
    assertThat("Unexpected previous declaration summary.", savedDto.getPreviousDeclarationSummary(),
        is(DEFAULT_PREVIOUS_DECLARATION_SUMMARY));
    assertThat("Unexpected haveCurrentDeclarations flag.", savedDto.getHaveCurrentDeclarations(),
        is(DEFAULT_HAVE_CURRENT_DECLARATIONS));
    assertThat("Unexpected current declarations.", savedDto.getCurrentDeclarations(),
        is(Collections.singletonList(currentDeclarationDto)));
    assertThat("Unexpected current declaration summary.", savedDto.getCurrentDeclarationSummary(),
        is(DEFAULT_CURRENT_DECLARATION_SUMMARY));
  }

  @Test
  void shouldSaveSubmittedFormRPartB() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    FormRPartBDto dto = mapper.toDto(entity);

    FormRPartBDto savedDto = service.save(dto);
    assertThat("Unexpected form ID.", savedDto.getId(), notNullValue());
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected work.", savedDto.getWork(), is(Collections.singletonList(workDto)));
    assertThat("Unexpected total leave.", savedDto.getTotalLeave(), is(DEFAULT_TOTAL_LEAVE));
    assertThat("Unexpected isHonest flag.", savedDto.getIsHonest(), is(DEFAULT_IS_HONEST));
    assertThat("Unexpected isHealthy flag.", savedDto.getIsHealthy(), is(DEFAULT_IS_HEALTHY));
    assertThat("Unexpected health statement.", savedDto.getHealthStatement(),
        is(DEFAULT_HEALTHY_STATEMENT));
    assertThat("Unexpected havePreviousDeclarations flag.", savedDto.getHavePreviousDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_DECLARATIONS));
    assertThat("Unexpected previous declarations.", savedDto.getPreviousDeclarations(),
        is(Collections.singletonList(previousDeclarationDto)));
    assertThat("Unexpected previous declaration summary.", savedDto.getPreviousDeclarationSummary(),
        is(DEFAULT_PREVIOUS_DECLARATION_SUMMARY));
    assertThat("Unexpected haveCurrentDeclarations flag.", savedDto.getHaveCurrentDeclarations(),
        is(DEFAULT_HAVE_CURRENT_DECLARATIONS));
    assertThat("Unexpected current declarations.", savedDto.getCurrentDeclarations(),
        is(Collections.singletonList(currentDeclarationDto)));
    assertThat("Unexpected current declaration summary.", savedDto.getCurrentDeclarationSummary(),
        is(DEFAULT_CURRENT_DECLARATION_SUMMARY));
    verify(s3Mock).putObject(any(PutObjectRequest.class));
    entity.setId(savedDto.getId());
    verify(repositoryMock).save(entity);
  }

  @Test
  void shouldThrowExceptionWhenFormRPartBNotSaved() {
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    when(s3Mock.putObject(any())).thenThrow(new RuntimeException("Expected Exception"));

    assertThrows(RuntimeException.class, () -> service.save(mapper.toDto(entity)));
    verifyNoInteractions(repositoryMock);
  }

  @Test
  void shouldGetFormRPartBsByTraineeTisId() {
    List<FormRPartB> entities = Collections.singletonList(entity);
    when(repositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(entities);

    List<FormRPartSimpleDto> dtos = service.getFormRPartBsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected numbers of forms.", dtos.size(), is(entities.size()));

    FormRPartSimpleDto dto = dtos.get(0);
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
  }

  @Test
  void shouldGetFormRPartBById() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    FormRPartBDto dto = service.getFormRPartBById(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", dto.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected work.", dto.getWork(), is(Collections.singletonList(workDto)));
    assertThat("Unexpected total leave.", dto.getTotalLeave(), is(DEFAULT_TOTAL_LEAVE));
    assertThat("Unexpected isHonest flag.", dto.getIsHonest(), is(DEFAULT_IS_HONEST));
    assertThat("Unexpected isHealthy flag.", dto.getIsHealthy(), is(DEFAULT_IS_HEALTHY));
    assertThat("Unexpected health statement.", dto.getHealthStatement(),
        is(DEFAULT_HEALTHY_STATEMENT));
    assertThat("Unexpected havePreviousDeclarations flag.", dto.getHavePreviousDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_DECLARATIONS));
    assertThat("Unexpected previous declarations.", dto.getPreviousDeclarations(),
        is(Collections.singletonList(previousDeclarationDto)));
    assertThat("Unexpected previous declaration summary.", dto.getPreviousDeclarationSummary(),
        is(DEFAULT_PREVIOUS_DECLARATION_SUMMARY));
    assertThat("Unexpected haveCurrentDeclarations flag.", dto.getHaveCurrentDeclarations(),
        is(DEFAULT_HAVE_CURRENT_DECLARATIONS));
    assertThat("Unexpected current declarations.", dto.getCurrentDeclarations(),
        is(Collections.singletonList(currentDeclarationDto)));
    assertThat("Unexpected current declaration summary.", dto.getCurrentDeclarationSummary(),
        is(DEFAULT_CURRENT_DECLARATION_SUMMARY));
  }
}
