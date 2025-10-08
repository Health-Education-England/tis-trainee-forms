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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
public class FormRelocateResourceIntegrationTest {

  private static final String FROM_TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TO_TRAINEE_ID = UUID.randomUUID().toString();

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
    template.findAllAndRemove(new Query(), FormRPartA.class);
    template.findAllAndRemove(new Query(), FormRPartB.class);
  }

  @Test
  void shouldMoveApplicableForms() throws Exception {

    //forms to move
    UUID id = UUID.randomUUID();
    FormRPartA formPartA = new FormRPartA();
    formPartA.setId(id);
    formPartA.setLifecycleState(DRAFT);
    formPartA.setTraineeTisId(FROM_TRAINEE_ID);
    template.insert(formPartA);

    UUID id2 = UUID.randomUUID();
    FormRPartB formPartB = new FormRPartB();
    formPartB.setId(id2);
    formPartB.setLifecycleState(DRAFT);
    formPartB.setTraineeTisId(FROM_TRAINEE_ID);
    template.insert(formPartB);

    //forms to leave
    UUID id3 = UUID.randomUUID();
    FormRPartB formPartB2 = new FormRPartB();
    formPartB2.setId(id3);
    formPartB2.setLifecycleState(DRAFT);
    formPartB2.setTraineeTisId("another trainee");
    template.insert(formPartB2);

    UUID id4 = UUID.randomUUID();
    FormRPartA formPartA2 = new FormRPartA();
    formPartA2.setId(id4);
    formPartA2.setLifecycleState(DRAFT);
    formPartA2.setTraineeTisId(TO_TRAINEE_ID);
    template.insert(formPartA2);

    mockMvc
        .perform(
            patch(
                "/api/form-relocate/move/{fromTraineeId}/to/{toTraineeId}",
                FROM_TRAINEE_ID,
                TO_TRAINEE_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.formr-a").value(1))
        .andExpect(jsonPath("$.formr-b").value(1));

    FormRPartA movedFormPartA = template.findById(id, FormRPartA.class);
    assertThat("Unexpected missing moved entity.", movedFormPartA, notNullValue());
    assertThat("Unexpected trainee id on moved entity.", movedFormPartA.getTraineeTisId(),
        is(TO_TRAINEE_ID));

    FormRPartB movedFormPartB = template.findById(id2, FormRPartB.class);
    assertThat("Unexpected missing moved entity.", movedFormPartB, notNullValue());
    assertThat("Unexpected trainee id on moved entity.", movedFormPartB.getTraineeTisId(),
        is(TO_TRAINEE_ID));

    FormRPartB movedFormPartB2 = template.findById(id3, FormRPartB.class);
    assertThat("Unexpected missing non-moved entity.", movedFormPartB2, notNullValue());
    assertThat("Unexpected trainee id on non-moved entity.", movedFormPartB2.getTraineeTisId(),
        is("another trainee"));

    FormRPartA movedFormPartA2 = template.findById(id4, FormRPartA.class);
    assertThat("Unexpected missing non-moved entity.", movedFormPartA2, notNullValue());
    assertThat("Unexpected trainee id on non-moved entity.", movedFormPartA2.getTraineeTisId(),
        is(TO_TRAINEE_ID));
  }
}
