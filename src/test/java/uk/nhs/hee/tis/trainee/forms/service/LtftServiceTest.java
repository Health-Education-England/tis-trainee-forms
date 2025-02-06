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
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm.LtftProgrammeMembership;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;

class LtftServiceTest {

  private static final String TRAINEE_ID = "40";
  private static final UUID ID = UUID.randomUUID();

  private LtftService service;
  private LtftFormRepository ltftRepository;
  private LtftMapper mapper = new LtftMapperImpl();

  @BeforeEach
  void setUp() {
    TraineeIdentity traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(TRAINEE_ID);

    ltftRepository = mock(LtftFormRepository.class);

    service = new LtftService(traineeIdentity, ltftRepository, new LtftMapperImpl());
  }

  @Test
  void shouldReturnEmptyGettingLtftFormSummariesWhenNotFound() {
    when(ltftRepository.findByTraineeIdOrderByLastModified(TRAINEE_ID)).thenReturn(
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

    LtftForm entity1 = LtftForm.builder()
        .id(ltftId1)
        .traineeId(TRAINEE_ID)
        .name("Test LTFT form 1")
        .programmeMembership(LtftProgrammeMembership.builder()
            .id(pmId1)
            .build())
        .discussions(LtftForm.LtftDiscussions.builder()
            .tpdName("tpd")
            .other(List.of(LtftForm.LtftPersonRole.builder()
                    .name("other")
                .build()))
            .build())
        .created(created1)
        .lastModified(lastModified1)
        .build();

    UUID ltftId2 = UUID.randomUUID();
    UUID pmId2 = UUID.randomUUID();
    Instant created2 = Instant.now().minus(Duration.ofDays(2));
    Instant lastModified2 = Instant.now().plus(Duration.ofDays(2));

    LtftForm entity2 = LtftForm.builder()
        .id(ltftId2)
        .traineeId(TRAINEE_ID)
        .name("Test LTFT form 2")
        .programmeMembership(LtftProgrammeMembership.builder()
            .id(pmId2)
            .build())
        .created(created2)
        .lastModified(lastModified2)
        .build();

    when(ltftRepository.findByTraineeIdOrderByLastModified(TRAINEE_ID)).thenReturn(
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
  void shouldCountAllLtftWhenFiltersEmpty(Set<LifecycleState> states) {
    when(ltftRepository.count()).thenReturn(40L);

    long count = service.getAdminLtftCount(states);

    assertThat("Unexpected count.", count, is(40L));
    verify(ltftRepository, never()).countByStatusIn(any());
  }

  @Test
  void shouldCountFilteredLtftWhenFiltersNotEmpty() {
    Set<LifecycleState> states = Set.of(LifecycleState.SUBMITTED);
    when(ltftRepository.countByStatusIn(states)).thenReturn(40L);

    long count = service.getAdminLtftCount(Set.of(LifecycleState.SUBMITTED));

    assertThat("Unexpected count.", count, is(40L));
    verify(ltftRepository, never()).count();
  }

  @Test
  void shouldReturnEmptyIfLtftFormNotFound() {
    when(ltftRepository.findByTraineeIdAndId(any(), any())).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verify(ltftRepository).findByTraineeIdAndId(any(), eq(ID));
    verifyNoMoreInteractions(ltftRepository);
  }

  @Test
  void shouldReturnEmptyIfLtftFormForTraineeNotFound() {
    when(ltftRepository.findByTraineeIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verify(ltftRepository).findByTraineeIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(ltftRepository);
  }

  @Test
  void shouldReturnDtoIfLtftFormForTraineeFound() {
    LtftForm form = LtftForm.builder()
        .id(ID)
        .traineeId(TRAINEE_ID)
        .name("test")
        .build();
    when(ltftRepository.findByTraineeIdAndId(TRAINEE_ID, ID))
        .thenReturn(Optional.of(form));

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(false));
    verify(ltftRepository).findByTraineeIdAndId(TRAINEE_ID, ID);
    LtftFormDto returnedFormDto = formDtoOptional.get();
    assertThat("Unexpected returned LTFT form.", returnedFormDto.equals(mapper.toDto(form)),
        is(true));
  }

  @Test
  void shouldNotSaveIfNewLtftFormNotForTrainee() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeId("another trainee");

    Optional<LtftFormDto> formDtoOptional = service.saveLtftForm(dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldSaveIfNewLtftFormForTrainee() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeId(TRAINEE_ID);

    LtftForm existingForm = LtftForm.builder()
        .id(ID)
        .traineeId(TRAINEE_ID)
        .name("test")
        .build();
    when(ltftRepository.save(any())).thenReturn(existingForm);

    Optional<LtftFormDto> formDtoOptional = service.saveLtftForm(dtoToSave);

    verify(ltftRepository).save(any());
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));
  }

  @Test
  void shouldNotUpdateFormIfWithoutId() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeId(TRAINEE_ID);

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfIdDoesNotMatchPathParameter() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeId(TRAINEE_ID);
    dtoToSave.setId(ID);

    Optional<LtftFormDto> formDtoOptional
        = service.updateLtftForm(UUID.randomUUID(), dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfTraineeDoesNotMatchLoggedInUser() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeId("another trainee");
    dtoToSave.setId(ID);

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verifyNoInteractions(ltftRepository);
  }

  @Test
  void shouldNotUpdateFormIfExistingFormNotFound() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeId(TRAINEE_ID);
    dtoToSave.setId(ID);

    when(ltftRepository.findByTraineeIdAndId(TRAINEE_ID, ID))
        .thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isEmpty(), is(true));
    verify(ltftRepository).findByTraineeIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(ltftRepository);
  }

  @Test
  void shouldSaveIfUpdatingLtftFormForTrainee() {
    LtftFormDto dtoToSave = new LtftFormDto();
    dtoToSave.setTraineeId(TRAINEE_ID);
    dtoToSave.setId(ID);

    LtftForm existingForm = LtftForm.builder()
        .id(ID)
        .traineeId(TRAINEE_ID)
        .name("test")
        .build();
    when(ltftRepository.findByTraineeIdAndId(TRAINEE_ID, ID))
        .thenReturn(Optional.of(existingForm));
    when(ltftRepository.save(any())).thenReturn(existingForm);

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    LtftForm formToSave = mapper.toEntity(dtoToSave);
    verify(ltftRepository).save(formToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));
  }
}
