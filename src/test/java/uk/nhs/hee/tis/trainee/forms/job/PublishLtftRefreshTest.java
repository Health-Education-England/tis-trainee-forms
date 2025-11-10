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

package uk.nhs.hee.tis.trainee.forms.job;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

class PublishLtftRefreshTest {

  private static final String PUBLISH_TOPIC = "refresh.all.ltfts";

  private PublishLtftRefresh job;

  private LtftFormRepository repository;
  private LtftService service;

  @BeforeEach
  void setUp() {
    repository = mock(LtftFormRepository.class);
    service = mock(LtftService.class);
    job = new PublishLtftRefresh(repository, service, PUBLISH_TOPIC);
  }

  @Test
  void shouldNotPublishWhenNoLtftsFound() {
    when(repository.findAll()).thenReturn(List.of());

    job.execute();

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = "DRAFT")
  void shouldNotPublishDraftLtfts(LifecycleState state) {
    UUID id1 = UUID.randomUUID();
    LtftForm form1 = new LtftForm();
    form1.setId(id1);

    UUID id2 = UUID.randomUUID();
    LtftForm form2 = new LtftForm();
    form2.setId(id2);

    ArgumentCaptor<Set<LifecycleState>> statesCaptor = ArgumentCaptor.captor();
    when(repository.findByStatus_Current_StateIn(statesCaptor.capture())).thenReturn(List.of());

    job.execute();

    Set<LifecycleState> states = statesCaptor.getValue();
    assertThat("Unexpected state query count.", states, hasSize(6));
    assertThat("Unexpected state in query.", states, hasItem(state));
  }

  @Test
  void shouldPublishAllFoundLtfts() {
    UUID id1 = UUID.randomUUID();
    LtftForm form1 = new LtftForm();
    form1.setId(id1);

    UUID id2 = UUID.randomUUID();
    LtftForm form2 = new LtftForm();
    form2.setId(id2);

    when(repository.findByStatus_Current_StateIn(any())).thenReturn(List.of(form1, form2));

    int publishCount = job.execute();

    assertThat("Unexpected published LTFT count.", publishCount, is(2));

    verify(service).publishUpdateNotification(form1, null, PUBLISH_TOPIC);
    verify(service).publishUpdateNotification(form2, null, PUBLISH_TOPIC);
  }

  @Test
  void shouldPublishPartialLtftsWhenFailures() {
    UUID id1 = UUID.randomUUID();
    LtftForm form1 = new LtftForm();
    form1.setId(id1);

    UUID id2 = UUID.randomUUID();
    LtftForm form2 = new LtftForm();
    form2.setId(id2);

    when(repository.findByStatus_Current_StateIn(any())).thenReturn(List.of(form1, form2));
    doThrow(RuntimeException.class).when(service)
        .publishUpdateNotification(form1, null, PUBLISH_TOPIC);

    int publishCount = job.execute();

    assertThat("Unexpected published LTFT count.", publishCount, is(1));

    verify(service).publishUpdateNotification(form1, null, PUBLISH_TOPIC);
    verify(service).publishUpdateNotification(form2, null, PUBLISH_TOPIC);
  }
}
