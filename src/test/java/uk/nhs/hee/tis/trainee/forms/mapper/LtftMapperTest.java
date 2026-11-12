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

package uk.nhs.hee.tis.trainee.forms.mapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.INVALID;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctAssumedFullTimePeriodDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctCalculatedChangeDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctCalculationDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctProgrammeDetailsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctProjectionDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctResultDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.StatusInfoDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;

class LtftMapperTest {

  private LtftMapper mapper;

  @BeforeEach
  void setUp() {
    TemporalMapper temporalMapper = new TemporalMapper(ZoneId.of("Etc/UTC"));
    mapper = new LtftMapperImpl(temporalMapper);

    // The temporal mapper must also be injected in to the abstract LTFT mapper.
    mapper.setTemporalMapper(temporalMapper);
  }

  @Test
  void shouldReturnNullJoinedStringWhenListNull() {
    String joined = mapper.joinListWithComma(null);

    assertThat("Unexpected joined string.", joined, nullValue());
  }

  @Test
  void shouldReturnEmptyJoinedStringWhenListEmpty() {
    String joined = mapper.joinListWithComma(List.of());

    assertThat("Unexpected joined string.", joined, is(""));
  }

  @Test
  void shouldReturnJoinedStringWhenListHasSingleString() {
    String joined = mapper.joinListWithComma(List.of("abc"));

    assertThat("Unexpected joined string.", joined, is("abc"));
  }

  @Test
  void shouldReturnSortedJoinedStringWhenListHasMultipleStrings() {
    String joined = mapper.joinListWithComma(List.of("def", "abc"));

    assertThat("Unexpected joined string.", joined, is("abc, def"));
  }

