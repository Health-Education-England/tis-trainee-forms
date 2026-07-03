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

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.content.FormrPartaContentDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.UserIdentityResolver;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.TemporalMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.content.FormrPartaContent;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.EventBroadcastService.FormrFileEventDto;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@ExtendWith(MockitoExtension.class)
class FormRPartAServiceTest {

  private static final UUID DEFAULT_ID = UUID.randomUUID();
  private static final String DEFAULT_ID_STRING = DEFAULT_ID.toString();
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final Instant DEFAULT_SUBMISSION_DATE = Instant.now();
  private static final String DEFAULT_PROGRAMME_SPECIALTY = "Cardiology";
  private static final String FORM_R_PART_A_UPDATED_TOPIC = "arn:aws:sns:topic";

  private FormRPartAService service;

  @Mock
  private FormRPartARepository repositoryMock;

  @Mock
  private EventBroadcastService eventBroadcastService;

  @Mock
  private SubmissionHistoryService<FormRPartA> historyService;

  private FormRPartA entity;

  private TraineeIdentity traineeIdentity;

  @Captor
  private ArgumentCaptor<FormRPartA> formRPartACaptor;

  @BeforeEach
  void setUp() {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setName("Admin User");
    adminIdentity.setEmail("admin.user@example.com");
    adminIdentity.setGroups(Set.of("group1"));
    traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(DEFAULT_TRAINEE_TIS_ID);
    UserIdentityResolver identityResolver = new UserIdentityResolver(adminIdentity,
        traineeIdentity);

    service = new FormRPartAService(
        repositoryMock,
        new FormRPartAMapperImpl(new TemporalMapper(ZoneId.of("Etc/UTC"))),
        new ObjectMapper().findAndRegisterModules(), identityResolver,
        eventBroadcastService, historyService,
        FORM_R_PART_A_UPDATED_TOPIC);
    entity = createEntity();
  }

  /**
   * init test data.
   */
  FormRPartA createEntity() {
    FormRPartA newEntity = new FormRPartA();
    newEntity.setId(DEFAULT_ID);
    newEntity.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    newEntity.setContent(FormrPartaContent.builder()
        .isArcp(true)
        .programmeSpecialty(DEFAULT_PROGRAMME_SPECIALTY)
        .forename(DEFAULT_FORENAME)
        .surname(DEFAULT_SURNAME)
        .build());
    newEntity.setLifecycleState(DRAFT);
    return newEntity;
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = {"DRAFT", "SUBMITTED"})
  void shouldThrowExceptionWhenCreatingFormNotInInitialState(LifecycleState state) {
    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(state);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    assertThrows(IllegalArgumentException.class, () -> service.save(dto));

    verify(repositoryMock, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldThrowExceptionWhenUpdatingNonModifiableForm(LifecycleState state) {
    UUID id = UUID.randomUUID();
    entity.setId(id);
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().state(state).build())
        .submitted(DEFAULT_SUBMISSION_DATE)
        .build());

    FormRPartADto dto = new FormRPartADto();
    dto.setId(id.toString());
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(DRAFT);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.findById(id)).thenReturn(Optional.of(entity));

    assertThrows(IllegalArgumentException.class, () -> service.save(dto));

    verify(repositoryMock, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldThrowExceptionWhenUpdatingToUnsupportedStatus(LifecycleState state) {
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartADto dto = new FormRPartADto();
    dto.setId(DEFAULT_ID_STRING);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(state);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    assertThrows(IllegalArgumentException.class, () -> service.save(dto));

    verify(repositoryMock, never()).save(any());
  }

  @Test
  void shouldSaveNewFormWhenDraft() {
    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(DRAFT);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.save(any())).thenAnswer(inv -> {
      FormRPartA form = inv.getArgument(0);
      form.setId(DEFAULT_ID);
      return form;
    });

    FormRPartADto savedDto = service.save(dto);
    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getContent().getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getContent().getSurname(), is(DEFAULT_SURNAME));

    assertThat("Unexpected submission date.", savedDto.getSubmissionDate(), nullValue());
    assertThat("Unexpected lifecycle state.", savedDto.getLifecycleState(), is(DRAFT));

