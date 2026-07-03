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

import static org.mockito.Mockito.mock;

import java.time.ZoneId;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.TemporalMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartaSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.repository.BaseSubmissionHistoryRepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormrPartaSubmissionHistoryRepository;

/**
 * This test class extends the abstract contract test class
 * {@link AbstractSubmissionHistoryServiceContractTest} to test the
 * {@link FormrPartaSubmissionHistoryService}.
 */
class FormrPartaSubmissionHistoryServiceTest extends
    AbstractSubmissionHistoryServiceContractTest<FormRPartA, FormrPartaSubmissionHistory> {

  @Override
  FormrPartaSubmissionHistoryRepository createRepositoryMock() {
    return mock();
  }

  @Override
  SubmissionHistoryService<FormRPartA> getService(
      BaseSubmissionHistoryRepository<FormrPartaSubmissionHistory> repository) {
    FormRPartAMapper mapper = new FormRPartAMapperImpl(new TemporalMapper(ZoneId.of("Etc/UTC")));
    return new FormrPartaSubmissionHistoryService(
        (FormrPartaSubmissionHistoryRepository) repository, mapper);
  }

  @Override
  FormRPartA getNewForm() {
    return new FormRPartA();
  }

  @Override
  FormrPartaSubmissionHistory getNewHistory() {
    return new FormrPartaSubmissionHistory();
  }
}
