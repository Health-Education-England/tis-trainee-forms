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
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.ReflectionUtils;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.TemporalMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Discussions;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.ProgrammeMembership;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;

class LtftServiceTest {

  private static final String TRAINEE_ID = "40";
  private static final String ADMIN_GROUP = "abc-123";
  private static final UUID ID = UUID.randomUUID();

  private LtftService service;
  private LtftFormRepository ltftRepository;
  private LtftMapper mapper;

  @BeforeEach
  void setUp() {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setGroups(Set.of(ADMIN_GROUP));

    TraineeIdentity traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(TRAINEE_ID);

    ltftRepository = mock(LtftFormRepository.class);

    mapper = new LtftMapperImpl();
    Field field = ReflectionUtils.findField(LtftMapperImpl.class, "temporalMapper");
    field.setAccessible(true);
    ReflectionUtils.setField(field, mapper, new TemporalMapperImpl());

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
  void shouldCountAllLocalOfficeLtftWhenFiltersEmpty(Set<LifecycleState> states) {
    when(ltftRepository.countByContent_ProgrammeMembership_DesignatedBodyCodeIn(
        Set.of(ADMIN_GROUP))).thenReturn(40L);

    long count = service.getAdminLtftCount(states);

    assertThat("Unexpected count.", count, is(40L));
    verify(ltftRepository, never()).count();
    verify(ltftRepository, never())
        .countByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(any(),
            any());
  }

  @Test
  void shouldCountFilteredLocalOfficeLtftWhenFiltersNotEmpty() {
    Set<LifecycleState> states = Set.of(LifecycleState.SUBMITTED);
    when(ltftRepository
        .countByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(states,
            Set.of(ADMIN_GROUP))).thenReturn(40L);

    long count = service.getAdminLtftCount(states);

    assertThat("Unexpected count.", count, is(40L));
    verify(ltftRepository, never()).count();
    verify(ltftRepository, never()).countByContent_ProgrammeMembership_DesignatedBodyCodeIn(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldGetAllLocalOfficeLtftWhenFiltersEmpty(Set<LifecycleState> states) {
    PageRequest pageRequest = PageRequest.of(1, 1);

    LtftForm entity1 = new LtftForm();
    UUID id1 = UUID.randomUUID();
    entity1.setId(id1);

    LtftForm entity2 = new LtftForm();
    UUID id2 = UUID.randomUUID();
    entity2.setId(id2);

    when(ltftRepository
        .findByContent_ProgrammeMembership_DesignatedBodyCodeIn(Set.of(ADMIN_GROUP), pageRequest))
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
  void shouldGetFilteredLocalOfficeLtftWhenFiltersNotEmpty() {
    Set<LifecycleState> states = Set.of(LifecycleState.SUBMITTED);
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

    verify(ltftRepository, never()).findByContent_ProgrammeMembership_DesignatedBodyCodeIn(any(),
        any());
  }

  @Test
  void shouldReturnEmptyIfLtftFormNotFound() {
    when(ltftRepository.findByTraineeTisIdAndId(any(), any())).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
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

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(false));
    verify(ltftRepository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    LtftFormDto returnedFormDto = formDtoOptional.get();
    assertThat("Unexpected returned LTFT form.", returnedFormDto, is(mapper.toDto(form)));
  }

  @Test
  void shouldNotSaveIfNewLtftFormNotForTrainee() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeTisId("another trainee");

    Optional<LtftFormDto> formDtoOptional = service.saveLtftForm(dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldSaveIfNewLtftFormForTrainee() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeTisId(TRAINEE_ID);

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
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeTisId(TRAINEE_ID);

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfIdDoesNotMatchPathParameter() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeTisId(TRAINEE_ID);
    dtoToSave.setId(ID);

    Optional<LtftFormDto> formDtoOptional
        = service.updateLtftForm(UUID.randomUUID(), dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfTraineeDoesNotMatchLoggedInUser() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeTisId("another trainee");
    dtoToSave.setId(ID);

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfExistingFormNotFound() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeTisId(TRAINEE_ID);
    dtoToSave.setId(ID);

    when(ltftRepository.findByTraineeTisIdAndId(TRAINEE_ID, ID))
        .thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verify(ltftRepository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(ltftRepository);
  }

  @Test
  void shouldSaveIfUpdatingLtftFormForTrainee() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeTisId(TRAINEE_ID);
    dtoToSave.setId(ID);

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
}
