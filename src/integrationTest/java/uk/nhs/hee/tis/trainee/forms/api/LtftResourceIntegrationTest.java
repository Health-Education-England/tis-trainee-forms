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

package uk.nhs.hee.tis.trainee.forms.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class LtftResourceIntegrationTest {
  private static final String TRAINEE_ID = "40";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MongoTemplate template;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), LtftForm.class);
  }

  @Test
  void shouldSetCreatedAndLastModifiedWhenSave() {
    LtftForm ltft = new LtftForm();
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setName("name");
    template.insert(ltft);

    List<LtftForm> savedRecords = template.find(new Query(), LtftForm.class);
    assertThat("Unexpected saved records.", savedRecords.size(), is(1));
    LtftForm savedRecord = savedRecords.get(0);
    assertThat("Unexpected saved record name.", savedRecord.getName(), is("name"));
    assertThat("Unexpected saved record trainee id.", savedRecord.getTraineeTisId(),
        is(TRAINEE_ID));
    assertThat("Unexpected saved record id.", savedRecord.getId(), is(notNullValue()));
    Instant roughlyNow = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant roughlyCreated = savedRecord.getCreated().truncatedTo(ChronoUnit.SECONDS);
    assertThat("Unexpected saved record created timestamp.",
        roughlyCreated.equals(roughlyNow), is(true));
    Instant roughlyLastModified = savedRecord.getLastModified().truncatedTo(ChronoUnit.SECONDS);
    assertThat("Unexpected saved record last modified timestamp.",
        roughlyLastModified.equals(roughlyNow), is(true));
  }

  @Test
  void shouldUpdateLastModifiedWhenUpdate() throws InterruptedException {
    LtftForm ltft = new LtftForm();
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setName("name");
    template.insert(ltft);

    List<LtftForm> savedRecords = template.find(new Query(), LtftForm.class);
    assertThat("Unexpected saved records.", savedRecords.size(), is(1));
    LtftForm savedRecord = savedRecords.get(0);
    Instant savedCreated = savedRecord.getCreated();
    Instant savedLastModified = savedRecord.getLastModified();

    ltft.setId(savedRecord.getId());
    Thread.sleep(1000);
    template.save(ltft);

    List<LtftForm> updatedRecords = template.find(new Query(), LtftForm.class);
    assertThat("Unexpected updated records.", updatedRecords.size(), is(1));
    LtftForm updatedRecord = updatedRecords.get(0);
    Instant updatedCreated = updatedRecord.getCreated();
    Instant updatedLastModified = updatedRecord.getLastModified();

    assertThat("Unexpected updated record created timestamp.",
        updatedCreated.equals(savedCreated), is(true));
    assertThat("Unexpected updated record last modified timestamp.",
        updatedLastModified.isAfter(savedLastModified), is(true));
  }
}
