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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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

  @Test
  void shouldMoveLtftSubmissionsFromOneTraineeToAnother() {
    String fromTraineeId = "123";
    String toTraineeId = "456";

    LtftSubmissionHistory submission1 = new LtftSubmissionHistory();
    submission1.setTraineeTisId(fromTraineeId);
    LtftSubmissionHistory submission2 = new LtftSubmissionHistory();
    submission2.setTraineeTisId(fromTraineeId);
    List<LtftSubmissionHistory> submissions = List.of(submission1, submission2);

    when(repository.findByTraineeTisId(fromTraineeId)).thenReturn(submissions);

    service.moveLtftSubmissions(fromTraineeId, toTraineeId);

    ArgumentCaptor<LtftSubmissionHistory> captor =
        ArgumentCaptor.forClass(LtftSubmissionHistory.class);
    verify(repository, times(2)).save(captor.capture());

    List<LtftSubmissionHistory> savedSubmissions = captor.getAllValues();
    assertThat("Unexpected number of moved submissions", savedSubmissions.size(), is(2));
    savedSubmissions.forEach(submission ->
        assertThat("Unexpected trainee ID", submission.getTraineeTisId(), is(toTraineeId)));
  }

  @Test
  void shouldHandleNoSubmissionsToMove() {
    String fromTraineeId = "123";
    String toTraineeId = "456";

    when(repository.findByTraineeTisId(fromTraineeId)).thenReturn(List.of());

    service.moveLtftSubmissions(fromTraineeId, toTraineeId);

    verify(repository, never()).save(any());
  }
}
