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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LtftSubmissionHistoryServiceIntegrationTest {

  private static final String TRAINEE_ID = "47165";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private LtftSubmissionHistoryService service;

  @Autowired
  private MongoTemplate template;

  @MockBean
  private SnsTemplate snsTemplate;

  @AfterEach
  void tearDown() {
    template.remove(new Query(), LtftSubmissionHistory.class);
  }

  @Test
  void shouldSaveLtftSubmissionHistory() {
    LtftForm form = new LtftForm();
    form.setId(UUID.randomUUID());
    form.setRevision(1);
    form.setFormRef("LTFT-12345");
    form.setTraineeTisId(TRAINEE_ID);

    service.takeSnapshot(form);

    var savedSubmissionHistory = template.findOne(new Query(), LtftSubmissionHistory.class);

    assert savedSubmissionHistory != null;

    assertThat("Unexpected saved submission ID.",
        savedSubmissionHistory.getId(), not(form.getId()));
    assertThat("Unexpected saved submission created.",
        savedSubmissionHistory.getCreated(), notNullValue());
    assertThat("Unexpected saved submission last modified.",
        savedSubmissionHistory.getLastModified(), notNullValue());

    assertThat("Unexpected saved submission form ref.",
        savedSubmissionHistory.getFormRef(), is(form.getFormRef()));
    assertThat("Unexpected saved submission revision.",
        savedSubmissionHistory.getRevision(), is(form.getRevision()));
    assertThat("Unexpected saved submission trainee TIS ID.",
        savedSubmissionHistory.getTraineeTisId(), is(form.getTraineeTisId()));
  }

  @Test
  void shouldMoveLtftSubmissionsWhenSubmissionsExist() {
    String fromTraineeId = "oldTrainee";
    String toTraineeId = "newTrainee";
    LtftForm form1 = new LtftForm();
    form1.setId(UUID.randomUUID());
    form1.setTraineeTisId(fromTraineeId);
    form1.setFormRef("form 1");
    form1.setRevision(1);
    LtftForm form2 = new LtftForm();
    form2.setId(UUID.randomUUID());
    form2.setTraineeTisId(fromTraineeId);
    form2.setFormRef("form 2");
    form2.setRevision(1);

    service.takeSnapshot(form1);
    service.takeSnapshot(form2);

    service.moveLtftSubmissions(fromTraineeId, toTraineeId);

    List<LtftSubmissionHistory> traineeSubmissions
        = template.findAll(LtftSubmissionHistory.class);

    assertThat("Unexpected number of submissions.", traineeSubmissions.size(), is(2));
    for (LtftSubmissionHistory submission : traineeSubmissions) {
      assertThat("Submission not moved.", submission.getTraineeTisId(), is(toTraineeId));
    }
  }

  @Test
  void shouldHandleMoveLtftSubmissionsWhenNoSubmissionsExist() {
    String fromTraineeId = "oldTrainee";
    String toTraineeId = "newTrainee";

    service.moveLtftSubmissions(fromTraineeId, toTraineeId);

    List<LtftSubmissionHistory> traineeSubmissions
        = template.findAll(LtftSubmissionHistory.class);

    assertThat("Unexpected number of submissions.", traineeSubmissions.size(), is(0));
  }

  @Test
  void willUpdateLastModifiedDateWhenMoving() {
    String fromTraineeId = "oldTrainee";
    String toTraineeId = "newTrainee";
    LtftForm form1 = new LtftForm();
    form1.setId(UUID.randomUUID());
    form1.setTraineeTisId(fromTraineeId);
    form1.setFormRef("form 1");
    form1.setRevision(1);
    Instant lastModified = Instant.now().minusSeconds(100);
    form1.setLastModified(lastModified);

    service.takeSnapshot(form1);

    service.moveLtftSubmissions(fromTraineeId, toTraineeId);

    List<LtftSubmissionHistory> traineeSubmissions = template.findAll(LtftSubmissionHistory.class);

    assertThat("Unexpected number of submissions.", traineeSubmissions.size(), is(1));
    for (LtftSubmissionHistory submission : traineeSubmissions) {
      assertThat("Submission last modified has changed.", submission.getLastModified(),
          greaterThan(lastModified));
    }
  }
}
