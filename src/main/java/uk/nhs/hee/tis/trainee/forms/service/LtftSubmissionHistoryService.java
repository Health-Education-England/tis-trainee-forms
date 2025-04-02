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

import com.amazonaws.xray.spring.aop.XRayEnabled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.repository.LtftSubmissionHistoryRepository;

/**
 * A service for managing LTFT submission history.
 */
@Slf4j
@Service
@XRayEnabled
public class LtftSubmissionHistoryService {

  private final LtftSubmissionHistoryRepository ltftHistoryRepository;

  private final LtftMapper mapper;

  /**
   * Constructor for LtftSubmissionHistoryService.
   *
   * @param ltftHistoryRepository The repository for LTFT submission history.
   * @param mapper                The mapper for converting LTFT and LTFT submission history items.
   */
  public LtftSubmissionHistoryService(
      LtftSubmissionHistoryRepository ltftHistoryRepository, LtftMapper mapper) {
    this.ltftHistoryRepository = ltftHistoryRepository;
    this.mapper = mapper;
  }

  /**
   * Take a snapshot of the submitted LTFT form and save it to the repository.
   *
   * @param ltftForm The LTFT form to be saved.
   */
  public void takeSnapshot(LtftForm ltftForm) {
    log.info("Taking snapshot of submitted form {}", ltftForm.getId());
    LtftSubmissionHistory submissionHistory = mapper.toSubmissionHistory(ltftForm);
    ltftHistoryRepository.save(submissionHistory);
  }

}
