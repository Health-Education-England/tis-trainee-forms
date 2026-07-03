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

package uk.nhs.hee.tis.trainee.forms.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.FormSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.content.FormContent;
import uk.nhs.hee.tis.trainee.forms.repository.BaseSubmissionHistoryRepository;

/**
 * Contract test for the {@link SubmissionHistoryService} implementations. This test class is
 * abstract and should be extended by concrete test classes for each implementation of the service.
 *
 * @param <F> The type of the form entity.
 * @param <H> The type of the submission history entity.
 */
public abstract class AbstractSubmissionHistoryServiceContractTest<
    F extends AbstractAuditedForm<? extends FormContent>,
    H extends AbstractAuditedForm<? extends FormContent> & FormSubmissionHistory> {

  private static final UUID ID = UUID.randomUUID();
  private static final String TRAINEE_ID = "123";

  private SubmissionHistoryService<F> service;
  private BaseSubmissionHistoryRepository<H> repository;

  /**
   * Create a mock of the repository to be used in tests.
   *
   * @return A mock of the repository to be used in tests.
   */
  abstract BaseSubmissionHistoryRepository<H> createRepositoryMock();

  /**
   * Get an instance of the service to be tested.
   *
   * @param repository The repository to be used by the service.
   * @return An instance of the service to be tested.
   */
  abstract SubmissionHistoryService<F> getService(BaseSubmissionHistoryRepository<H> repository);

  /**
   * Get an instance of the form entity to be used in tests.
   *
   * @return An instance of the form entity.
   */
  abstract F getNewForm();

  /**
   * Get an instance of the submission history entity to be used in tests.
   *
   * @return An instance of the submission history entity.
   */
  abstract H getNewHistory();

  @BeforeEach
  public void setup() {
    repository = createRepositoryMock();
    service = getService(repository);
  }

  @Test
  void shouldSaveSnapshotWithoutIdAndCreatedAndLastModifiedDate() {
    F form = getNewForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setRevision(1);
    form.setCreated(Instant.EPOCH);
    form.setLastModified(Instant.now());

    service.takeSnapshot(form);

    ArgumentCaptor<H> captor = ArgumentCaptor.captor();
    verify(repository).save(captor.capture());
    H submissionHistory = captor.getValue();
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
  void shouldMoveSubmissionsFromOneTraineeToAnother() {
    String fromTraineeId = "123";
    String toTraineeId = "456";

    H submission1 = getNewHistory();
    submission1.setTraineeTisId(fromTraineeId);
    H submission2 = getNewHistory();
    submission2.setTraineeTisId(fromTraineeId);
    List<H> submissions = List.of(submission1, submission2);

    when(repository.findByTraineeTisId(fromTraineeId)).thenReturn(submissions);

    service.moveHistory(fromTraineeId, toTraineeId);

    ArgumentCaptor<H> captor = ArgumentCaptor.captor();
    verify(repository, times(2)).save(captor.capture());

    List<H> savedSubmissions = captor.getAllValues();
    assertThat("Unexpected number of moved submissions", savedSubmissions.size(), is(2));
    savedSubmissions.forEach(submission ->
        assertThat("Unexpected trainee ID", submission.getTraineeTisId(), is(toTraineeId)));
  }

  @Test
  void shouldHandleNoSubmissionsToMove() {
    String fromTraineeId = "123";
    String toTraineeId = "456";

    when(repository.findByTraineeTisId(fromTraineeId)).thenReturn(List.of());

    service.moveHistory(fromTraineeId, toTraineeId);

    verify(repository, never()).save(any());
  }
}
