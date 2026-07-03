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
 */

package uk.nhs.hee.tis.trainee.forms.service;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.repository.FormrPartbSubmissionHistoryRepository;

/**
 * A service for managing FormR Part B submission history.
 */
@Service
@XRayEnabled
public class FormrPartbSubmissionHistoryService extends
    AbstractSubmissionHistoryService<FormRPartB, FormrPartbSubmissionHistory> {

  /**
   * Constructor for FormrPartbSubmissionHistoryService.
   *
   * @param historyRepository The repository for Form-R Part B submission history.
   * @param mapper            The mapper for converting form and form submission history items.
   */
  public FormrPartbSubmissionHistoryService(
      FormrPartbSubmissionHistoryRepository historyRepository, FormRPartBMapper mapper) {
    super(historyRepository, mapper);
  }
}