    verify(repositoryMock).save(any());
  }

  @Test
  void shouldSaveNewFormWhenSubmitted() {
    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(SUBMITTED);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.save(any())).thenAnswer(inv -> {
      FormRPartA form = inv.getArgument(0);
      form.setId(DEFAULT_ID);
      return form;
    });

    FormRPartADto savedDto = service.save(dto);
    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getContent().getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getContent().getSurname(), is(DEFAULT_SURNAME));

    assertThat("Unexpected submission date.", savedDto.getSubmissionDate(), not(nullValue()));
    assertThat("Unexpected lifecycle state.", savedDto.getLifecycleState(), is(SUBMITTED));

    verify(repositoryMock).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldSaveFormWhenUpdatingModifiableForm(LifecycleState state) {
    UUID id = UUID.randomUUID();
    entity.setId(id);
    entity.setLifecycleState(state);

    FormRPartADto dto = new FormRPartADto();
    dto.setId(id.toString());
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(state);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.findById(id)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.save(dto);
    verify(repositoryMock).save(any());
  }

  @Test
  void shouldSaveSubmittedFormRPartAWhenCurrentlyDraft() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartADto dto = new FormRPartADto();
    dto.setId(DEFAULT_ID_STRING);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(SUBMITTED);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    content.setIsArcp(true);
    content.setProgrammeSpecialty(DEFAULT_PROGRAMME_SPECIALTY);
    dto.setContent(content);

    when(repositoryMock.save(any())).thenAnswer(invocation -> {
      FormRPartA invEntity = invocation.getArgument(0);

      FormRPartA savedEntity = new FormRPartA();
      BeanUtils.copyProperties(invEntity, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    FormRPartADto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getContent().getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getContent().getSurname(), is(DEFAULT_SURNAME));

    verify(repositoryMock).save(any());
  }

  @Test
  void shouldSetFormRefWhenSubmittedFormRPartA() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartADto dto = new FormRPartADto();
    dto.setId(DEFAULT_ID_STRING);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(SUBMITTED);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.countSubmittedByTraineeId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(4L);
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.save(dto);

    ArgumentCaptor<FormRPartA> formCaptor = ArgumentCaptor.captor();
    verify(repositoryMock).save(formCaptor.capture());

    FormRPartA savedEntity = formCaptor.getValue();
    assertThat("Unexpected form reference.", savedEntity.getFormRef(),
        is("formr_parta_" + DEFAULT_TRAINEE_TIS_ID + "_005"));
  }

  @Test
  void shouldSnapshotWhenSubmittingFormRPartA() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartADto dto = new FormRPartADto();
    dto.setId(DEFAULT_ID_STRING);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(SUBMITTED);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.save(dto);

    ArgumentCaptor<FormRPartA> formCaptor = ArgumentCaptor.captor();
    verify(repositoryMock).save(formCaptor.capture());

    FormRPartA savedEntity = formCaptor.getValue();
    verify(historyService).takeSnapshot(savedEntity);
  }

  @Test
  void shouldThrowExceptionWhenFormRPartANotSaved() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartADto dto = new FormRPartADto();
    dto.setId(DEFAULT_ID_STRING);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(SUBMITTED);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.save(any())).thenThrow(ApplicationException.class);

    assertThrows(ApplicationException.class, () -> service.save(dto));
  }

  @Test
  void shouldGetFormRPartAs() {
    List<FormRPartA> entities = Collections.singletonList(entity);
    when(repositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(entities);

    List<FormRPartSimpleDto> dtos = service.getFormRPartAs();

    assertThat("Unexpected numbers of forms.", dtos.size(), is(entities.size()));

    FormRPartSimpleDto dto = dtos.get(0);
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
  }

  @Test
  void shouldReturnNullGettingFormRPartAByIdWhenFormNotExists() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected form.", dto, nullValue());
  }

  @Test
  void shouldGetFormRPartAByIdWhenFormExists() {
    FormRPartA form = new FormRPartA();
    form.setId(DEFAULT_ID);
    form.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    form.setLifecycleState(UNSUBMITTED);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(form));

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(UNSUBMITTED));
  }

  @Test
  void shouldReturnTrueWhenDeletingDraft() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    boolean deleted = service.deleteFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected delete result.", deleted, is(true));
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldReturnFalseWhenFormToDeleteNotFound() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    boolean deleted = service.deleteFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected delete result.", deleted, is(false));
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest(name = "Should throw exception when deleting form with {0} state")
  @EnumSource(names = {"DRAFT"}, mode = Mode.EXCLUDE)
  void shouldThrowExceptionWhenDeletingNonDraftForm(LifecycleState state) {
    entity.setLifecycleState(state);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    assertThrows(IllegalArgumentException.class,
        () -> service.deleteFormRPartAById(DEFAULT_ID_STRING));
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldPartialDeleteFormRPartAById() throws MethodArgumentNotValidException {
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).then(invocation -> invocation.getArgument(0));

    Optional<FormRPartADto> resultDto = service.partialDeleteFormRPartAById(DEFAULT_ID);

    assertThat("Unexpected DTO presence.", resultDto.isPresent(), is(true));

    FormRPartADto dto = resultDto.get();
    assertThat("Unexpected ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected content.", dto.getContent(), nullValue());
    assertThat("Unexpected submission date.", dto.getSubmissionDate(),
        is(entity.getStatus().submitted().atZone(UTC).toLocalDateTime()));
    assertThat("Unexpected lifecycle state.", dto.getLifecycleState(), is(DELETED));

    verify(repositoryMock).save(any());
    verify(eventBroadcastService).publishFormRPartAEvent(any(), any(), any());
    verify(eventBroadcastService).publishFormrFileEvent(any());
  }

  @Test
  void shouldPublishEventWhenPartialDeletingFormRPartA() throws MethodArgumentNotValidException {
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).then(invocation -> invocation.getArgument(0));

    service.partialDeleteFormRPartAById(DEFAULT_ID);

    ArgumentCaptor<FormRPartADto> dtoCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishFormRPartAEvent(
        dtoCaptor.capture(), eq(Map.of("formType", "formr-a")), eq(FORM_R_PART_A_UPDATED_TOPIC));

    FormRPartADto publishedDto = dtoCaptor.getValue();
    assertThat("Unexpected form ID.", publishedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected lifecycle state.", publishedDto.getLifecycleState(), is(DELETED));

    ArgumentCaptor<FormrFileEventDto> fileEventCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishFormrFileEvent(fileEventCaptor.capture());

    FormrFileEventDto fileEvent = fileEventCaptor.getValue();
    assertThat("Unexpected form name.", fileEvent.formName(), is(DEFAULT_ID_STRING + ".json"));
    assertThat("Unexpected trainee ID.", fileEvent.traineeId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected form type.", fileEvent.formType(), is("formr-a"));
    assertThat("Unexpected lifecycle state.", fileEvent.lifecycleState(), is(DELETED.toString()));
    assertThat("Unexpected event date.", fileEvent.eventDate(), notNullValue());

    assertThat("Unexpected content ID.", fileEvent.formContentDto(),
        hasEntry("id", DEFAULT_ID_STRING));
    assertThat("Unexpected content trainee ID.", fileEvent.formContentDto(),
        hasEntry("traineeTisId", DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected content lifecycle state.", fileEvent.formContentDto(),
        hasEntry("lifecycleState", DELETED.toString()));
    assertThat("Unexpected content.", fileEvent.formContentDto().keySet(), not(hasItem("content")));
  }

  @Test
  void shouldNotPartialDeleteWhenTraineeFormRPartANotFoundInDb()
      throws MethodArgumentNotValidException {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.empty());

    service.partialDeleteFormRPartAById(DEFAULT_ID);

    verify(repositoryMock, never()).save(formRPartACaptor.capture());
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldPublishEventWhenSavingSubmittedFormRPartA() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartADto dto = new FormRPartADto();
    dto.setId(DEFAULT_ID_STRING);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(SUBMITTED);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

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
        dtoCaptor.capture(), eq(Map.of("formType", "formr-a")), eq(FORM_R_PART_A_UPDATED_TOPIC));

    FormRPartADto publishedDto = dtoCaptor.getValue();
    assertThat("Unexpected form ID.", publishedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected lifecycle state.", publishedDto.getLifecycleState(), is(SUBMITTED));

    ArgumentCaptor<FormrFileEventDto> fileEventCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishFormrFileEvent(fileEventCaptor.capture());

    FormrFileEventDto fileEvent = fileEventCaptor.getValue();
    assertThat("Unexpected form name.", fileEvent.formName(), is(DEFAULT_ID_STRING + ".json"));
    assertThat("Unexpected trainee ID.", fileEvent.traineeId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected form type.", fileEvent.formType(), is("formr-a"));
    assertThat("Unexpected lifecycle state.", fileEvent.lifecycleState(), is(SUBMITTED.toString()));
    assertThat("Unexpected event date.", fileEvent.eventDate(), notNullValue());

    assertThat("Unexpected content ID.", fileEvent.formContentDto(),
        hasEntry("id", DEFAULT_ID_STRING));
    assertThat("Unexpected content trainee ID.", fileEvent.formContentDto(),
        hasEntry("traineeTisId", DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected content lifecycle state.", fileEvent.formContentDto(),
        hasEntry("lifecycleState", SUBMITTED.toString()));
    assertThat("Unexpected content forename.", fileEvent.formContentDto(),
        hasEntry("forename", DEFAULT_FORENAME));
    assertThat("Unexpected content surname.", fileEvent.formContentDto(),
        hasEntry("surname", DEFAULT_SURNAME));
  }

  @ParameterizedTest(name = "Should not publish event when saving form with {0} state.")
  @EnumSource(value = LifecycleState.class, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotPublishEventWhenSavingNonSubmittedFormRPartA(LifecycleState state) {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    entity.setLifecycleState(state);

    FormRPartADto dto = new FormRPartADto();
    dto.setId(DEFAULT_ID_STRING);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(state);

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

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
  void shouldUnsubmitFormRPartAById() throws MethodArgumentNotValidException {
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<FormRPartADto> resultDtoOptional = service.unsubmitFormRPartAById(DEFAULT_ID);

    assertThat("Unexpected DTO presence.", resultDtoOptional.isPresent(), is(true));
    FormRPartADto resultDto = resultDtoOptional.get();

    assertThat("Unexpected Id.", resultDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected Trainee TIS Id.", resultDto.getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", resultDto.getContent().getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", resultDto.getContent().getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected lifecycle state.", resultDto.getLifecycleState(), is(UNSUBMITTED));

    verify(repositoryMock).save(any());
    verify(eventBroadcastService).publishFormRPartAEvent(any(), any(), any());
    verify(eventBroadcastService).publishFormrFileEvent(any());
  }

  @Test
  void shouldIncrementRevisionWhenUnsubmittingFormRPartA() throws MethodArgumentNotValidException {
    entity.setLifecycleState(SUBMITTED);
    entity.setRevision(4);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.unsubmitFormRPartAById(DEFAULT_ID);

    ArgumentCaptor<FormRPartA> formCaptor = ArgumentCaptor.captor();
    verify(repositoryMock).save(formCaptor.capture());

    FormRPartA savedForm = formCaptor.getValue();
    assertThat("Unexpected revision.", savedForm.getRevision(), is(5));
  }

  @Test
  void shouldPublishEventWhenUnsubmittingFormRPartA() throws MethodArgumentNotValidException {
    entity.setId(DEFAULT_ID);
    entity.setLifecycleState(SUBMITTED);

    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.unsubmitFormRPartAById(DEFAULT_ID);

    ArgumentCaptor<FormRPartADto> dtoCaptor = ArgumentCaptor.forClass(FormRPartADto.class);
    verify(eventBroadcastService).publishFormRPartAEvent(
        dtoCaptor.capture(), eq(Map.of("formType", "formr-a")), eq(FORM_R_PART_A_UPDATED_TOPIC));

    FormRPartADto publishedDto = dtoCaptor.getValue();
    assertThat("Unexpected form ID.", publishedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected lifecycle state.", publishedDto.getLifecycleState(),
        is(UNSUBMITTED));

    ArgumentCaptor<FormrFileEventDto> fileEventCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishFormrFileEvent(fileEventCaptor.capture());

    FormrFileEventDto fileEvent = fileEventCaptor.getValue();
    assertThat("Unexpected form name.", fileEvent.formName(), is(DEFAULT_ID_STRING + ".json"));
    assertThat("Unexpected trainee ID.", fileEvent.traineeId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected form type.", fileEvent.formType(), is("formr-a"));
    assertThat("Unexpected lifecycle state.", fileEvent.lifecycleState(),
        is(UNSUBMITTED.toString()));
    assertThat("Unexpected event date.", fileEvent.eventDate(), notNullValue());

    assertThat("Unexpected content ID.", fileEvent.formContentDto(),
        hasEntry("id", DEFAULT_ID_STRING));
    assertThat("Unexpected content trainee ID.", fileEvent.formContentDto(),
        hasEntry("traineeTisId", DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected content lifecycle state.", fileEvent.formContentDto(),
        hasEntry("lifecycleState", UNSUBMITTED.toString()));
    assertThat("Unexpected content forename.", fileEvent.formContentDto(),
        hasEntry("forename", DEFAULT_FORENAME));
    assertThat("Unexpected content surname.", fileEvent.formContentDto(),
        hasEntry("surname", DEFAULT_SURNAME));
  }

  @Test
  void shouldNotUnsubmitWhenTraineeFormRPartANotFoundInDb() throws MethodArgumentNotValidException {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.empty());

    service.unsubmitFormRPartAById(DEFAULT_ID);

    verify(repositoryMock, never()).save(any());
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldGetFormRPartAsByTraineeId() {

    List<FormRPartA> entities = Collections.singletonList(entity);

    when(repositoryMock.findNotDraftNorDeletedByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(entities);

    List<FormRPartSimpleDto> dtos = service.getFormRPartAs(DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected numbers of forms.", dtos.size(), is(entities.size()));

    FormRPartSimpleDto dto = dtos.get(0);
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected programme name.", dto.getProgrammeName(),
        is(DEFAULT_PROGRAMME_SPECIALTY));
    assertThat("Unexpected isArcp.", dto.getIsArcp(), is(true));
  }

  @Test
  void shouldGetAdminsFormRPartAByIdWhenUnsubmitted() {
    entity.setLifecycleState(UNSUBMITTED);

    when(repositoryMock.findByIdAndNotDraftNorDeleted(DEFAULT_ID))
        .thenReturn(Optional.of(entity));

    Optional<FormRPartADto> optionalDto = service.getAdminsFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Unexpected DTO.", optionalDto.isPresent(), is(true));
    FormRPartADto dto = optionalDto.get();

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected lifecycle state.", dto.getLifecycleState(), is(UNSUBMITTED));
  }

  @Test
  void shouldReturnEmptyWhenAdminsFormRPartANotFound() {
    when(repositoryMock.findByIdAndNotDraftNorDeleted(DEFAULT_ID))
        .thenReturn(Optional.empty());

    Optional<FormRPartADto> optionalDto = service.getAdminsFormRPartAById(DEFAULT_ID_STRING);

    assertThat("Expected empty for non-existent form.", optionalDto.isEmpty(), is(true));
  }

  @Test
  void shouldReturnEmptyListWhenNoFormsFoundForTraineeId() {
    String traineeId = "99999";

    when(repositoryMock.findNotDraftNorDeletedByTraineeTisId(traineeId))
        .thenReturn(new ArrayList<>());

    List<FormRPartSimpleDto> dtos = service.getFormRPartAs(traineeId);

    assertThat("Unexpected numbers of forms.", dtos.size(), is(0));
  }

  @Test
  void shouldPublishUpdateNotification() {
    FormRPartADto form = new FormRPartADto();
    form.setId(UUID.randomUUID().toString());

    service.publishUpdateNotification(form, "my-topic");

    verify(eventBroadcastService).publishFormRPartAEvent(form,
        Map.of(EventBroadcastService.MESSAGE_ATTRIBUTE_KEY_FORM_TYPE, "formr-a"), "my-topic");
    verify(eventBroadcastService, never()).publishFormrFileEvent(any());
  }
}
