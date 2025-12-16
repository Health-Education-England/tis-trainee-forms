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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartARepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@ExtendWith(MockitoExtension.class)
class FormRPartAServiceTest {

  private static final UUID DEFAULT_ID = UUID.randomUUID();
  private static final String DEFAULT_ID_STRING = DEFAULT_ID.toString();
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final LocalDateTime DEFAULT_SUBMISSION_DATE = LocalDateTime.now();
  private static final String FORM_R_PART_A_SUBMITTED_TOPIC = "arn:aws:sns:topic";

  private FormRPartAService service;

  @Mock
  private FormRPartARepository repositoryMock;

  @Mock
  private S3FormRPartARepositoryImpl cloudObjectRepository;

  @Mock
  private EventBroadcastService eventBroadcastService;

  private FormRPartA entity;

  private TraineeIdentity traineeIdentity;

  @Captor
  private ArgumentCaptor<FormRPartA> formRPartACaptor;

  @Captor
  private ArgumentCaptor<FormRPartADto> formRPartADtoCaptor;

  @BeforeEach
  void setUp() {
    traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(DEFAULT_TRAINEE_TIS_ID);
    service = new FormRPartAService(
        repositoryMock,
        cloudObjectRepository,
        new FormRPartAMapperImpl(),
        new ObjectMapper().findAndRegisterModules(), traineeIdentity,
        eventBroadcastService,
        FORM_R_PART_A_SUBMITTED_TOPIC);
    entity = createEntity();
  }

  /**
   * init test data.
   */
  FormRPartA createEntity() {
    FormRPartA entity = new FormRPartA();
    entity.setId(DEFAULT_ID);
    entity.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    entity.setForename(DEFAULT_FORENAME);
    entity.setSurname(DEFAULT_SURNAME);
    entity.setLifecycleState(LifecycleState.DRAFT);
    return entity;
  }

