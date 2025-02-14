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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uk.nhs.hee.tis.trainee.forms.dto.DeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.CovidDeclarationMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.Declaration;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartBRepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@ExtendWith(MockitoExtension.class)
class FormRPartBServiceTest {

  private static final UUID DEFAULT_ID = UUID.randomUUID();
  private static final String DEFAULT_ID_STRING = DEFAULT_ID.toString();
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";

  private static final String DEFAULT_TYPE_OF_WORK = "DEFAULT_TYPE_OF_WORK";
  private static final LocalDate DEFAULT_WORK_START_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final LocalDate DEFAULT_WORk_END_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final String DEFAULT_WORK_TRAINING_POST = "DEFAULT_WORK_TRAINING_POST";
  private static final String DEFAULT_WORK_SITE = "DEFAULT_WORK_SITE";
  private static final String DEFAULT_WORK_SITE_LOCATION = "DEFAULT_WORK_SITE_LOCATION";
  private static final String DEFAULT_WORK_SITE_KNOWN_AS = "DEFAULT_WORK_SITE_KNOWN_AS";
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
  private static final LocalDateTime DEFAULT_SUBMISSION_DATE = LocalDateTime.now();
  private static final UUID DEFAULT_FORM_ID = UUID.randomUUID();

  private static final Boolean DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS = true;
  private static final Boolean DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS = true;
  private static final Set<String> FIXED_FIELDS =
      Set.of("id", "traineeTisId", "lifecycleState");


  private FormRPartBService service;

  @Mock
  private FormRPartBRepository repositoryMock;
  @Mock
  private S3FormRPartBRepositoryImpl s3FormRPartBRepository;

  private ObjectMapper objectMapper;

  private TraineeIdentity traineeIdentity;

  @Captor
  private ArgumentCaptor<PutObjectRequest> putRequestCaptor;
  @Captor
  private ArgumentCaptor<FormRPartB> formRPartBCaptor;

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
    traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(DEFAULT_TRAINEE_TIS_ID);
    mapper = new FormRPartBMapperImpl();
    objectMapper = new ObjectMapper().findAndRegisterModules();
    Field field = ReflectionUtils.findField(FormRPartBMapperImpl.class, "covidDeclarationMapper");
    field.setAccessible(true);
    ReflectionUtils.setField(field, mapper, new CovidDeclarationMapperImpl());