  @Test
  void shouldReturnNullShortNoticeWhenStatusNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(null);

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeWhenContentNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().build())
        .submitted(Instant.now())
        .build());

    entity.setContent(null);

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeWhenChangeNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().build())
        .submitted(Instant.now())
        .build());

    entity.setContent(LtftContent.builder()
        .change(null)
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeWhenStartDateNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().build())
        .submitted(Instant.now())
        .build());

    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(null)
            .build())
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeWhenCurrentNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(null)
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 111})
  void shouldReturnTrueShortNoticeWhenSubmissionWithinNoticePeriod(int days) {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().build())
        .submitted(Instant.now())
        .build());

    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now().plusDays(days))
            .build())
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, is(true));
  }

  @ParameterizedTest
  @ValueSource(ints = {112, 113})
  void shouldReturnFalseShortNoticeWhenSubmissionOutsideOrEqualToNoticePeriod(int days) {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().build())
        .submitted(Instant.now())
        .build());

    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now().plusDays(days))
            .build())
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, is(false));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 111})
  void shouldReturnUseCurrentDateForShortNoticeWhenCurrentStatusUnsubmitted(int days) {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(LifecycleState.UNSUBMITTED)
            .build())
        .submitted(Instant.now().minus(Duration.ofDays(120)))
        .build());

    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now().plusDays(days))
            .build())
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, is(true));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"DRAFT", "UNSUBMITTED"})
  void shouldReturnUseCurrentDateForShortNoticeWhenDraftUnsubmitted(LifecycleState state) {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(state)
            .build())
        .submitted(Instant.now().minus(Duration.ofDays(120)))
        .build());

    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now().plusDays(100))
            .build())
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, is(true));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldReturnUseSubmissionDateForShortNoticeTraineeWhenSubmitted(LifecycleState state) {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(state)
            .build())
        .submitted(Instant.now().minus(Duration.ofDays(120)))
        .build());

    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now().plusDays(100))
            .build())
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, is(false));
  }

  @Test
  void shouldReturnStatusDetailDtoWhenStatusDetailNotNull() {
    var statusDetail = Status.StatusDetail.builder()
        .reason("reason")
        .message("message")
        .build();

    var dto = mapper.toStatusDetailDto(statusDetail);

    assertThat("Unexpected status detail DTO.", dto.reason(), is("reason"));
    assertThat("Unexpected status detail DTO.", dto.message(), is("message"));
  }

  @Test
  void shouldReturnEmptyStatusDetailDtoWhenStatusDetailNull() {
    var dto = mapper.toStatusDetailDto(null);

    assertThat("Unexpected status detail DTO.", dto.reason(), nullValue());
    assertThat("Unexpected status detail DTO.", dto.message(), nullValue());
  }

  @Test
  void shouldMapAltStartDateToAdminSummaryDto() {
    LocalDate altStartDate = LocalDate.of(2026, Month.JUNE, 1);

    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().build())
        .build());
    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .altStartDate(altStartDate)
            .build())
        .build());

    LtftAdminSummaryDto dto = mapper.toAdminSummaryDto(entity);

    assertThat("Unexpected alternate start date.",
        dto.altStartDate(), is(altStartDate));
  }

  @Test
  void shouldMapNullAltStartDateToAdminSummaryDto() {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().build())
        .build());
    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .altStartDate(null)
            .build())
        .build());

    LtftAdminSummaryDto dto = mapper.toAdminSummaryDto(entity);

    assertThat("Unexpected alternate start date.", dto.altStartDate(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(ResponsibleRole.class)
  void shouldMapResponsibleRoleToAdminSummaryDto(ResponsibleRole role) {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().responsibleRole(role).build())
        .build());

    LtftAdminSummaryDto dto = mapper.toAdminSummaryDto(entity);

    assertThat("Unexpected responsible role.", dto.responsibleRole(), is(role));
  }

  @Test
  void shouldNotMapResponsibleRoleFromStatusInfoDto() {
    StatusInfoDto dto = StatusInfoDto.builder()
        .state(LifecycleState.SUBMITTED)
        .responsibleRole(ResponsibleRole.REVIEWER)
        .build();

    StatusInfo statusInfo = mapper.toStatusInfo(dto);

    assertThat("Unexpected state.", statusInfo.state(), is(LifecycleState.SUBMITTED));
    assertThat("Unexpected responsible role.", statusInfo.responsibleRole(), nullValue());
  }

  @ParameterizedTest
  @EnumSource(ResponsibleRole.class)
  void shouldMapResponsibleRoleToFormDto(ResponsibleRole role) {
    StatusInfo current = StatusInfo.builder().responsibleRole(role).build();
    StatusInfo previous = StatusInfo.builder().build();
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(current)
        .history(List.of(previous, current))
        .build());

    LtftFormDto dto = mapper.toDto(entity);

    assertThat("Unexpected current responsible role.", dto.status().current().responsibleRole(),
        is(role));
    assertThat("Unexpected previous responsible role.",
        dto.status().history().get(0).responsibleRole(), nullValue());
    assertThat("Unexpected latest responsible role.",
        dto.status().history().get(1).responsibleRole(), is(role));
  }

  @Test
  void shouldMapCctSnapshotToEntityAndBackToDto() {
    CctSnapshotDto snapshot = CctSnapshotDto.builder()
        .calculationId(UUID.randomUUID())
        .calculation(CctCalculationDto.builder()
            .id(UUID.randomUUID())
            .name("My calculation")
            .programmeDetails(CctProgrammeDetailsDto.builder()
                .specialty("General Practice")
                .startDate(LocalDate.of(2024, Month.AUGUST, 7))
                .lengthMonths(36)
                .startGrade("ST1")
                .build())
            .changes(List.of(CctCalculatedChangeDto.builder()
                .type("LTFT")
                .startDate(LocalDate.of(2025, Month.FEBRUARY, 5))
                .endDate(LocalDate.of(2025, Month.AUGUST, 4))
                .wte(0.8)
                .countsTowardsCompletion(true)
                .calendarMonths(6.0)
                .wteMonths(4.8)
                .build()))
            .result(CctResultDto.builder()
                .cctDate(LocalDate.of(2027, Month.OCTOBER, 1))
                .totalCalendarMonthsCompleted(12.0)
                .totalWteMonthsCompleted(10.8)
                .monthsRemaining(25.2)
                .assumedFullTimePeriods(List.of(CctAssumedFullTimePeriodDto.builder()
                    .startDate(LocalDate.of(2024, Month.AUGUST, 7))
                    .endDate(LocalDate.of(2025, Month.FEBRUARY, 4))
                    .months(6.0)
                    .build()))
                .projection(CctProjectionDto.builder()
                    .startDate(LocalDate.of(2025, Month.AUGUST, 5))
                    .wte(1.0)
                    .calendarMonths(25.2)
                    .wteMonths(25.2)
                    .build())
                .build())
            .created(Instant.parse("2025-01-01T00:00:00Z"))
            .lastModified(Instant.parse("2025-01-02T00:00:00Z"))
            .build())
        .build();
    LtftFormDto dto = LtftFormDto.builder().cctSnapshot(snapshot).build();

    LtftForm entity = mapper.toEntity(dto);

    LtftContent.CctSnapshot entitySnapshot = entity.getContent().cctSnapshot();
    assertThat("Unexpected calculation ID.", entitySnapshot.calculationId(),
        is(snapshot.calculationId()));
    assertThat("Unexpected specialty.",
        entitySnapshot.calculation().programmeDetails().specialty(), is("General Practice"));
    assertThat("Unexpected change count.", entitySnapshot.calculation().changes().size(), is(1));
    assertThat("Unexpected assumed full time period count.",
        entitySnapshot.calculation().result().assumedFullTimePeriods().size(), is(1));
    assertThat("Unexpected projection WTE.",
        entitySnapshot.calculation().result().projection().wte(), is(1.0));

    LtftFormDto mappedDto = mapper.toDto(entity);
    assertThat("Unexpected CCT snapshot.", mappedDto.cctSnapshot(), is(snapshot));
  }

  @Test
  void shouldReturnNullEmailValidityWhenStatusNull() {
    EmailValidityType emailValidity = mapper.toEmailValidity(null);

    assertThat("Unexpected email validity.", emailValidity, nullValue());
  }

  @Test
  void shouldReturnEmailInvalidWhenStatusNotMapped() {
    EmailValidityType emailValidity = mapper.toEmailValidity("unknown status");

    assertThat("Unexpected email validity.", emailValidity, is(INVALID));
  }
}
