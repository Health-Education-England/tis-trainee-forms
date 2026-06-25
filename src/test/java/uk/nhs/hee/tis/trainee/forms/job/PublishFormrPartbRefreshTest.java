/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
 *
 */

package uk.nhs.hee.tis.trainee.forms.job;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Objects;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.TemporalMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

class PublishFormrPartbRefreshTest {

  private static final String PUBLISH_TOPIC = "refresh.all.formrs";

  private PublishFormrPartbRefresh job;

  private FormRPartBRepository repository;
  private FormRPartBService service;

  @BeforeEach
  void setUp() {
    repository = mock(FormRPartBRepository.class);
    service = mock(FormRPartBService.class);

    FormRPartBMapper mapper = new FormRPartBMapperImpl(new TemporalMapper(ZoneId.of("Etc/UTC")));

    job = new PublishFormrPartbRefresh(repository, service, mapper, PUBLISH_TOPIC);
  }

  @Test
  void shouldNotPublishWhenNoFormrPartbsFound() {
    when(repository.streamByLifecycleStateIn(any())).thenReturn(Stream.of());

    job.execute(Optional.empty());

    verifyNoInteractions(service);
  }

  @Test
  void shouldNotPublishWhenNoFormrPartbsFoundWithCutoffDate() {
    when(repository.streamByLifecycleStateInAndLastModifiedDateGreaterThanEqual(any(),
        any())).thenReturn(Stream.of());

    job.execute(Optional.of(LocalDate.of(2025, Month.JANUARY, 1)));

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = {"APPROVED", "DRAFT",
      "REJECTED", "WITHDRAWN"})
  void shouldNotPublishDraftFormrPartbs(LifecycleState state) {
    ArgumentCaptor<Set<LifecycleState>> statesCaptor = ArgumentCaptor.captor();
    when(repository.streamByLifecycleStateIn(statesCaptor.capture())).thenReturn(Stream.of());

    job.execute(Optional.empty());

    Set<LifecycleState> states = statesCaptor.getValue();
    assertThat("Unexpected state query count.", states, hasSize(3));
    assertThat("Unexpected state in query.", states, hasItem(state));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = {"APPROVED", "DRAFT",
      "REJECTED", "WITHDRAWN"})
  void shouldNotPublishDraftFormrPartbsWithCutoffDate(LifecycleState state) {
    LocalDate since = LocalDate.of(2025, Month.JANUARY, 1);
    ArgumentCaptor<Set<LifecycleState>> statesCaptor = ArgumentCaptor.captor();
    when(repository.streamByLifecycleStateInAndLastModifiedDateGreaterThanEqual(
        statesCaptor.capture(), any())).thenReturn(Stream.of());

    job.execute(Optional.of(since));

    Set<LifecycleState> states = statesCaptor.getValue();
    assertThat("Unexpected state query count.", states, hasSize(3));
    assertThat("Unexpected state in query.", states, hasItem(state));
  }

  @Test
  void shouldUseCutoffDateWhenProvided() {
    LocalDate since = LocalDate.of(2025, Month.JUNE, 15);
    ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.captor();
    when(repository.streamByLifecycleStateInAndLastModifiedDateGreaterThanEqual(any(),
        cutoffCaptor.capture())).thenReturn(Stream.of());

    job.execute(Optional.of(since));

    assertThat("Unexpected cutoff date.", cutoffCaptor.getValue(),
        is(since.atStartOfDay().toInstant(UTC)));
  }

  @Test
  void shouldPublishAllFoundFormrPartbs() {
    UUID id1 = UUID.randomUUID();
    FormRPartB form1 = new FormRPartB();
    form1.setId(id1);

    UUID id2 = UUID.randomUUID();
    FormRPartB form2 = new FormRPartB();
    form2.setId(id2);

    when(repository.streamByLifecycleStateIn(any())).thenReturn(Stream.of(form1, form2));

    int publishCount = job.execute(Optional.empty());

    assertThat("Unexpected published Form-R count.", publishCount, is(2));

    verify(service).publishUpdateNotification(argThat(hasId(id1)), eq(PUBLISH_TOPIC));
    verify(service).publishUpdateNotification(argThat(hasId(id2)), eq(PUBLISH_TOPIC));
  }

  @Test
  void shouldPublishAllFoundFormrPartbsWithCutoffDate() {
    UUID id1 = UUID.randomUUID();
    FormRPartB form1 = new FormRPartB();
    form1.setId(id1);

    when(repository.streamByLifecycleStateInAndLastModifiedDateGreaterThanEqual(any(),
        any())).thenReturn(Stream.of(form1));

    int publishCount = job.execute(Optional.of(LocalDate.of(2025, Month.JANUARY, 1)));

    assertThat("Unexpected published Form-R count.", publishCount, is(1));
    verify(service).publishUpdateNotification(argThat(hasId(id1)), eq(PUBLISH_TOPIC));
  }

  @Test
  void shouldPublishPartialFormrPartbsWhenFailures() {
    UUID id1 = UUID.randomUUID();
    FormRPartB form1 = new FormRPartB();
    form1.setId(id1);

    UUID id2 = UUID.randomUUID();
    FormRPartB form2 = new FormRPartB();
    form2.setId(id2);

    when(repository.streamByLifecycleStateIn(any())).thenReturn(Stream.of(form1, form2));
    doThrow(RuntimeException.class).when(service)
        .publishUpdateNotification(argThat(hasId(id1)), eq(PUBLISH_TOPIC));

    int publishCount = job.execute(Optional.empty());

    assertThat("Unexpected published Form-R count.", publishCount, is(1));

    verify(service).publishUpdateNotification(argThat(hasId(id1)), eq(PUBLISH_TOPIC));
    verify(service).publishUpdateNotification(argThat(hasId(id2)), eq(PUBLISH_TOPIC));
  }

  /**
   * Creates a Mockito {@link ArgumentMatcher} that matches {@link FormRPartBDto} with the given
   * ID.
   *
   * @param id The expected UUID.
   * @return an ArgumentMatcher that matches DTOs with the given UUID.
   */
  private static ArgumentMatcher<FormRPartBDto> hasId(UUID id) {
    return dto -> Objects.equal(dto.getId(), id.toString());
  }
}