    service = new FormRPartBService(
        repositoryMock, s3FormRPartBRepository, mapper, objectMapper, traineeIdentity);
    initData();
  }

  /**
   * init test data.
   */
  void initData() {
    work = createWork();
    workDto = createWorkDto();
    previousDeclaration = createDeclaration(true);
    previousDeclarationDto = createDeclarationDto(true);
    currentDeclaration = createDeclaration(false);
    currentDeclarationDto = createDeclarationDto(false);

    entity = createEntity();
  }

  /**
   * Set up an FormRPartB.
   *
   * @return form with all default values
   */
  FormRPartB createEntity() {
    FormRPartB entity = new FormRPartB();
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
    entity.setLifecycleState(LifecycleState.DRAFT);
    entity.setHaveCurrentUnresolvedDeclarations(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS);
    entity.setHavePreviousUnresolvedDeclarations(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS);
    return entity;
  }

  /**
   * Set up data for work.
   *
   * @return work with default values
   */
  Work createWork() {
    Work work = new Work();
    work.setTypeOfWork(DEFAULT_TYPE_OF_WORK);
    work.setStartDate(DEFAULT_WORK_START_DATE);
    work.setEndDate(DEFAULT_WORk_END_DATE);
    work.setTrainingPost(DEFAULT_WORK_TRAINING_POST);
    work.setSite(DEFAULT_WORK_SITE);
    work.setSiteLocation(DEFAULT_WORK_SITE_LOCATION);
    work.setSiteKnownAs(DEFAULT_WORK_SITE_KNOWN_AS);
    return work;
  }

  /**
   * Set up data for work.
   *
   * @return work with default values
   */
  WorkDto createWorkDto() {
    WorkDto workDto = new WorkDto();
    workDto.setTypeOfWork(DEFAULT_TYPE_OF_WORK);
    workDto.setStartDate(DEFAULT_WORK_START_DATE);
    workDto.setEndDate(DEFAULT_WORk_END_DATE);
    workDto.setTrainingPost(DEFAULT_WORK_TRAINING_POST);
    workDto.setSite(DEFAULT_WORK_SITE);
    workDto.setSiteLocation(DEFAULT_WORK_SITE_LOCATION);
    workDto.setSiteKnownAs(DEFAULT_WORK_SITE_KNOWN_AS);
    return workDto;
  }

  /**
   * Set up data for previous declaration.
   *
   * @param isPrevious indicates whether to use previous values
   * @return declaration with default values
   */
  Declaration createDeclaration(boolean isPrevious) {
    Declaration declaration = new Declaration();
    if (isPrevious) {
      declaration.setDeclarationType(DEFAULT_PREVIOUS_DECLARATION_TYPE);
      declaration.setDateOfEntry(DEFAULT_PREVIOUS_DATE_OF_ENTRY);
    } else {
      declaration.setDeclarationType(DEFAULT_CURRENT_DECLARATION_TYPE);
      declaration.setDateOfEntry(DEFAULT_CURRENT_DATE_OF_ENTRY);
    }
    return declaration;
  }

  /**
   * Set up data for previous declaration.
   *
   * @param isPrevious indicates whether to use previous values
   * @return declaration with default values
   */
  DeclarationDto createDeclarationDto(boolean isPrevious) {
    DeclarationDto declarationDto = new DeclarationDto();
    if (isPrevious) {
      declarationDto.setDeclarationType(DEFAULT_PREVIOUS_DECLARATION_TYPE);
      declarationDto.setDateOfEntry(DEFAULT_PREVIOUS_DATE_OF_ENTRY);
    } else {
      declarationDto.setDeclarationType(DEFAULT_CURRENT_DECLARATION_TYPE);
      declarationDto.setDateOfEntry(DEFAULT_CURRENT_DATE_OF_ENTRY);
    }
    return declarationDto;
  }

  @ParameterizedTest(name = "Should save to db and cloud when always store files "
      + "and form state is {0}.")
  @EnumSource(value = LifecycleState.class, names = {"DRAFT", "UNSUBMITTED"})
  void shouldSaveFormRPartBToDbAndCloudWhenAlwaysStoreFiles(LifecycleState state) {
    entity.setId(null);
    entity.setLifecycleState(state);
    FormRPartBDto dto = mapper.toDto(entity);

    when(repositoryMock.save(entity)).thenAnswer(invocation -> {
      FormRPartB entity = invocation.getArgument(0);

      FormRPartB savedEntity = new FormRPartB();
      BeanUtils.copyProperties(entity, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    ReflectionTestUtils.setField(service, "alwaysStoreFiles", true);

    FormRPartBDto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
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
    assertThat("Unexpected haveCurrentUnresolvedDeclarations flag.",
        savedDto.getHaveCurrentUnresolvedDeclarations(),
        is(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS));
    assertThat("Unexpected havePreviousUnresolvedDeclarations flag.",
        savedDto.getHavePreviousUnresolvedDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS));

    verify(repositoryMock).save(entity);
    verify(s3FormRPartBRepository).save(entity);
  }

  @ParameterizedTest(name = "Should save to db only when not always store files "
      + "and form state is {0}.")
  @EnumSource(value = LifecycleState.class, names = {"DRAFT", "UNSUBMITTED"})
  void shouldSaveFormRPartBToDbWhenNotAlwaysStoreFiles(LifecycleState state) {
    entity.setId(null);
    entity.setLifecycleState(state);
    FormRPartBDto dto = mapper.toDto(entity);

    when(repositoryMock.save(entity)).thenAnswer(invocation -> {
      FormRPartB entity = invocation.getArgument(0);

      FormRPartB savedEntity = new FormRPartB();
      BeanUtils.copyProperties(entity, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });
    ReflectionTestUtils.setField(service, "alwaysStoreFiles", false);

    FormRPartBDto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
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
    assertThat("Unexpected haveCurrentUnresolvedDeclarations flag.",
        savedDto.getHaveCurrentUnresolvedDeclarations(),
        is(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS));
    assertThat("Unexpected havePreviousUnresolvedDeclarations flag.",
        savedDto.getHavePreviousUnresolvedDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS));

    verify(repositoryMock).save(entity);
    verifyNoInteractions(s3FormRPartBRepository);
  }

  @Test
  void shouldSaveSubmittedFormRPartB() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    FormRPartBDto dto = mapper.toDto(entity);

    when(repositoryMock.save(entity)).thenAnswer(invocation -> {
      FormRPartB entity = invocation.getArgument(0);

      FormRPartB savedEntity = new FormRPartB();
      BeanUtils.copyProperties(entity, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    FormRPartBDto savedDto = service.save(dto);
    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
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
    assertThat("Unexpected haveCurrentUnresolvedDeclarations flag.",
        savedDto.getHaveCurrentUnresolvedDeclarations(),
        is(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS));
    assertThat("Unexpected havePreviousUnresolvedDeclarations flag.",
        savedDto.getHavePreviousUnresolvedDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS));

    verify(repositoryMock).save(entity);
    verify(s3FormRPartBRepository).save(entity);
  }

  @Test
  void shouldThrowExceptionWhenFormRPartBNotSaved() {
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    when(s3FormRPartBRepository.save(any()))
        .thenThrow(new ApplicationException("Expected Exception"));

    FormRPartBDto dto = mapper.toDto(entity);
    assertThrows(ApplicationException.class, () -> service.save(dto));
    verifyNoInteractions(repositoryMock);
  }

  @Test
  void shouldGetFormRPartBsByTraineeTisId() {
    List<FormRPartB> entities = Collections.singletonList(entity);
    when(repositoryMock
        .findByTraineeTisIdAndLifecycleState(DEFAULT_TRAINEE_TIS_ID, LifecycleState.DRAFT))
        .thenReturn(entities);
    when(s3FormRPartBRepository.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(new ArrayList<>());

    List<FormRPartSimpleDto> dtos = service.getFormRPartBs();

    assertThat("Unexpected numbers of forms.", dtos.size(), is(entities.size()));
    FormRPartSimpleDto dto = dtos.get(0);
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
  }

  @Test
  void shouldCombineAllFormRPartBsByTraineeTisId() {
    List<FormRPartB> entities = Collections.singletonList(entity);
    when(repositoryMock
        .findByTraineeTisIdAndLifecycleState(DEFAULT_TRAINEE_TIS_ID, LifecycleState.DRAFT))
        .thenReturn(entities);
    FormRPartB cloudEntity = createEntity();
    cloudEntity.setId(DEFAULT_FORM_ID);
    cloudEntity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    cloudEntity.setLifecycleState(LifecycleState.UNSUBMITTED);
    List<FormRPartB> cloudStoredEntities = new ArrayList<>();
    cloudStoredEntities.add(cloudEntity);
    when(s3FormRPartBRepository.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(cloudStoredEntities);

    List<FormRPartSimpleDto> dtos = service.getFormRPartBs();

    assertThat("Unexpected numbers of forms.", dtos.size(), is(2));

    FormRPartSimpleDto dto = dtos.stream().filter(
        f -> f.getLifecycleState() == LifecycleState.DRAFT).findAny().orElseThrow();
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    dto = dtos.stream().filter(
        f -> f.getLifecycleState() == LifecycleState.UNSUBMITTED).findAny().orElseThrow();
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_FORM_ID.toString()));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected submitted date.", dto.getSubmissionDate(), is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycle state.", dto.getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));
  }

  @Test
  void shouldGetFormRPartBFromCloudStorageByIdWhenOnlyCloudFormExists() {
    entity.setForename("Cloud Only");
    entity.setLifecycleState(LifecycleState.UNSUBMITTED);

    when(s3FormRPartBRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    FormRPartBDto dto = service.getFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Cloud Only"));
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
    assertThat("Unexpected haveCurrentUnresolvedDeclarations flag.",
        dto.getHaveCurrentUnresolvedDeclarations(),
        is(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS));
    assertThat("Unexpected havePreviousUnresolvedDeclarations flag.",
        dto.getHavePreviousUnresolvedDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.UNSUBMITTED));
  }

  @Test
  void shouldGetFormRPartBFromDatabaseByIdWhenOnlyDatabaseFormExists() {
    when(s3FormRPartBRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    entity.setForename("Database Only");
    entity.setLifecycleState(LifecycleState.SUBMITTED);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    FormRPartBDto dto = service.getFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Database Only"));
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
    assertThat("Unexpected haveCurrentUnresolvedDeclarations flag.",
        dto.getHaveCurrentUnresolvedDeclarations(),
        is(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS));
    assertThat("Unexpected havePreviousUnresolvedDeclarations flag.",
        dto.getHavePreviousUnresolvedDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.SUBMITTED));
  }

  @Test
  void shouldGetFormRPartBFromCloudStorageByIdWhenCloudFormIsLatest() {
    FormRPartB cloudForm = new FormRPartB();
    cloudForm.setId(DEFAULT_ID);
    cloudForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    cloudForm.setForename("Cloud Latest");
    cloudForm.setLifecycleState(LifecycleState.UNSUBMITTED);
    cloudForm.setLastModifiedDate(LocalDateTime.MAX);

    when(s3FormRPartBRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(cloudForm));

    FormRPartB dbForm = new FormRPartB();
    dbForm.setId(DEFAULT_ID);
    dbForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dbForm.setForename("Database Oldest");
    dbForm.setLifecycleState(LifecycleState.SUBMITTED);
    dbForm.setLastModifiedDate(LocalDateTime.MIN);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(dbForm));

    FormRPartBDto dto = service.getFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Cloud Latest"));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.UNSUBMITTED));
  }

  @Test
  void shouldGetFormRPartBFromDatabaseByIdWhenDatabaseFormIsLatest() {
    FormRPartB cloudForm = new FormRPartB();
    cloudForm.setId(DEFAULT_ID);
    cloudForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    cloudForm.setForename("Cloud Oldest");
    cloudForm.setLifecycleState(LifecycleState.UNSUBMITTED);
    cloudForm.setLastModifiedDate(LocalDateTime.MIN);

    when(s3FormRPartBRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(cloudForm));

    FormRPartB dbForm = new FormRPartB();
    dbForm.setId(DEFAULT_ID);
    dbForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dbForm.setForename("Database Latest");
    dbForm.setLifecycleState(LifecycleState.SUBMITTED);
    dbForm.setLastModifiedDate(LocalDateTime.MAX);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(dbForm));

    FormRPartBDto dto = service.getFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Database Latest"));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.SUBMITTED));
  }

  @Test
  void shouldGetFormRPartBFromDatabaseByIdWhenCloudAndDatabaseEqualModifiedTime() {
    LocalDateTime now = LocalDateTime.now();

    FormRPartB cloudForm = new FormRPartB();
    cloudForm.setId(DEFAULT_ID);
    cloudForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    cloudForm.setForename("Cloud Equal");
    cloudForm.setLifecycleState(LifecycleState.UNSUBMITTED);
    cloudForm.setLastModifiedDate(now);

    when(s3FormRPartBRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(cloudForm));

    FormRPartB dbForm = new FormRPartB();
    dbForm.setId(DEFAULT_ID);
    dbForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dbForm.setForename("Database Equal");
    dbForm.setLifecycleState(LifecycleState.SUBMITTED);
    dbForm.setLastModifiedDate(now);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(dbForm));

    FormRPartBDto dto = service.getFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Database Equal"));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.SUBMITTED));
  }

  @Test
  void shouldReturnTrueWhenDeletingDraft() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    boolean deleted = service.deleteFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected delete result.", deleted, is(true));
  }

  @Test
  void shouldReturnFalseWhenFormToDeleteNotFound() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    boolean deleted = service.deleteFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected delete result.", deleted, is(false));
  }

  @ParameterizedTest(name = "Should throw exception when deleting form with {0} state")
  @EnumSource(names = {"DRAFT"}, mode = Mode.EXCLUDE)
  void shouldThrowExceptionWhenDeletingNonDraftForm(LifecycleState state) {
    entity.setLifecycleState(state);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    assertThrows(IllegalArgumentException.class,
        () -> service.deleteFormRPartBById(DEFAULT_ID_STRING));
  }

  @Test
  void shouldPartialDeleteFormRPartBById() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    FormRPartBDto resultDto = service.partialDeleteFormRPartBById(
        DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID, FIXED_FIELDS);

    FormRPartB expectedForm = new FormRPartB();
    expectedForm.setId(DEFAULT_ID);
    expectedForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    expectedForm.setLifecycleState(LifecycleState.DELETED);

    verify(repositoryMock).save(any());
    assertThat("Unexpected DTO.", resultDto, is(mapper.toDto(expectedForm)));
  }

  @Test
  void shouldNotPartialDeleteWhenFormRPartBNotFoundInDb() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    service.partialDeleteFormRPartBById(
        DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID, FIXED_FIELDS);

    verify(repositoryMock, never()).save(formRPartBCaptor.capture());
  }

  @Test
  void shouldThrowExceptionWhenFailToPartialDeleteFormRPartB() throws ApplicationException {
    when(repositoryMock.findByIdAndTraineeTisId(any(), any()))
        .thenThrow(IllegalArgumentException.class);
    assertThrows(ApplicationException.class, () -> service.partialDeleteFormRPartBById(
        DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID, FIXED_FIELDS));
  }
}
