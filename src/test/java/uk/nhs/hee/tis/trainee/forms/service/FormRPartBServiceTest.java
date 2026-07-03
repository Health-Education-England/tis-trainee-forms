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

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
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
import java.time.LocalDate;
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
import uk.nhs.hee.tis.trainee.forms.dto.DeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.content.FormrPartbContentDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.UserIdentityResolver;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.TemporalMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.Declaration;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.model.content.FormrPartbContent;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.EventBroadcastService.FormrFileEventDto;
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
  private static final String DEFAULT_PROGRAMME_SPECIALTY = "Cardiology";

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
  private static final Instant DEFAULT_SUBMISSION_DATE = Instant.now();

  private static final Boolean DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS = true;
  private static final Boolean DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS = true;
  private static final String FORM_R_PART_B_UPDATED_TOPIC = "arn:aws:sns:topic";

  private FormRPartBService service;

  @Mock
  private FormRPartBRepository repositoryMock;

  @Mock
  private EventBroadcastService eventBroadcastService;

  @Mock
  private SubmissionHistoryService<FormRPartB> historyService;

  private ObjectMapper objectMapper;

  private TraineeIdentity traineeIdentity;

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
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setName("Admin User");
    adminIdentity.setEmail("admin.user@example.com");
    adminIdentity.setGroups(Set.of("group1"));
    traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(DEFAULT_TRAINEE_TIS_ID);
    UserIdentityResolver identityResolver = new UserIdentityResolver(adminIdentity,
        traineeIdentity);

    mapper = new FormRPartBMapperImpl(new TemporalMapper(ZoneId.of("Etc/UTC")));
    objectMapper = new ObjectMapper().findAndRegisterModules();

    service = new FormRPartBService(repositoryMock, mapper, objectMapper, identityResolver,
        eventBroadcastService, historyService, FORM_R_PART_B_UPDATED_TOPIC);
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
    FormRPartB newEntity = new FormRPartB();
    newEntity.setId(DEFAULT_ID);
    newEntity.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    newEntity.setContent(FormrPartbContent.builder()
        .isArcp(true)
        .programmeSpecialty(DEFAULT_PROGRAMME_SPECIALTY)
        .forename(DEFAULT_FORENAME)
        .surname(DEFAULT_SURNAME)
        .work(Collections.singletonList(work))
        .totalLeave(DEFAULT_TOTAL_LEAVE)
        .isHonest(DEFAULT_IS_HONEST)
        .isHealthy(DEFAULT_IS_HEALTHY)
        .healthStatement(DEFAULT_HEALTHY_STATEMENT)
        .havePreviousDeclarations(DEFAULT_HAVE_PREVIOUS_DECLARATIONS)
        .previousDeclarations(Collections.singletonList(previousDeclaration))
        .previousDeclarationSummary(DEFAULT_PREVIOUS_DECLARATION_SUMMARY)
        .haveCurrentDeclarations(DEFAULT_HAVE_CURRENT_DECLARATIONS)
        .currentDeclarations(Collections.singletonList(currentDeclaration))
        .currentDeclarationSummary(DEFAULT_CURRENT_DECLARATION_SUMMARY)
        .haveCurrentUnresolvedDeclarations(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS)
        .havePreviousUnresolvedDeclarations(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS)
        .build());
    newEntity.setLifecycleState(DRAFT);
    return newEntity;
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

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = {"DRAFT", "SUBMITTED"})
  void shouldThrowExceptionWhenCreatingFormNotInInitialState(LifecycleState state) {
    FormRPartBDto dto = new FormRPartBDto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(state);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartbContentDto content = new FormrPartbContentDto();
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

    FormRPartBDto dto = mapper.toDto(entity);
    dto.setLifecycleState(DRAFT);

    when(repositoryMock.findById(id)).thenReturn(Optional.of(entity));

    assertThrows(IllegalArgumentException.class, () -> service.save(dto));

    verify(repositoryMock, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldThrowExceptionWhenUpdatingToUnsupportedStatus(LifecycleState state) {
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartBDto dto = new FormRPartBDto();
    dto.setId(DEFAULT_ID_STRING);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(state);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartbContentDto content = new FormrPartbContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    assertThrows(IllegalArgumentException.class, () -> service.save(dto));

    verify(repositoryMock, never()).save(any());
  }

  @Test
  void shouldSaveNewFormWhenDraft() {
    FormRPartBDto dto = new FormRPartBDto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(DRAFT);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartbContentDto content = new FormrPartbContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.save(any())).thenAnswer(inv -> {
      FormRPartB form = inv.getArgument(0);
      form.setId(DEFAULT_ID);
      return form;
    });

    FormRPartBDto savedDto = service.save(dto);
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
    FormRPartBDto dto = new FormRPartBDto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setLifecycleState(SUBMITTED);
    dto.setSubmissionDate(LocalDateTime.ofInstant(DEFAULT_SUBMISSION_DATE, UTC));

    FormrPartbContentDto content = new FormrPartbContentDto();
    content.setForename(DEFAULT_FORENAME);
    content.setSurname(DEFAULT_SURNAME);
    dto.setContent(content);

    when(repositoryMock.save(any())).thenAnswer(inv -> {
      FormRPartB form = inv.getArgument(0);
      form.setId(DEFAULT_ID);
      return form;
    });

    FormRPartBDto savedDto = service.save(dto);
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

    FormRPartBDto dto = mapper.toDto(entity);
    dto.setLifecycleState(state);

    when(repositoryMock.findById(id)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.save(dto);
    verify(repositoryMock).save(any());
  }

  @Test
  void shouldSaveSubmittedFormRPartB() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartBDto dto = mapper.toDto(entity);
    dto.setLifecycleState(SUBMITTED);

    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    FormRPartBDto savedDto = service.save(dto);
    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));

    FormrPartbContentDto savedContent = savedDto.getContent();
    assertThat("Unexpected forename.", savedContent.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedContent.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected work.", savedContent.getWork(), is(Collections.singletonList(workDto)));
    assertThat("Unexpected total leave.", savedContent.getTotalLeave(), is(DEFAULT_TOTAL_LEAVE));
    assertThat("Unexpected isHonest flag.", savedContent.getIsHonest(), is(DEFAULT_IS_HONEST));
    assertThat("Unexpected isHealthy flag.", savedContent.getIsHealthy(), is(DEFAULT_IS_HEALTHY));
    assertThat("Unexpected health statement.", savedContent.getHealthStatement(),
        is(DEFAULT_HEALTHY_STATEMENT));
    assertThat("Unexpected havePreviousDeclarations flag.",
        savedContent.getHavePreviousDeclarations(), is(DEFAULT_HAVE_PREVIOUS_DECLARATIONS));
    assertThat("Unexpected previous declarations.", savedContent.getPreviousDeclarations(),
        is(Collections.singletonList(previousDeclarationDto)));
    assertThat("Unexpected previous declaration summary.",
        savedContent.getPreviousDeclarationSummary(), is(DEFAULT_PREVIOUS_DECLARATION_SUMMARY));
    assertThat("Unexpected haveCurrentDeclarations flag.",
        savedContent.getHaveCurrentDeclarations(), is(DEFAULT_HAVE_CURRENT_DECLARATIONS));
    assertThat("Unexpected current declarations.", savedContent.getCurrentDeclarations(),
        is(Collections.singletonList(currentDeclarationDto)));
    assertThat("Unexpected current declaration summary.",
        savedContent.getCurrentDeclarationSummary(), is(DEFAULT_CURRENT_DECLARATION_SUMMARY));
    assertThat("Unexpected haveCurrentUnresolvedDeclarations flag.",
        savedContent.getHaveCurrentUnresolvedDeclarations(),
        is(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS));
    assertThat("Unexpected havePreviousUnresolvedDeclarations flag.",
        savedContent.getHavePreviousUnresolvedDeclarations(),
        is(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS));

    verify(repositoryMock).save(any());
  }

  @Test
  void shouldSetFormRefWhenSubmittedFormRPartB() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartBDto dto = mapper.toDto(entity);
    dto.setLifecycleState(SUBMITTED);

    when(repositoryMock.countSubmittedByTraineeId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(4L);
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.save(dto);

    ArgumentCaptor<FormRPartB> formCaptor = ArgumentCaptor.captor();
    verify(repositoryMock).save(formCaptor.capture());

    FormRPartB savedEntity = formCaptor.getValue();
    assertThat("Unexpected form reference.", savedEntity.getFormRef(),
        is("formr_partb_" + DEFAULT_TRAINEE_TIS_ID + "_005"));
  }

  @Test
  void shouldSnapshotWhenSubmittingFormRPartB() {
    entity.setRevision(3);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartBDto dto = mapper.toDto(entity);
    dto.setLifecycleState(SUBMITTED);

    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.save(dto);

    ArgumentCaptor<FormRPartB> formCaptor = ArgumentCaptor.captor();
    verify(repositoryMock).save(formCaptor.capture());

    FormRPartB savedEntity = formCaptor.getValue();
    verify(historyService).takeSnapshot(savedEntity);
  }

  @Test
  void shouldThrowExceptionWhenFormRPartBNotSaved() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenThrow(ApplicationException.class);

    FormRPartBDto dto = mapper.toDto(entity);
    assertThrows(ApplicationException.class, () -> service.save(dto));
  }

  @Test
  void shouldGetFormRPartBsByTraineeTisId() {
    List<FormRPartB> entities = Collections.singletonList(entity);
    when(repositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(entities);

    List<FormRPartSimpleDto> dtos = service.getFormRPartBs();

    assertThat("Unexpected numbers of forms.", dtos.size(), is(entities.size()));

    FormRPartSimpleDto dto = dtos.get(0);
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
  }

  @Test
  void shouldReturnNullGettingFormRPartBByIdWhenFormNotExists() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    FormRPartBDto dto = service.getFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected form.", dto, nullValue());
  }

  @Test
  void shouldGetFormRPartBByIdWhenFormExists() {
    FormRPartB form = new FormRPartB();
    form.setId(DEFAULT_ID);
    form.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    form.setLifecycleState(UNSUBMITTED);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(form));

    FormRPartBDto dto = service.getFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected status.", dto.getLifecycleState(), is(UNSUBMITTED));
  }

  @Test
  void shouldReturnTrueWhenDeletingDraft() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    boolean deleted = service.deleteFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected delete result.", deleted, is(true));
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldReturnFalseWhenFormToDeleteNotFound() {
    entity.setLifecycleState(LifecycleState.DRAFT);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    boolean deleted = service.deleteFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected delete result.", deleted, is(false));
    verifyNoInteractions(eventBroadcastService);
  }

  @ParameterizedTest(name = "Should throw exception when deleting form with {0} state")
  @EnumSource(names = {"DRAFT"}, mode = EXCLUDE)
  void shouldThrowExceptionWhenDeletingNonDraftForm(LifecycleState state) {
    entity.setLifecycleState(state);

    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    assertThrows(IllegalArgumentException.class,
        () -> service.deleteFormRPartBById(DEFAULT_ID_STRING));
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldPartialDeleteFormRPartBById() throws MethodArgumentNotValidException {
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    Optional<FormRPartBDto> resultDto = service.partialDeleteFormRPartBById(DEFAULT_ID);

    assertThat("Unexpected DTO presence.", resultDto.isPresent(), is(true));

    FormRPartBDto dto = resultDto.get();
    assertThat("Unexpected ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected content.", dto.getContent(), nullValue());
    assertThat("Unexpected submission date.", dto.getSubmissionDate(),
        is(entity.getStatus().submitted().atZone(UTC).toLocalDateTime()));
    assertThat("Unexpected lifecycle state.", dto.getLifecycleState(), is(DELETED));

    verify(repositoryMock).save(any());
    verify(eventBroadcastService).publishFormRPartBEvent(any(), any(), any());
    verify(eventBroadcastService).publishFormrFileEvent(any());
  }

  @Test
  void shouldPublishEventWhenPartialDeletingFormRPartB() throws MethodArgumentNotValidException {
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).then(invocation -> invocation.getArgument(0));

    service.partialDeleteFormRPartBById(DEFAULT_ID);

    ArgumentCaptor<FormRPartBDto> dtoCaptor = ArgumentCaptor.forClass(FormRPartBDto.class);
    verify(eventBroadcastService).publishFormRPartBEvent(
        dtoCaptor.capture(), eq(Map.of("formType", "formr-b")), eq(FORM_R_PART_B_UPDATED_TOPIC));

    FormRPartBDto publishedDto = dtoCaptor.getValue();
    assertThat("Unexpected form ID.", publishedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected lifecycle state.", publishedDto.getLifecycleState(), is(DELETED));

    ArgumentCaptor<FormrFileEventDto> fileEventCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishFormrFileEvent(fileEventCaptor.capture());

    FormrFileEventDto fileEvent = fileEventCaptor.getValue();
    assertThat("Unexpected form name.", fileEvent.formName(), is(DEFAULT_ID_STRING + ".json"));
    assertThat("Unexpected trainee ID.", fileEvent.traineeId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected form type.", fileEvent.formType(), is("formr-b"));
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
  void shouldNotPartialDeleteWhenFormRPartBNotFoundInDb() throws MethodArgumentNotValidException {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.empty());

    service.partialDeleteFormRPartBById(DEFAULT_ID);

    verify(repositoryMock, never()).save(formRPartBCaptor.capture());
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldPublishEventWhenSavingSubmittedFormRPartB() {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));

    FormRPartBDto dto = mapper.toDto(entity);
    dto.setLifecycleState(SUBMITTED);

    when(repositoryMock.save(any())).thenAnswer(invocation -> {
      FormRPartB toSave = invocation.getArgument(0);
      FormRPartB savedEntity = new FormRPartB();
      BeanUtils.copyProperties(toSave, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    service.save(dto);

    ArgumentCaptor<FormRPartBDto> dtoCaptor = ArgumentCaptor.forClass(FormRPartBDto.class);
    verify(eventBroadcastService).publishFormRPartBEvent(
        dtoCaptor.capture(), eq(Map.of("formType", "formr-b")), eq(FORM_R_PART_B_UPDATED_TOPIC));

    FormRPartBDto publishedDto = dtoCaptor.getValue();
    assertThat("Unexpected form ID.", publishedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected lifecycle state.", publishedDto.getLifecycleState(), is(SUBMITTED));

    ArgumentCaptor<FormrFileEventDto> fileEventCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishFormrFileEvent(fileEventCaptor.capture());

    FormrFileEventDto fileEvent = fileEventCaptor.getValue();
    assertThat("Unexpected form name.", fileEvent.formName(), is(DEFAULT_ID_STRING + ".json"));
    assertThat("Unexpected trainee ID.", fileEvent.traineeId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected form type.", fileEvent.formType(), is("formr-b"));
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
  void shouldNotPublishEventWhenSavingNonSubmittedFormRPartB(LifecycleState state) {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    entity.setLifecycleState(state);

    FormRPartBDto dto = mapper.toDto(entity);

    when(repositoryMock.save(any())).thenAnswer(invocation -> {
      FormRPartB toSave = invocation.getArgument(0);
      FormRPartB savedEntity = new FormRPartB();
      BeanUtils.copyProperties(toSave, savedEntity);
      savedEntity.setId(DEFAULT_ID);
      return savedEntity;
    });

    service.save(dto);

    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldUnsubmitFormRPartBById() throws MethodArgumentNotValidException {
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<FormRPartBDto> resultDtoOptional = service.unsubmitFormRPartBById(DEFAULT_ID);

    assertThat("Unexpected DTO presence.", resultDtoOptional.isPresent(), is(true));
    FormRPartBDto resultDto = resultDtoOptional.get();

    assertThat("Unexpected Id.", resultDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected Trainee TIS Id.", resultDto.getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", resultDto.getContent().getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", resultDto.getContent().getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected lifecycle state.", resultDto.getLifecycleState(), is(UNSUBMITTED));

    verify(repositoryMock).save(any());
    verify(eventBroadcastService).publishFormRPartBEvent(any(), any(), any());
    verify(eventBroadcastService).publishFormrFileEvent(any());
  }

  @Test
  void shouldIncrementRevisionWhenUnsubmittingFormRPartB() throws MethodArgumentNotValidException {
    entity.setLifecycleState(SUBMITTED);
    entity.setRevision(4);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.unsubmitFormRPartBById(DEFAULT_ID);

    ArgumentCaptor<FormRPartB> formCaptor = ArgumentCaptor.captor();
    verify(repositoryMock).save(formCaptor.capture());

    FormRPartB savedForm = formCaptor.getValue();
    assertThat("Unexpected revision.", savedForm.getRevision(), is(5));
  }

  @Test
  void shouldPublishEventWhenUnsubmittingFormRPartB() throws MethodArgumentNotValidException {
    entity.setId(DEFAULT_ID);
    entity.setLifecycleState(SUBMITTED);
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.of(entity));
    when(repositoryMock.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.unsubmitFormRPartBById(DEFAULT_ID);

    ArgumentCaptor<FormRPartBDto> dtoCaptor = ArgumentCaptor.forClass(FormRPartBDto.class);
    verify(eventBroadcastService).publishFormRPartBEvent(
        dtoCaptor.capture(), eq(Map.of("formType", "formr-b")), eq(FORM_R_PART_B_UPDATED_TOPIC));

    FormRPartBDto publishedDto = dtoCaptor.getValue();
    assertThat("Unexpected form ID.", publishedDto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected lifecycle state.", publishedDto.getLifecycleState(), is(UNSUBMITTED));

    ArgumentCaptor<FormrFileEventDto> fileEventCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishFormrFileEvent(fileEventCaptor.capture());

    FormrFileEventDto fileEvent = fileEventCaptor.getValue();
    assertThat("Unexpected form name.", fileEvent.formName(), is(DEFAULT_ID_STRING + ".json"));
    assertThat("Unexpected trainee ID.", fileEvent.traineeId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected form type.", fileEvent.formType(), is("formr-b"));
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
  void shouldNotUnsubmitWhenFormRPartBNotFoundInDb() throws MethodArgumentNotValidException {
    when(repositoryMock.findById(DEFAULT_ID)).thenReturn(Optional.empty());

    service.unsubmitFormRPartBById(DEFAULT_ID);

    verify(repositoryMock, never()).save(formRPartBCaptor.capture());
    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldGetFormRPartBsByTraineeId() {

    List<FormRPartB> entities = Collections.singletonList(entity);

    when(repositoryMock.findNotDraftNorDeletedByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(entities);

    List<FormRPartSimpleDto> dtos = service.getFormRPartBs(DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected numbers of forms.", dtos.size(), is(entities.size()));

    FormRPartSimpleDto dto = dtos.get(0);
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected programme name.", dto.getProgrammeName(),
        is(DEFAULT_PROGRAMME_SPECIALTY));
    assertThat("Unexpected isArcp.", dto.getIsArcp(), is(true));
  }

  @Test
  void shouldGetAdminsFormRPartBByIdWhenUnsubmitted() {
    entity.setLifecycleState(UNSUBMITTED);

    when(repositoryMock.findByIdAndNotDraftNorDeleted(DEFAULT_ID))
        .thenReturn(Optional.of(entity));

    Optional<FormRPartBDto> optionalDto = service.getAdminsFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Unexpected DTO.", optionalDto.isPresent(), is(true));
    FormRPartBDto dto = optionalDto.get();

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID_STRING));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected lifecycle state.", dto.getLifecycleState(), is(UNSUBMITTED));
  }

  @Test
  void shouldReturnEmptyWhenAdminsFormRPartBNotFound() {
    when(repositoryMock.findByIdAndNotDraftNorDeleted(DEFAULT_ID))
        .thenReturn(Optional.empty());

    Optional<FormRPartBDto> optionalDto = service.getAdminsFormRPartBById(DEFAULT_ID_STRING);

    assertThat("Expected empty for non-existent form.", optionalDto.isEmpty(), is(true));
  }

  @Test
  void shouldReturnEmptyListWhenNoFormRPartBsFoundForTraineeId() {
    String traineeId = "99999";

    when(repositoryMock.findNotDraftNorDeletedByTraineeTisId(traineeId))
        .thenReturn(new ArrayList<>());

    List<FormRPartSimpleDto> dtos = service.getFormRPartBs(traineeId);

    assertThat("Unexpected numbers of forms.", dtos.size(), is(0));
  }

  @Test
  void shouldPublishUpdateNotification() {
    FormRPartBDto form = new FormRPartBDto();
    form.setId(UUID.randomUUID().toString());

    service.publishUpdateNotification(form, "my-topic");

    verify(eventBroadcastService).publishFormRPartBEvent(form,
        Map.of(EventBroadcastService.MESSAGE_ATTRIBUTE_KEY_FORM_TYPE, "formr-b"), "my-topic");
    verify(eventBroadcastService, never()).publishFormrFileEvent(any());
  }
}
