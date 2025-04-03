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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.TemporalMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.repository.LtftSubmissionHistoryRepository;

class LtftSubmissionHistoryServiceTest {

  private static final UUID ID = UUID.randomUUID();
  private static final String TRAINEE_ID = "123";

  private LtftSubmissionHistoryService service;

  private LtftSubmissionHistoryRepository repository;

  @BeforeEach
  void setUp() {
    repository = mock(LtftSubmissionHistoryRepository.class);
    LtftMapper mapper = new LtftMapperImpl(new TemporalMapperImpl());
    service = new LtftSubmissionHistoryService(repository, mapper);
  }

  @Test
  void shouldSaveSnapshotWithoutIdAndCreatedAndLastModifiedDate() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setRevision(1);
    form.setCreated(Instant.EPOCH);
    form.setLastModified(Instant.now());

    service.takeSnapshot(form);

    ArgumentCaptor<LtftSubmissionHistory> captor
        = ArgumentCaptor.forClass(LtftSubmissionHistory.class);
    verify(repository).save(captor.capture());
    LtftSubmissionHistory submissionHistory = captor.getValue();
    assertThat("Unexpected ID.", submissionHistory.getId(), is(nullValue()));
    assertThat("Unexpected created date.", submissionHistory.getCreated(),
        is(nullValue()));
    assertThat("Unexpected last modified date.", submissionHistory.getLastModified(),
        is(nullValue()));

    assertThat("Unexpected revision.", submissionHistory.getRevision(), is(form.getRevision()));
    assertThat("Unexpected trainee ID.", submissionHistory.getTraineeTisId(),
        is(form.getTraineeTisId()));
  }
}
