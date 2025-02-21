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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  void shouldReturnNullShortNoticeWhenSubmittedNull() {
    LtftForm entity = spy(new LtftForm());
    when(entity.getSubmitted()).thenReturn(null);

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeWhenContentNull() {
    LtftForm entity = spy(new LtftForm());
    when(entity.getSubmitted()).thenReturn(Instant.now());

    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .build())
        .build());

    entity.setContent(null);

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeWhenChangeNull() {
    LtftForm entity = spy(new LtftForm());
    when(entity.getSubmitted()).thenReturn(Instant.now());

    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .build())
        .build());

    entity.setContent(LtftContent.builder()
        .change(null)
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, nullValue());
  }

  @Test
  void shouldReturnNullShortNoticeWhenStartDateNull() {
    LtftForm entity = spy(new LtftForm());
    when(entity.getSubmitted()).thenReturn(Instant.now());

    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .build())
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
  void shouldReturnTrueShortNoticeWhenSubmissionWithinNoticePeriod(int days) {
    LtftForm entity = spy(new LtftForm());
    when(entity.getSubmitted()).thenReturn(Instant.now());

    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .build())
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
    LtftForm entity = spy(new LtftForm());
    when(entity.getSubmitted()).thenReturn(Instant.now());

    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .build())
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
    LtftForm entity = spy(new LtftForm());
    when(entity.getSubmitted()).thenReturn(Instant.now().minus(Duration.ofDays(120)));

    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(LifecycleState.UNSUBMITTED)
            .build())
        .build());

    entity.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now().plusDays(days))
            .build())
        .build());

    Boolean isShortNotice = mapper.isShortNotice(entity);

    assertThat("Unexpected short notice value.", isShortNotice, is(true));
  }
}
