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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
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
  private static final Set<String> FIXED_FIELDS =
      Set.of("id", "traineeTisId", "lifecycleState");

  private FormRPartAService service;

  @Mock
  private FormRPartARepository repositoryMock;

  @Mock
  private S3FormRPartARepositoryImpl cloudObjectRepository;

  private FormRPartA entity;

  @Captor
  private ArgumentCaptor<FormRPartA> formRPartACaptor;


  @BeforeEach
  void setUp() {
    service = new FormRPartAService(
        repositoryMock,
        cloudObjectRepository,
        new FormRPartAMapperImpl(),
        new ObjectMapper().findAndRegisterModules());
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

  @Test
  void shouldSaveDraftFormRPartAToDbAndCloudWhenAlwaysStoreFiles() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.DRAFT);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(LifecycleState.DRAFT);

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

  @Test
  void shouldSaveDraftFormRPartAToDbWhenNotAlwaysStoreFiles() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.DRAFT);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(LifecycleState.DRAFT);

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
  void shouldSaveUnsubmittedFormRPartA() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.UNSUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(LifecycleState.UNSUBMITTED);
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
  void shouldGetFormRPartAsByTraineeTisId() {
    List<FormRPartA> entities = Collections.singletonList(entity);
    when(repositoryMock
        .findByTraineeTisIdAndLifecycleState(DEFAULT_TRAINEE_TIS_ID, LifecycleState.DRAFT))
        .thenReturn(entities);
    when(cloudObjectRepository.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(new ArrayList<>());

    List<FormRPartSimpleDto> dtos = service.getFormRPartAsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

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

    List<FormRPartSimpleDto> dtos = service.getFormRPartAsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

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
  void shouldGetFormRPartBFromCloudStorageById() {
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    when(cloudObjectRepository.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", dto.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(LifecycleState.SUBMITTED));
    verifyNoInteractions(repositoryMock);
  }

  @Test
  void shouldGetDraftFormRPartAById() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", dto.getSurname(), is(DEFAULT_SURNAME));
  }

  @Test
  void shouldReturnTrueWhenDeletingDraft() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    boolean deleted = service.deleteFormRPartAById(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected delete result.", deleted, is(true));
  }

  @Test
  void shouldReturnFalseWhenFormToDeleteNotFound() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    boolean deleted = service.deleteFormRPartAById(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected delete result.", deleted, is(false));
  }

  @ParameterizedTest(name = "Should throw exception when deleting form with {0} state")
  @EnumSource(names = {"DRAFT"}, mode = Mode.EXCLUDE)
  void shouldThrowExceptionWhenDeletingNonDraftForm(LifecycleState state) {
    entity.setLifecycleState(state);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    assertThrows(IllegalArgumentException.class,
        () -> service.deleteFormRPartAById(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID));
  }

  @Test
  void shouldPartialDeleteFormRPartAById() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    FormRPartADto resultDto = service.partialDeleteFormRPartAById(
        DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID, FIXED_FIELDS);

    FormRPartADto expectedDto = new FormRPartADto();
    expectedDto.setId(DEFAULT_ID.toString());
    expectedDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    expectedDto.setLifecycleState(LifecycleState.DELETED);

    verify(repositoryMock).save(any());
    assertThat("Unexpected DTO.", resultDto, is(expectedDto));
  }

  @Test
  void shouldNotPartialDeleteWhenFormRPartANotFoundInDb() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    service.partialDeleteFormRPartAById(
        DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID, FIXED_FIELDS);

    verify(repositoryMock, never()).save(formRPartACaptor.capture());
  }

  @Test
  void shouldThrowExceptionWhenFailToPartialDeleteFormRPartA() throws ApplicationException {
    when(repositoryMock.findByIdAndTraineeTisId(any(), any()))
        .thenThrow(IllegalArgumentException.class);
    assertThrows(ApplicationException.class, () -> service.partialDeleteFormRPartAById(
        DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID, FIXED_FIELDS));
  }
}
