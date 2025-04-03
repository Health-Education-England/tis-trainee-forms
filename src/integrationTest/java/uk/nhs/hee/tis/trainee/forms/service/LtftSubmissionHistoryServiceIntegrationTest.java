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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

  @BeforeEach
  void setUp() {

  }

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
        savedSubmissionHistory.getId().equals(form.getId()), is(false));
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
}
