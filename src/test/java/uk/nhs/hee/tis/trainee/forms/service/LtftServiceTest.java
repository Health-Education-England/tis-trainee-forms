/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctChangeDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.DeclarationsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.DiscussionsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.ProgrammeMembershipDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.ReasonsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.StatusInfoDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonalDetailsDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.TemporalMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChangeType;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Declarations;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Discussions;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.PersonalDetails;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.ProgrammeMembership;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Reasons;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;

class LtftServiceTest {

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_EMAIL = "email";
  private static final String TRAINEE_NAME = "name";
  private static final String TRAINEE_ROLE = "TRAINEE";

  private static final String ADMIN_NAME = "Ad Min";
  private static final String ADMIN_EMAIL = "ad.min@example.com";
  private static final String ADMIN_GROUP = "abc-123";
  private static final UUID ID = UUID.randomUUID();

  private LtftService service;
  private LtftFormRepository ltftRepository;
  private LtftMapper mapper;

  @BeforeEach
  void setUp() {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setName(ADMIN_NAME);
    adminIdentity.setEmail(ADMIN_EMAIL);
    adminIdentity.setGroups(Set.of(ADMIN_GROUP));

    TraineeIdentity traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(TRAINEE_ID);
    traineeIdentity.setEmail(TRAINEE_EMAIL);
    traineeIdentity.setName(TRAINEE_NAME);

    ltftRepository = mock(LtftFormRepository.class);

    mapper = new LtftMapperImpl(new TemporalMapperImpl());
    service = new LtftService(adminIdentity, traineeIdentity, ltftRepository, mapper);
  }

  @Test
  void shouldReturnEmptyGettingLtftFormSummariesWhenNotFound() {
    when(ltftRepository.findByTraineeTisIdOrderByLastModified(TRAINEE_ID)).thenReturn(
        List.of());

    List<LtftSummaryDto> result = service.getLtftSummaries();

    assertThat("Unexpected LTFT summaries count.", result.size(), is(0));
  }

  @Test
  void shouldGetLtftFormSummariesWhenFound() {
    UUID ltftId1 = UUID.randomUUID();
    UUID pmId1 = UUID.randomUUID();
    Instant created1 = Instant.now().minus(Duration.ofDays(1));
    Instant lastModified1 = Instant.now().plus(Duration.ofDays(1));

    LtftForm entity1 = new LtftForm();
    entity1.setId(ltftId1);
    entity1.setTraineeTisId(TRAINEE_ID);

    LtftContent content1 = LtftContent.builder()
        .name("Test LTFT form 1")
        .programmeMembership(ProgrammeMembership.builder()
            .id(pmId1)
            .build())
        .discussions(Discussions.builder()
            .tpdName("tpd")
            .other(List.of(Person.builder()
                .name("other")
                .build()))
            .build())
        .build();
    entity1.setContent(content1);
    entity1.setCreated(created1);
    entity1.setLastModified(lastModified1);

    UUID ltftId2 = UUID.randomUUID();
    UUID pmId2 = UUID.randomUUID();
    Instant created2 = Instant.now().minus(Duration.ofDays(2));
    Instant lastModified2 = Instant.now().plus(Duration.ofDays(2));

    LtftForm entity2 = new LtftForm();
    entity2.setId(ltftId2);
    entity2.setTraineeTisId(TRAINEE_ID);

    LtftContent content2 = LtftContent.builder()
        .name("Test LTFT form 2")
        .programmeMembership(ProgrammeMembership.builder()
            .id(pmId2)
            .build())
        .build();
    entity2.setContent(content2);
    entity2.setCreated(created2);
    entity2.setLastModified(lastModified2);

    when(ltftRepository.findByTraineeTisIdOrderByLastModified(TRAINEE_ID)).thenReturn(
        List.of(entity1, entity2));

    List<LtftSummaryDto> result = service.getLtftSummaries();

    assertThat("Unexpected LTFT form summary count.", result.size(), is(2));

    LtftSummaryDto dto1 = result.get(0);
    assertThat("Unexpected LTFT form ID.", dto1.id(), is(ltftId1));
    assertThat("Unexpected LTFT name.", dto1.name(), is("Test LTFT form 1"));
    assertThat("Unexpected PM ID.", dto1.programmeMembershipId(), is(pmId1));
    assertThat("Unexpected created timestamp.", dto1.created(), is(created1));
    assertThat("Unexpected last modified timestamp.", dto1.lastModified(), is(lastModified1));

    LtftSummaryDto dto2 = result.get(1);
    assertThat("Unexpected LTFT form ID.", dto2.id(), is(ltftId2));
    assertThat("Unexpected LTFT name.", dto2.name(), is("Test LTFT form 2"));
    assertThat("Unexpected PM ID.", dto2.programmeMembershipId(), is(pmId2));
    assertThat("Unexpected created timestamp.", dto2.created(), is(created2));
    assertThat("Unexpected last modified timestamp.", dto2.lastModified(), is(lastModified2));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldCountAllNonDraftAdminLtftsWhenFiltersEmpty(Set<LifecycleState> states) {
    when(ltftRepository
        .countByStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            Set.of(DRAFT), Set.of(ADMIN_GROUP))).thenReturn(40L);

    long count = service.getAdminLtftCount(states);

    assertThat("Unexpected count.", count, is(40L));
    verify(ltftRepository, never()).count();
    verify(ltftRepository, never())
        .countByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(any(),
            any());
  }

