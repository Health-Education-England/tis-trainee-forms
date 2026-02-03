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
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;

class LtftMapperTest {

  private LtftMapper mapper;

  @BeforeEach
  void setUp() {
    TemporalMapperImpl temporalMapper = new TemporalMapperImpl();
    temporalMapper.zoneId = ZoneId.of("Etc/UTC");

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
  void shouldReturnNullShortNoticeAdminWhenStatusNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(null);

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeAdminWhenSubmittedNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .submitted(null)
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeAdminWhenContentNull() {
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
  void shouldReturnNullShortNoticeAdminWhenChangeNull() {
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
  void shouldReturnNullShortNoticeAdminWhenStartDateNull() {
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

  @ParameterizedTest
  @ValueSource(ints = {0, 111})
  void shouldReturnTrueShortNoticeAdminWhenSubmissionWithinNoticePeriod(int days) {
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
  void shouldReturnFalseShortNoticeAdminWhenSubmissionOutsideOrEqualToNoticePeriod(int days) {
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
  void shouldReturnUseCurrentDateForShortNoticeAdminWhenCurrentStatusUnsubmitted(int days) {
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
  void shouldReturnNullEmailValidityWhenStatusNull() {
    EmailValidityType emailValidity = mapper.toEmailValidity(null);

    assertThat("Unexpected email validity.", emailValidity, nullValue());
  }

  @Test
  void shouldReturnEmailInvalidWhenStatusNotMapped() {
    EmailValidityType emailValidity = mapper.toEmailValidity("unknown status");

    assertThat("Unexpected email validity.", emailValidity, is(INVALID));
  }

  @Test
  void shouldReturnNullShortNoticeTraineeWhenStatusNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(null);

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeTraineeWhenCurrentNull() {
    LtftForm entity = new LtftForm();
    entity.setStatus(Status.builder()
        .current(null)
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeTraineeWhenContentNull() {
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
  void shouldReturnNullShortNoticeTraineeWhenChangeNull() {
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
  void shouldReturnNullShortNoticeTraineeWhenStartDateNull() {
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

  @ParameterizedTest
  @ValueSource(ints = {0, 111})
  void shouldReturnTrueShortNoticeTraineeWhenSubmissionWithinNoticePeriod(int days) {
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
  void shouldReturnFalseShortNoticeTraineeWhenSubmissionOutsideOrEqualToNoticePeriod(int days) {
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
  @EnumSource(value = LifecycleState.class, names = {"DRAFT", "UNSUBMITTED"})
  void shouldReturnUseCurrentDateForShortNoticeTraineeWhenDraftUnsubmitted(LifecycleState state) {
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
}
