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

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.nhs.hee.tis.trainee.forms.mapper.SubmissionHistoryMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.FormSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.content.FormContent;
import uk.nhs.hee.tis.trainee.forms.repository.BaseSubmissionHistoryRepository;

/**
 * An abstract service class that provides common functionality for managing the history of form
 * submissions.
 *
 * @param <F> The type of form content.
 * @param <H> The type of submission history.
 */
@Slf4j
public abstract class AbstractSubmissionHistoryService<
    F extends AbstractAuditedForm<? extends FormContent>,
    H extends AbstractAuditedForm<? extends FormContent> & FormSubmissionHistory> implements
    SubmissionHistoryService<F> {

  private final BaseSubmissionHistoryRepository<H> historyRepository;
  private final SubmissionHistoryMapper<F, H> mapper;

  /**
   * Constructor for AbstractSubmissionHistoryService.
   *
   * @param historyRepository The repository for managing submission history.
   * @param mapper            The mapper for converting between form and submission history.
   */
  protected AbstractSubmissionHistoryService(BaseSubmissionHistoryRepository<H> historyRepository,
      SubmissionHistoryMapper<F, H> mapper) {
    this.historyRepository = historyRepository;
    this.mapper = mapper;
  }

  @Override
  public void takeSnapshot(F form) {
    log.info("Taking snapshot of submitted form {}", form.getId());
    H submissionHistory = mapper.toSubmissionHistory(form);
    historyRepository.save(submissionHistory);
  }

  @Override
  public int moveHistory(String fromTraineeId, String toTraineeId) {
    int movedCount = 0;
    List<H> submissions = historyRepository.findByTraineeTisId(fromTraineeId);

    for (H submission : submissions) {
      log.debug("Moving submission [{}] from trainee [{}] to trainee [{}]", submission.getId(),
          fromTraineeId, toTraineeId);
      submission.setTraineeTisId(toTraineeId);
      historyRepository.save(submission); //lastModifiedDate will be overwritten here
      movedCount++;
    }
    log.info("Moved {} submissions from trainee [{}] to trainee [{}]", movedCount,
        fromTraineeId, toTraineeId);
    return movedCount;
  }
}