  @Test
  void shouldCountFilteredAdminLtftsWhenFiltersNotEmpty() {
    Set<LifecycleState> states = Set.of(SUBMITTED);
    when(ltftRepository
        .countByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(states,
            Set.of(ADMIN_GROUP))).thenReturn(40L);

    long count = service.getAdminLtftCount(states);

    assertThat("Unexpected count.", count, is(40L));
    verify(ltftRepository, never()).count();
    verify(ltftRepository, never())
        .countByStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any());
  }

  @Test
  void shouldNotCountDraftAdminLtftsWhenFiltersNotEmpty() {
    Set<LifecycleState> states = Set.of(DRAFT, SUBMITTED);

    service.getAdminLtftCount(states);

    ArgumentCaptor<Set<LifecycleState>> statesCaptor = ArgumentCaptor.captor();
    verify(ltftRepository)
        .countByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            statesCaptor.capture(), any());

    Set<LifecycleState> filteredStates = statesCaptor.getValue();
    assertThat("Unexpected state count.", filteredStates, hasSize(1));
    assertThat("Unexpected states.", filteredStates, hasItems(SUBMITTED));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldGetAllNonDraftAdminLtftsWhenFiltersEmpty(Set<LifecycleState> states) {
    PageRequest pageRequest = PageRequest.of(1, 1);

    LtftForm entity1 = new LtftForm();
    UUID id1 = UUID.randomUUID();
    entity1.setId(id1);

    LtftForm entity2 = new LtftForm();
    UUID id2 = UUID.randomUUID();
    entity2.setId(id2);

    when(ltftRepository
        .findByStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            Set.of(DRAFT), Set.of(ADMIN_GROUP), pageRequest))
        .thenReturn(new PageImpl<>(List.of(entity1, entity2)));

    Page<LtftAdminSummaryDto> dtos = service.getAdminLtftSummaries(states, pageRequest);

    assertThat("Unexpected total elements.", dtos.getTotalElements(), is(2L));

    List<LtftAdminSummaryDto> content = dtos.getContent();
    assertThat("Unexpected ID.", content.get(0).id(), is(id1));
    assertThat("Unexpected ID.", content.get(1).id(), is(id2));

    verify(ltftRepository,
        never()).findByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        any(), any(), any());
  }

  @Test
  void shouldGetFilteredAdminLtftsWhenFiltersNotEmpty() {
    Set<LifecycleState> states = Set.of(SUBMITTED);
    PageRequest pageRequest = PageRequest.of(1, 1);

    LtftForm entity1 = new LtftForm();
    UUID id1 = UUID.randomUUID();
    entity1.setId(id1);

    LtftForm entity2 = new LtftForm();
    UUID id2 = UUID.randomUUID();
    entity2.setId(id2);

    when(ltftRepository
        .findByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(states,
            Set.of(ADMIN_GROUP), pageRequest))
        .thenReturn(new PageImpl<>(List.of(entity1, entity2)));

    Page<LtftAdminSummaryDto> dtos = service.getAdminLtftSummaries(states, pageRequest);

    assertThat("Unexpected total elements.", dtos.getTotalElements(), is(2L));

    List<LtftAdminSummaryDto> content = dtos.getContent();
    assertThat("Unexpected ID.", content.get(0).id(), is(id1));
    assertThat("Unexpected ID.", content.get(1).id(), is(id2));

    verify(ltftRepository, never())
        .findByStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any());
  }

  @Test
  void shouldNotGetDraftAdminLtftsWhenFiltersNotEmpty() {
    Set<LifecycleState> states = Set.of(DRAFT, SUBMITTED);
    PageRequest pageRequest = PageRequest.of(1, 1);

    when(ltftRepository
        .findByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(any(),
            any(), any())).thenReturn(new PageImpl<>(List.of()));

    service.getAdminLtftSummaries(states, pageRequest);

    ArgumentCaptor<Set<LifecycleState>> statesCaptor = ArgumentCaptor.captor();
    verify(ltftRepository)
        .findByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            statesCaptor.capture(), any(), any());

    Set<LifecycleState> filteredStates = statesCaptor.getValue();
    assertThat("Unexpected state count.", filteredStates, hasSize(1));
    assertThat("Unexpected states.", filteredStates, hasItems(SUBMITTED));
  }

  @Test
  void shouldGetAdminLtftDetailWithFormId() {
    service.getAdminLtftDetail(ID);

    verify(ltftRepository)
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            eq(ID), any(), any());
  }

  @Test
  void shouldGetAdminLtftDetailWithDraftExcluded() {
    service.getAdminLtftDetail(ID);

    verify(ltftRepository)
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), eq(Set.of(DRAFT)), any());
  }

  @Test
  void shouldGetAdminLtftDetailWithAdminDbcs() {
    service.getAdminLtftDetail(ID);

    verify(ltftRepository)
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), eq(Set.of(ADMIN_GROUP)));
  }

  @Test
  void shouldGetEmptyAdminLtftDetailWhenFormNotFound() {
    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.empty());

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(false));
  }

  @Test
  void shouldGetAdminLtftDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setTraineeTisId(TRAINEE_ID);
    entity.setFormRef("LTFT_test_ref");
    entity.setRevision(1);
    entity.setCreated(Instant.MIN);
    entity.setLastModified(Instant.MAX);

    LtftContent content = LtftContent.builder()
        .name("Test LTFT")
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    assertThat("Unexpected ID.", dto.id(), is(ID));
    assertThat("Unexpected trainee ID.", dto.traineeTisId(), is(TRAINEE_ID));
    assertThat("Unexpected form ref.", dto.formRef(), is("LTFT_test_ref"));
    assertThat("Unexpected revision.", dto.revision(), is(1));
    assertThat("Unexpected name.", dto.name(), is("Test LTFT"));
    assertThat("Unexpected created.", dto.created(), is(Instant.MIN));
    assertThat("Unexpected lastModified.", dto.lastModified(), is(Instant.MAX));
  }

  @Test
  void shouldGetAdminLtftDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setContent(LtftContent.builder().build());

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    assertThat("Unexpected ID.", dto.id(), is(ID));
    assertThat("Unexpected trainee ID.", dto.traineeTisId(), nullValue());
    assertThat("Unexpected form ref.", dto.formRef(), nullValue());
    assertThat("Unexpected revision.", dto.revision(), is(0));
    assertThat("Unexpected name.", dto.name(), nullValue());
    assertThat("Unexpected created.", dto.created(), nullValue());
    assertThat("Unexpected lastModified.", dto.lastModified(), nullValue());
  }

  @Test
  void shouldGetAdminLtftPersonalDetailsDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setTraineeTisId(TRAINEE_ID);

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder()
            .title("Dr")
            .forenames("Anthony")
            .surname("Gilliam")
            .email("anthony.gilliam@example.com")
            .telephoneNumber("07700900000")
            .mobileNumber("07700900001")
            .gmcNumber("1234567")
            .gdcNumber("D123456")
            .skilledWorkerVisaHolder(true)
            .build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    PersonalDetailsDto personalDetails = dto.personalDetails();
    assertThat("Unexpected ID.", personalDetails.id(), is(TRAINEE_ID));
    assertThat("Unexpected title.", personalDetails.title(), is("Dr"));
    assertThat("Unexpected forenames.", personalDetails.forenames(), is("Anthony"));
    assertThat("Unexpected surname.", personalDetails.surname(), is("Gilliam"));
    assertThat("Unexpected email.", personalDetails.email(), is("anthony.gilliam@example.com"));
    assertThat("Unexpected telephone number.", personalDetails.telephoneNumber(),
        is("07700900000"));
    assertThat("Unexpected mobile number.", personalDetails.mobileNumber(), is("07700900001"));
    assertThat("Unexpected GMC number.", personalDetails.gmcNumber(), is("1234567"));
    assertThat("Unexpected GDC number.", personalDetails.gdcNumber(), is("D123456"));
    assertThat("Unexpected visa flag.", personalDetails.skilledWorkerVisaHolder(), is(true));
  }

  @Test
  void shouldGetAdminLtftPersonalDetailsDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder().build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    PersonalDetailsDto personalDetails = dto.personalDetails();
    assertThat("Unexpected ID.", personalDetails.id(), nullValue());
    assertThat("Unexpected title.", personalDetails.title(), nullValue());
    assertThat("Unexpected forenames.", personalDetails.forenames(), nullValue());
    assertThat("Unexpected surname.", personalDetails.surname(), nullValue());
    assertThat("Unexpected email.", personalDetails.email(), nullValue());
    assertThat("Unexpected telephone number.", personalDetails.telephoneNumber(), nullValue());
    assertThat("Unexpected mobile number.", personalDetails.mobileNumber(), nullValue());
    assertThat("Unexpected GMC number.", personalDetails.gmcNumber(), nullValue());
    assertThat("Unexpected GDC number.", personalDetails.gdcNumber(), nullValue());
    assertThat("Unexpected visa flag.", personalDetails.skilledWorkerVisaHolder(), nullValue());
  }

  @Test
  void shouldGetAdminLtftProgrammeMembershipDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    UUID pmId = UUID.randomUUID();

    LtftContent content = LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .id(pmId)
            .name("Test PM")
            .designatedBodyCode("1-1DBC")
            .startDate(LocalDate.MIN)
            .endDate(LocalDate.MAX)
            .wte(0.75)
            .build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    ProgrammeMembershipDto programmeMembership = dto.programmeMembership();
    assertThat("Unexpected PM ID.", programmeMembership.id(), is(pmId));
    assertThat("Unexpected PM name.", programmeMembership.name(), is("Test PM"));
    assertThat("Unexpected PM DBC.", programmeMembership.designatedBodyCode(), is("1-1DBC"));
    assertThat("Unexpected PM start date.", programmeMembership.startDate(), is(LocalDate.MIN));
    assertThat("Unexpected PM end date.", programmeMembership.endDate(), is(LocalDate.MAX));
    assertThat("Unexpected PM wte.", programmeMembership.wte(), is(0.75));
  }

  @Test
  void shouldGetAdminLtftProgrammeMembershipDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder().build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    ProgrammeMembershipDto programmeMembership = dto.programmeMembership();
    assertThat("Unexpected PM ID.", programmeMembership.id(), nullValue());
    assertThat("Unexpected PM name.", programmeMembership.name(), nullValue());
    assertThat("Unexpected PM DBC.", programmeMembership.designatedBodyCode(), nullValue());
    assertThat("Unexpected PM start date.", programmeMembership.startDate(), nullValue());
    assertThat("Unexpected PM end date.", programmeMembership.endDate(), nullValue());
    assertThat("Unexpected PM wte.", programmeMembership.wte(), nullValue());
  }

  @Test
  void shouldGetAdminLtftDeclarationsDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .declarations(Declarations.builder()
            .discussedWithTpd(true)
            .notGuaranteed(false)
            .informationIsCorrect(true)
            .build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    DeclarationsDto declarations = dto.declarations();
    assertThat("Unexpected declaration.", declarations.discussedWithTpd(), is(true));
    assertThat("Unexpected declaration.", declarations.notGuaranteed(), is(false));
    assertThat("Unexpected declaration.", declarations.informationIsCorrect(), is(true));
  }

  @Test
  void shouldGetAdminLtftDeclarationsDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .declarations(Declarations.builder().build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    DeclarationsDto declarations = dto.declarations();
    assertThat("Unexpected declaration.", declarations.discussedWithTpd(), nullValue());
    assertThat("Unexpected declaration.", declarations.notGuaranteed(), nullValue());
    assertThat("Unexpected declaration.", declarations.informationIsCorrect(), nullValue());
  }

  @Test
  void shouldGetAdminLtftDiscussionDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .discussions(Discussions.builder()
            .tpdName("Tee Pee-Dee")
            .tpdEmail("t.pd@example.com")
            .other(List.of(
                Person.builder().name("Other 1").email("other.1@example.com").role("Role 1")
                    .build(),
                Person.builder().name("Other 2").email("other.2@example.com").role("Role 2").build()
            ))
            .build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    DiscussionsDto discussions = dto.discussions();
    assertThat("Unexpected TPD name.", discussions.tpdName(), is("Tee Pee-Dee"));
    assertThat("Unexpected TPD email.", discussions.tpdEmail(), is("t.pd@example.com"));
    assertThat("Unexpected other discussion count.", discussions.other(), hasSize(2));

    PersonDto discussion1 = discussions.other().get(0);
    assertThat("Unexpected discussion name.", discussion1.name(), is("Other 1"));
    assertThat("Unexpected discussion email.", discussion1.email(), is("other.1@example.com"));
    assertThat("Unexpected discussion role.", discussion1.role(), is("Role 1"));

    PersonDto discussion2 = discussions.other().get(1);
    assertThat("Unexpected discussion name.", discussion2.name(), is("Other 2"));
    assertThat("Unexpected discussion email.", discussion2.email(), is("other.2@example.com"));
    assertThat("Unexpected discussion role.", discussion2.role(), is("Role 2"));
  }

  @Test
  void shouldGetAdminLtftDiscussionDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .discussions(Discussions.builder().build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    DiscussionsDto discussions = dto.discussions();
    assertThat("Unexpected TPD name.", discussions.tpdName(), nullValue());
    assertThat("Unexpected TPD email.", discussions.tpdEmail(), nullValue());
    assertThat("Unexpected other discussion count.", discussions.other(), nullValue());
  }

  @Test
  void shouldGetAdminLtftChangeDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    UUID changeId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();

    LtftContent content = LtftContent.builder()
        .change(CctChange.builder()
            .id(changeId)
            .calculationId(calculationId)
            .type(CctChangeType.LTFT)
            .wte(0.5)
            .startDate(LocalDate.MIN)
            .endDate(LocalDate.EPOCH)
            .cctDate(LocalDate.MAX)
            .build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    CctChangeDto change = dto.change();
    assertThat("Unexpected ID.", change.id(), is(changeId));
    assertThat("Unexpected calculation ID.", change.calculationId(), is(calculationId));
    assertThat("Unexpected type.", change.type(), is(CctChangeType.LTFT));
    assertThat("Unexpected WTE.", change.wte(), is(0.5));
    assertThat("Unexpected start date.", change.startDate(), is(LocalDate.MIN));
    assertThat("Unexpected end date.", change.endDate(), is(LocalDate.EPOCH));
    assertThat("Unexpected CCT date.", change.cctDate(), is(LocalDate.MAX));
  }

  @Test
  void shouldGetAdminLtftChangeDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .change(CctChange.builder().build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    CctChangeDto change = dto.change();
    assertThat("Unexpected ID.", change.id(), nullValue());
    assertThat("Unexpected calculation ID.", change.calculationId(), nullValue());
    assertThat("Unexpected type.", change.type(), nullValue());
    assertThat("Unexpected WTE.", change.wte(), nullValue());
    assertThat("Unexpected start date.", change.startDate(), nullValue());
    assertThat("Unexpected end date.", change.endDate(), nullValue());
    assertThat("Unexpected CCT date.", change.cctDate(), nullValue());
  }

  @Test
  void shouldGetAdminLtftReasonsDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .reasons(Reasons.builder()
            .selected(List.of("Test Reason 2", "Test Reason 1", "Test Reason 3"))
            .otherDetail("Other Detail")
            .build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    ReasonsDto reasons = dto.reasons();
    assertThat("Unexpected reason count.", reasons.selected(), hasSize(3));
    assertThat("Unexpected reason.", reasons.selected().get(0), is("Test Reason 2"));
    assertThat("Unexpected reason.", reasons.selected().get(1), is("Test Reason 1"));
    assertThat("Unexpected reason.", reasons.selected().get(2), is("Test Reason 3"));
    assertThat("Unexpected reason detail.", reasons.otherDetail(), is("Other Detail"));
  }

  @Test
  void shouldGetAdminLtftReasonsDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .reasons(Reasons.builder().build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    ReasonsDto reasons = dto.reasons();
    assertThat("Unexpected reason count.", reasons.selected(), nullValue());
    assertThat("Unexpected reason detail.", reasons.otherDetail(), nullValue());
  }

  @Test
  void shouldGetAdminLtftAssignedAdminDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .assignedAdmin(Person.builder()
            .name(ADMIN_NAME)
            .email(ADMIN_EMAIL)
            .role("ADMIN")
            .build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    PersonDto assignedAdmin = dto.assignedAdmin();
    assertThat("Unexpected admin name.", assignedAdmin.name(), is(ADMIN_NAME));
    assertThat("Unexpected admin email.", assignedAdmin.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected admin role.", assignedAdmin.role(), is("ADMIN"));
  }

  @Test
  void shouldGetAdminLtftAssignedAdminDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .assignedAdmin(Person.builder().build())
        .build();
    entity.setContent(content);

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    PersonDto assignedAdmin = dto.assignedAdmin();
    assertThat("Unexpected admin name.", assignedAdmin.name(), nullValue());
    assertThat("Unexpected admin email.", assignedAdmin.email(), nullValue());
    assertThat("Unexpected admin role.", assignedAdmin.role(), nullValue());
  }

  @Test
  void shouldGetAdminLtftStatusDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setContent(LtftContent.builder().build());

    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(SUBMITTED)
            .detail(AbstractAuditedForm.Status.StatusDetail.builder()
                .reason("Submitted Detail")
                .build())
            .timestamp(Instant.EPOCH)
            .revision(1)
            .modifiedBy(Person.builder()
                .name("Anthony Gilliam")
                .email("anthony.gilliam@example.com")
                .role("TRAINEE")
                .build())
            .build())
        .history(List.of(
            StatusInfo.builder()
                .state(DRAFT)
                .detail(AbstractAuditedForm.Status.StatusDetail.builder()
                    .reason("Draft Detail")
                    .build())
                .timestamp(Instant.MIN)
                .revision(0)
                .modifiedBy(Person.builder()
                    .name("Anthony Gilliam")
                    .email("anthony.gilliam@example.com")
                    .role("TRAINEE")
                    .build())
                .build(),
            StatusInfo.builder()
                .state(SUBMITTED)
                .detail(AbstractAuditedForm.Status.StatusDetail.builder()
                    .reason("Submitted Detail")
                    .build())
                .timestamp(Instant.EPOCH)
                .revision(1)
                .modifiedBy(Person.builder()
                    .name("Anthony Gilliam")
                    .email("anthony.gilliam@example.com")
                    .role("TRAINEE")
                    .build())
                .build()
        ))
        .build());

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    StatusDto status = dto.status();
    StatusInfoDto currentStatus = status.current();
    assertThat("Unexpected state.", currentStatus.state(), is(SUBMITTED));
    assertThat("Unexpected detail.", currentStatus.detail().reason(), is("Submitted Detail"));
    assertThat("Unexpected timestamp.", currentStatus.timestamp(), is(Instant.EPOCH));
    assertThat("Unexpected revision.", currentStatus.revision(), is(1));

    PersonDto modifiedBy = currentStatus.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is("Anthony Gilliam"));
    assertThat("Unexpected modified email.", modifiedBy.email(), is("anthony.gilliam@example.com"));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("TRAINEE"));

    List<StatusInfoDto> statusHistory = status.history();
    assertThat("Unexpected history count.", statusHistory, hasSize(2));

    StatusInfoDto history1 = statusHistory.get(0);
    assertThat("Unexpected state.", history1.state(), is(DRAFT));
    assertThat("Unexpected detail.", history1.detail().reason(), is("Draft Detail"));
    assertThat("Unexpected timestamp.", history1.timestamp(), is(Instant.MIN));
    assertThat("Unexpected revision.", history1.revision(), is(0));

    modifiedBy = history1.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is("Anthony Gilliam"));
    assertThat("Unexpected modified email.", modifiedBy.email(), is("anthony.gilliam@example.com"));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("TRAINEE"));

    StatusInfoDto history2 = statusHistory.get(1);
    assertThat("Unexpected state.", history2.state(), is(SUBMITTED));
    assertThat("Unexpected detail.", history2.detail().reason(), is("Submitted Detail"));
    assertThat("Unexpected timestamp.", history2.timestamp(), is(Instant.EPOCH));
    assertThat("Unexpected revision.", history2.revision(), is(1));

    modifiedBy = history2.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is("Anthony Gilliam"));
    assertThat("Unexpected modified email.", modifiedBy.email(), is("anthony.gilliam@example.com"));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("TRAINEE"));
  }

  @Test
  void shouldGetAdminLtftStatusDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setContent(LtftContent.builder().build());

    entity.setStatus(Status.builder().build());

    when(ltftRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    StatusDto status = dto.status();
    assertThat("Unexpected state.", status.current(), nullValue());
    assertThat("Unexpected history count.", status.history(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldReturnEmptyUpdatingStatusWhenFormNotFound(LifecycleState state)
      throws MethodArgumentNotValidException {
    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.empty());

    Optional<LtftFormDto> form = service.updateStatusAsAdmin(ID, state, null);

    assertThat("Unexpected form presence.", form.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = "SUBMITTED")
  void shouldThrowExceptionUpdatingStatusWhenTransitionFromDraftInvalid(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(DRAFT);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState, null));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("LtftForm"));
    assertThat("Unexpected field.", fieldError.getField(), is("status.current.state"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("can not be transitioned to " + targetState.name()));

    verify(ltftRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = "SUBMITTED")
  void shouldUpdateStatusWhenTransitionFromDraftValid(
      LifecycleState targetState) throws MethodArgumentNotValidException {
    LtftForm entity = new LtftForm();
    entity.setLifecycleState(DRAFT);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(entity));
    when(ltftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> optionalDto = service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder()
            .reason("detail reason")
            .message("detail message")
            .build()
    );

    assertThat("Unexpected form presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();

    StatusInfoDto current = dto.status().current();
    assertThat("Unexpected current state.", current.state(), is(targetState));
    assertThat("Unexpected current revision.", current.revision(), is(0));
    assertThat("Unexpected current timestamp.", current.timestamp(), notNullValue());

    LftfStatusInfoDetailDto detail = current.detail();
    assertThat("Unexpected current reason.", detail.reason(), is("detail reason"));
    assertThat("Unexpected current message.", detail.message(), is("detail message"));

    PersonDto modifiedBy = current.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is(ADMIN_NAME));
    assertThat("Unexpected modified email.", modifiedBy.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfoDto> history = dto.status().history();
    assertThat("Unexpected history count.", history, hasSize(2));
    assertThat("Unexpected history state.", history.get(0).state(), is(DRAFT));
    assertThat("Unexpected history state.", history.get(1).state(), is(targetState));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"APPROVED", "REJECTED",
      "UNSUBMITTED", "WITHDRAWN"})
  void shouldThrowExceptionUpdatingStatusWhenTransitionFromSubmittedInvalid(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(SUBMITTED);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState, null));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("LtftForm"));
    assertThat("Unexpected field.", fieldError.getField(), is("status.current.state"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("can not be transitioned to " + targetState.name()));

    verify(ltftRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"APPROVED", "REJECTED",
      "UNSUBMITTED", "WITHDRAWN"})
  void shouldUpdateStatusWhenTransitionFromSubmittedValid(
      LifecycleState targetState) throws MethodArgumentNotValidException {
    LtftForm entity = new LtftForm();
    entity.setLifecycleState(SUBMITTED);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(entity));
    when(ltftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> optionalDto = service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder()
            .reason("detail reason")
            .message("detail message")
            .build()
    );

    assertThat("Unexpected form presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();

    StatusInfoDto current = dto.status().current();
    assertThat("Unexpected current state.", current.state(), is(targetState));
    assertThat("Unexpected current revision.", current.revision(), is(0));
    assertThat("Unexpected current timestamp.", current.timestamp(), notNullValue());

    LftfStatusInfoDetailDto detail = current.detail();
    assertThat("Unexpected current reason.", detail.reason(), is("detail reason"));
    assertThat("Unexpected current message.", detail.message(), is("detail message"));

    PersonDto modifiedBy = current.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is(ADMIN_NAME));
    assertThat("Unexpected modified email.", modifiedBy.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfoDto> history = dto.status().history();
    assertThat("Unexpected history count.", history, hasSize(2));
    assertThat("Unexpected history state.", history.get(0).state(), is(SUBMITTED));
    assertThat("Unexpected history state.", history.get(1).state(), is(targetState));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"SUBMITTED", "WITHDRAWN"})
  void shouldThrowExceptionUpdatingStatusWhenTransitionFromUnsubmittedInvalid(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(UNSUBMITTED);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState, null));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("LtftForm"));
    assertThat("Unexpected field.", fieldError.getField(), is("status.current.state"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("can not be transitioned to " + targetState.name()));

    verify(ltftRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"SUBMITTED", "WITHDRAWN"})
  void shouldUpdateStatusWhenTransitionFromUnsubmittedValid(
      LifecycleState targetState) throws MethodArgumentNotValidException {
    LtftForm entity = new LtftForm();
    entity.setLifecycleState(UNSUBMITTED);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(entity));
    when(ltftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> optionalDto = service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder()
            .reason("detail reason")
            .message("detail message")
            .build()
    );

    assertThat("Unexpected form presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();

    StatusInfoDto current = dto.status().current();
    assertThat("Unexpected current state.", current.state(), is(targetState));
    assertThat("Unexpected current revision.", current.revision(), is(0));
    assertThat("Unexpected current timestamp.", current.timestamp(), notNullValue());

    LftfStatusInfoDetailDto detail = current.detail();
    assertThat("Unexpected current reason.", detail.reason(), is("detail reason"));
    assertThat("Unexpected current message.", detail.message(), is("detail message"));

    PersonDto modifiedBy = current.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is(ADMIN_NAME));
    assertThat("Unexpected modified email.", modifiedBy.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfoDto> history = dto.status().history();
    assertThat("Unexpected history count.", history, hasSize(2));
    assertThat("Unexpected history state.", history.get(0).state(), is(UNSUBMITTED));
    assertThat("Unexpected history state.", history.get(1), is(current));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"DRAFT", "SUBMITTED",
      "UNSUBMITTED"})
  void shouldThrowExceptionUpdatingStatusWhenTransitionFromFinalState(LifecycleState currentState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(currentState);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    Arrays.stream(LifecycleState.values()).forEach(targetState -> {
      MethodArgumentNotValidException exception = assertThrows(
          MethodArgumentNotValidException.class,
          () -> service.updateStatusAsAdmin(ID, targetState, LftfStatusInfoDetailDto.builder()
              .reason("detail reason")
              .message("detail message")
              .build()
          ));

      List<FieldError> fieldErrors = exception.getFieldErrors();
      assertThat("Unexpected error count.", fieldErrors, hasSize(1));

      FieldError fieldError = fieldErrors.get(0);
      assertThat("Unexpected object name.", fieldError.getObjectName(), is("LtftForm"));
      assertThat("Unexpected field.", fieldError.getField(), is("status.current.state"));
      assertThat("Unexpected message.", fieldError.getDefaultMessage(),
          is("can not be transitioned to " + targetState.name()));
    });

    verify(ltftRepository, never()).save(any());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      DRAFT | SUBMITTED
      SUBMITTED | APPROVED
      """)
  void shouldNotThrowExceptionUpdatingStatusWhenOptionalStatusDetailNull(
      LifecycleState currentState, LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(currentState);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(ltftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertDoesNotThrow(() -> service.updateStatusAsAdmin(ID, targetState, null));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      DRAFT | SUBMITTED
      SUBMITTED | APPROVED
      """)
  void shouldNotThrowExceptionUpdatingStatusWhenOptionalStatusDetailReasonNull(
      LifecycleState currentState, LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(currentState);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(ltftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertDoesNotThrow(() -> service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder().build()));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"REJECTED", "UNSUBMITTED",
      "WITHDRAWN"})
  void shouldThrowExceptionUpdatingStatusWhenRequiredStatusDetailNull(LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(SUBMITTED);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState, null));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("StatusInfo"));
    assertThat("Unexpected field.", fieldError.getField(), is("detail"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("must not be null when transitioning to " + targetState.name()));

    verify(ltftRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"REJECTED", "UNSUBMITTED",
      "WITHDRAWN"})
  void shouldThrowExceptionUpdatingStatusWhenRequiredStatusDetailReasonNull(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(SUBMITTED);

    when(ltftRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState,
            LftfStatusInfoDetailDto.builder().build()));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("StatusInfo"));
    assertThat("Unexpected field.", fieldError.getField(), is("detail.reason"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("must not be null when transitioning to " + targetState.name()));

    verify(ltftRepository, never()).save(any());
  }

  @Test
  void shouldReturnEmptyIfLtftFormNotFound() {
    when(ltftRepository.findByTraineeTisIdAndId(any(), any())).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verify(ltftRepository).findByTraineeTisIdAndId(any(), eq(ID));
    verifyNoMoreInteractions(ltftRepository);
  }

  @Test
  void shouldReturnEmptyIfLtftFormForTraineeNotFound() {
    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected form returned.", formDtoOptional, is(Optional.empty()));
    verify(ltftRepository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(ltftRepository);
  }

  @Test
  void shouldReturnDtoIfLtftFormForTraineeFound() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setContent(LtftContent.builder().name("test").build());
    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID))
        .thenReturn(Optional.of(form));

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected empty form returned.", formDtoOptional.isPresent(), is(true));
    verify(ltftRepository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    LtftFormDto returnedFormDto = formDtoOptional.get();
    assertThat("Unexpected returned LTFT form.", returnedFormDto, is(mapper.toDto(form)));
  }

  @Test
  void shouldNotSaveIfNewLtftFormNotForTrainee() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId("another trainee")
        .build();

    Optional<LtftFormDto> formDtoOptional = service.saveLtftForm(dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldSaveIfNewLtftFormForTrainee() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setContent(LtftContent.builder().name("test").build());
    when(ltftRepository.save(any())).thenReturn(existingForm);

    Optional<LtftFormDto> formDtoOptional = service.saveLtftForm(dtoToSave);

    verify(ltftRepository).save(any());
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));
  }

  @Test
  void shouldNotUpdateFormIfWithoutId() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .build();

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfIdDoesNotMatchPathParameter() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();

    Optional<LtftFormDto> formDtoOptional
        = service.updateLtftForm(UUID.randomUUID(), dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfTraineeDoesNotMatchLoggedInUser() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("another trainee")
        .build();

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfExistingFormNotFound() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID))
        .thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verify(ltftRepository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(ltftRepository);
  }

  @Test
  void shouldSaveIfUpdatingLtftFormForTrainee() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setContent(LtftContent.builder().name("test").build());
    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID))
        .thenReturn(Optional.of(existingForm));
    when(ltftRepository.save(any())).thenReturn(existingForm);

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    LtftForm formToSave = mapper.toEntity(dtoToSave);
    verify(ltftRepository).save(formToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));
  }

  @Test
  void shouldReturnEmptyWhenDeletingIfFormNotFound() {
    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<Boolean> result = service.deleteLtftForm(ID);

    assertThat("Expected empty result when form not found to delete.", result.isPresent(),
        is(false));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"DRAFT"}, mode = EXCLUDE)
  void shouldReturnFalseIfFormCannotTransitionToDeleted(LifecycleState lifecycleState) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(lifecycleState);

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<Boolean> result = service.deleteLtftForm(ID);

    assertThat("Unexpected empty result when form is deleted.", result.isPresent(), is(true));
    assertThat("Expected false result when form cannot transition to DELETED.", result.get(),
        is(false));
  }

  @Test
  void shouldDeleteFormIfCanTransitionToDeleted() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<Boolean> result = service.deleteLtftForm(ID);

    assertThat("Unexpected empty result when form is deleted.", result.isPresent(), is(true));
    assertThat("Expected true result when form is deleted.", result.get(), is(true));
    verify(ltftRepository).deleteById(ID);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "WITHDRAWN"})
  void shouldReturnEmptyWhenTransitionFormNotFound(LifecycleState targetState) {
    when(ltftRepository.findByTraineeTisIdAndId(any(), any())).thenReturn(Optional.empty());
    LftfStatusInfoDetailDto detail
        = new LftfStatusInfoDetailDto("reason", "message");

    Optional<LtftFormDto> result = service.changeLtftFormState(ID, detail, targetState);
    assertThat("Unexpected transition result when form not found.", result.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "WITHDRAWN"})
  void shouldReturnEmptyWhenFormCannotTransitionToGivenState(LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.APPROVED); //cannot transition to any of the given states
    form.setContent(LtftContent.builder().name("test").build());
    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<LtftFormDto> result = service.changeLtftFormState(ID, detail, targetState);
    assertThat("Unexpected form transition.", result.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"UNSUBMITTED", "WITHDRAWN"})
  void shouldReturnEmptyWhenFormCannotTransitionWithoutStatusDetail(LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setContent(LtftContent.builder().name("test").build());

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<LtftFormDto> result = service.changeLtftFormState(ID, null, targetState);

    assertThat("Unexpected form transition without status detail.", result.isPresent(), is(false));
    verify(ltftRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"UNSUBMITTED", "WITHDRAWN"})
  void shouldReturnEmptyWhenFormCannotTransitionWithoutStatusDetailReason(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setContent(LtftContent.builder().name("test").build());
    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto(null, "message");

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<LtftFormDto> result = service.changeLtftFormState(ID, detail, targetState);

    assertThat("Unexpected form transition without status detail reason.", result.isPresent(),
        is(false));
    verify(ltftRepository, never()).save(any());
  }

  @Test
  void shouldSubmitFormWhenValidTransition() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);
    form.setContent(LtftContent.builder().name("test").build());

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(ltftRepository.save(any())).thenReturn(form);

    Optional<LtftFormDto> result = service.submitLtftForm(ID, null);

    assertThat("Unexpected result when form is submitted.", result.isPresent(), is(true));
    verify(ltftRepository).save(form);
  }

  @Test
  void shouldSubmitFormWithStatusDetail() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setRevision(2);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);
    form.setContent(LtftContent.builder().name("test").build());

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(ltftRepository.save(any())).thenReturn(form);

    Optional<LtftFormDto> result = service.submitLtftForm(ID, detail);

    assertThat("Unexpected result when form is submitted with status detail.", result.isPresent(),
        is(true));
    Person expectedModifiedBy = new Person(TRAINEE_NAME, TRAINEE_EMAIL, TRAINEE_ROLE);
    AbstractAuditedForm.Status.StatusDetail statusDetail = mapper.toStatusDetail(detail);
    AbstractAuditedForm.Status.StatusInfo newFormState = form.getStatus().current();
    assertThat("Unexpected status detail.", newFormState.detail(), is(statusDetail));
    assertThat("Unexpected status modified by.", newFormState.modifiedBy(),
        is(expectedModifiedBy));
    assertThat("Unexpected status modified timestamp.", newFormState.timestamp(),
        is(notNullValue()));
    assertThat("Unexpected form revision.", form.getRevision(), is(2));
    verify(ltftRepository).save(form);
  }

  @Test
  void shouldUnsubmitFormWithStatusDetail() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setRevision(2);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setContent(LtftContent.builder().name("test").build());

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(ltftRepository.save(any())).thenReturn(form);

    Optional<LtftFormDto> result = service.unsubmitLtftForm(ID, detail);

    assertThat("Unexpected result when form is unsubmitted with status detail.",
        result.isPresent(), is(true));
    Person expectedModifiedBy = new Person(TRAINEE_NAME, TRAINEE_EMAIL, TRAINEE_ROLE);
    AbstractAuditedForm.Status.StatusDetail statusDetail = mapper.toStatusDetail(detail);
    AbstractAuditedForm.Status.StatusInfo newFormState = form.getStatus().current();
    assertThat("Unexpected status detail.", newFormState.detail(), is(statusDetail));
    assertThat("Unexpected status modified by.", newFormState.modifiedBy(),
        is(expectedModifiedBy));
    assertThat("Unexpected status modified timestamp.", newFormState.timestamp(),
        is(notNullValue()));
    assertThat("Unexpected form revision.", form.getRevision(), is(2));
    verify(ltftRepository).save(form);
  }

  @Test
  void shouldWithdrawFormWithStatusDetail() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setRevision(2);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setContent(LtftContent.builder().name("test").build());

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(ltftRepository.save(any())).thenReturn(form);

    Optional<LtftFormDto> result = service.withdrawLtftForm(ID, detail);

    assertThat("Unexpected result when form is unsubmitted with status detail.",
        result.isPresent(), is(true));
    Person expectedModifiedBy = new Person(TRAINEE_NAME, TRAINEE_EMAIL, TRAINEE_ROLE);
    AbstractAuditedForm.Status.StatusDetail statusDetail = mapper.toStatusDetail(detail);
    AbstractAuditedForm.Status.StatusInfo newFormState = form.getStatus().current();
    assertThat("Unexpected status detail.", newFormState.detail(), is(statusDetail));
    assertThat("Unexpected status modified by.", newFormState.modifiedBy(),
        is(expectedModifiedBy));
    assertThat("Unexpected status modified timestamp.", newFormState.timestamp(),
        is(notNullValue()));
    assertThat("Unexpected form revision.", form.getRevision(), is(2));
    verify(ltftRepository).save(form);
  }
}