  @ParameterizedTest(name = "Should save to db and cloud when always store files "
      + "and form state is {0}.")
  @EnumSource(value = LifecycleState.class, names = {"DRAFT", "UNSUBMITTED"})
  void shouldSaveFormRPartAToDbAndCloudWhenAlwaysStoreFiles(LifecycleState state) {
    entity.setId(null);
    entity.setLifecycleState(state);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(state);

    when(repositoryMock.save(entity)).thenAnswer(invocation -> {
      FormRPartA entity = invocation.getArgument(0);

      FormRPartA savedEntity = new FormRPartA();
      BeanUtils.copyProperties(entity, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    ReflectionTestUtils.setField(service, "alwaysStoreFiles", true);

    FormRPartADto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getSurname(), is(DEFAULT_SURNAME));

    verify(repositoryMock).save(entity);
    verify(cloudObjectRepository).save(entity);
  }

  @ParameterizedTest(name = "Should save to db only when not always store files "
      + "and form state is {0}.")
  @EnumSource(value = LifecycleState.class, names = {"DRAFT", "UNSUBMITTED"})
  void shouldSaveFormRPartAToDbOnlyWhenNotAlwaysStoreFiles(LifecycleState state) {
    entity.setId(null);
    entity.setLifecycleState(state);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(state);

    when(repositoryMock.save(entity)).thenAnswer(invocation -> {
      FormRPartA entity = invocation.getArgument(0);

      FormRPartA savedEntity = new FormRPartA();
      BeanUtils.copyProperties(entity, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });
    ReflectionTestUtils.setField(service, "alwaysStoreFiles", false);

    FormRPartADto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getSurname(), is(DEFAULT_SURNAME));

    verify(repositoryMock).save(entity);
    verifyNoInteractions(cloudObjectRepository);
  }

  @Test
  void shouldSaveSubmittedFormRPartA() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(LifecycleState.SUBMITTED);
    dto.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    when(repositoryMock.save(entity)).thenAnswer(invocation -> {
      FormRPartA entity = invocation.getArgument(0);

      FormRPartA savedEntity = new FormRPartA();
      BeanUtils.copyProperties(entity, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    FormRPartADto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getSurname(), is(DEFAULT_SURNAME));

    verify(repositoryMock).save(entity);
    verify(cloudObjectRepository).save(entity);
  }

  @Test
  void shouldThrowExceptionWhenFormRPartANotSaved() {
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(LifecycleState.SUBMITTED);
    dto.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    when(cloudObjectRepository.save(any()))
        .thenThrow(new ApplicationException("Expected Exception"));

    assertThrows(ApplicationException.class, () -> service.save(dto));
    verifyNoInteractions(repositoryMock);
  }

  @Test
  void shouldGetFormRPartAs() {
    List<FormRPartA> entities = Collections.singletonList(entity);
    when(repositoryMock
        .findByTraineeTisIdAndLifecycleState(DEFAULT_TRAINEE_TIS_ID, LifecycleState.DRAFT))
        .thenReturn(entities);
    when(cloudObjectRepository.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(new ArrayList<>());

    List<FormRPartSimpleDto> dtos = service.getFormRPartAs();

    assertThat("Unexpected numbers of forms.", dtos.size(), is(entities.size()));

    FormRPartSimpleDto dto = dtos.get(0);
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
  }

  @Test
  void shouldCombineAllFormRPartAsByTraineeTisId() {
    List<FormRPartA> entities = Collections.singletonList(entity);
    when(repositoryMock
        .findByTraineeTisIdAndLifecycleState(DEFAULT_TRAINEE_TIS_ID, LifecycleState.DRAFT))
        .thenReturn(entities);
    FormRPartA cloudEntity = createEntity();
    cloudEntity.setId(DEFAULT_ID);
    cloudEntity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    cloudEntity.setLifecycleState(LifecycleState.UNSUBMITTED);
    List<FormRPartA> cloudStoredEntities = new ArrayList<>();
    cloudStoredEntities.add(cloudEntity);
    when(cloudObjectRepository.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(cloudStoredEntities);

    List<FormRPartSimpleDto> dtos = service.getFormRPartAs();

    assertThat("Unexpected numbers of forms.", dtos.size(), is(2));

    FormRPartSimpleDto dto = dtos.stream().filter(
        f -> f.getLifecycleState() == LifecycleState.DRAFT).findAny().orElseThrow();
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    dto = dtos.stream().filter(
        f -> f.getLifecycleState() == LifecycleState.UNSUBMITTED).findAny().orElseThrow();
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected submitted date.", dto.getSubmissionDate(), is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycle state.", dto.getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));
  }

  @Test
  void shouldGetFormRPartAFromCloudStorageByIdWhenOnlyCloudFormExists() {
    FormRPartA cloudForm = new FormRPartA();
    cloudForm.setId(DEFAULT_ID);
    cloudForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    cloudForm.setForename("Cloud Only");
    cloudForm.setLifecycleState(LifecycleState.UNSUBMITTED);

    when(cloudObjectRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(cloudForm));

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Cloud Only"));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.UNSUBMITTED));
  }

  @Test
  void shouldGetFormRPartAFromDatabaseByIdWhenOnlyDatabaseFormExists() {
    when(cloudObjectRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    FormRPartA dbForm = new FormRPartA();
    dbForm.setId(DEFAULT_ID);
    dbForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dbForm.setForename("Database Only");
    dbForm.setLifecycleState(LifecycleState.SUBMITTED);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(dbForm));

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Database Only"));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.SUBMITTED));
  }

  @Test
  void shouldGetFormRPartAFromCloudStorageByIdWhenCloudFormIsLatest() {
    FormRPartA cloudForm = new FormRPartA();
    cloudForm.setId(DEFAULT_ID);
    cloudForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    cloudForm.setForename("Cloud Latest");
    cloudForm.setLifecycleState(LifecycleState.UNSUBMITTED);
    cloudForm.setLastModifiedDate(LocalDateTime.MAX);

    when(cloudObjectRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(cloudForm));

    FormRPartA dbForm = new FormRPartA();
    dbForm.setId(DEFAULT_ID);
    dbForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dbForm.setForename("Database Oldest");
    dbForm.setLifecycleState(LifecycleState.SUBMITTED);
    dbForm.setLastModifiedDate(LocalDateTime.MIN);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(dbForm));

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Cloud Latest"));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.UNSUBMITTED));
  }

  @Test
  void shouldGetFormRPartAFromDatabaseByIdWhenDatabaseFormIsLatest() {
    FormRPartA cloudForm = new FormRPartA();
    cloudForm.setId(DEFAULT_ID);
    cloudForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    cloudForm.setForename("Cloud Oldest");
    cloudForm.setLifecycleState(LifecycleState.UNSUBMITTED);
    cloudForm.setLastModifiedDate(LocalDateTime.MIN);

    when(cloudObjectRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(cloudForm));

    FormRPartA dbForm = new FormRPartA();
    dbForm.setId(DEFAULT_ID);
    dbForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dbForm.setForename("Database Latest");
    dbForm.setLifecycleState(LifecycleState.SUBMITTED);
    dbForm.setLastModifiedDate(LocalDateTime.MAX);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(dbForm));

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is("Database Latest"));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.SUBMITTED));
  }

  @Test
  void shouldGetFormRPartAFromDatabaseByIdWhenCloudAndDatabaseEqualModifiedTime() {
    LocalDateTime now = LocalDateTime.now();

    FormRPartA cloudForm = new FormRPartA();
    cloudForm.setId(DEFAULT_ID);
    cloudForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    cloudForm.setForename("Cloud Equal");
    cloudForm.setLifecycleState(LifecycleState.UNSUBMITTED);
    cloudForm.setLastModifiedDate(now);

    when(cloudObjectRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(cloudForm));

    FormRPartA dbForm = new FormRPartA();
    dbForm.setId(DEFAULT_ID);
    dbForm.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dbForm.setForename("Database Equal");
    dbForm.setLifecycleState(LifecycleState.SUBMITTED);
    dbForm.setLastModifiedDate(now);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(dbForm));

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING);

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

    boolean deleted = service.deleteFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected delete result.", deleted, is(true));
  }

  @Test
  void shouldReturnFalseWhenFormToDeleteNotFound() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    boolean deleted = service.deleteFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected delete result.", deleted, is(false));
  }

  @ParameterizedTest(name = "Should throw exception when deleting form with {0} state")
  @EnumSource(names = {"DRAFT"}, mode = Mode.EXCLUDE)
  void shouldThrowExceptionWhenDeletingNonDraftForm(LifecycleState state) {
    entity.setLifecycleState(state);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    assertThrows(IllegalArgumentException.class,
        () -> service.deleteFormRPartAById(DEFAULT_ID_STRING));
  }

  @Test
  void shouldPartialDeleteFormRPartAById() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(cloudObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<FormRPartADto> resultDto = service.partialDeleteFormRPartAById(DEFAULT_ID);

    assertThat("Unexpected DTO presence.", resultDto.isPresent(), is(true));

    FormRPartADto expectedDto = new FormRPartADto();
    expectedDto.setId(DEFAULT_ID.toString());
    expectedDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    expectedDto.setLifecycleState(LifecycleState.DELETED);
    assertThat("Unexpected DTO.", resultDto.get(), is(expectedDto));

    verify(repositoryMock).save(any());
    verify(cloudObjectRepository).save(any());
  }

  @Test
  void shouldNotPartialDeleteWhenTraineeFormRPartANotFoundInDb() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.empty());

    service.partialDeleteFormRPartAById(DEFAULT_ID);

    verify(repositoryMock, never()).save(formRPartACaptor.capture());
  }

  @Test
  void shouldPublishEventWhenSavingSubmittedFormRPartA() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(LifecycleState.SUBMITTED);
    dto.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    when(repositoryMock.save(any())).thenAnswer(invocation -> {
      FormRPartA toSave = invocation.getArgument(0);
      FormRPartA savedEntity = new FormRPartA();
      BeanUtils.copyProperties(toSave, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    service.save(dto);

    ArgumentCaptor<FormRPartADto> dtoCaptor = ArgumentCaptor.forClass(FormRPartADto.class);
    verify(eventBroadcastService).publishFormRPartAEvent(
        dtoCaptor.capture(), eq(Map.of("formType", "formr-a")), eq(FORM_R_PART_A_SUBMITTED_TOPIC));

    FormRPartADto publishedDto = dtoCaptor.getValue();
    assertThat("Unexpected form ID.", publishedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected lifecycle state.", publishedDto.getLifecycleState(),
        is(LifecycleState.SUBMITTED));
  }

  @ParameterizedTest(name = "Should not publish event when saving form with {0} state.")
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = {"SUBMITTED"})
  void shouldNotPublishEventWhenSavingNonSubmittedFormRPartA(LifecycleState state) {
    entity.setId(null);
    entity.setLifecycleState(state);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(state);

    when(repositoryMock.save(any())).thenAnswer(invocation -> {
      FormRPartA toSave = invocation.getArgument(0);
      FormRPartA savedEntity = new FormRPartA();
      BeanUtils.copyProperties(toSave, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    service.save(dto);

    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldUnsubmitFormRPartAById() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(cloudObjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<FormRPartADto> resultDtoOptional = service.unsubmitFormRPartAById(DEFAULT_ID);

    assertThat("Unexpected DTO presence.", resultDtoOptional.isPresent(), is(true));
    FormRPartADto resultDto = resultDtoOptional.get();

    assertThat("Unexpected Id.", resultDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected Trainee TIS Id.", resultDto.getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", resultDto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", resultDto.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected lifecycle state.", resultDto.getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));

    verify(repositoryMock).save(any());
    verify(cloudObjectRepository).save(any());
  }

  @Test
  void shouldNotUnsubmitWhenTraineeFormRPartANotFoundInDb() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.empty());

    service.unsubmitFormRPartAById(DEFAULT_ID);

    verify(repositoryMock, never()).save(formRPartACaptor.capture());
  }
}
