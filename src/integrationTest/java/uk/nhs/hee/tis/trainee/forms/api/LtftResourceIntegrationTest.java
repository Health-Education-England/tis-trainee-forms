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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class LtftResourceIntegrationTest {
  private static final String TRAINEE_ID = UUID.randomUUID().toString();

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
  void shouldBeForbiddenFromGettingLtftFormWhenNoToken() throws Exception {
    mockMvc.perform(get("/api/ltft/formX"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeForbiddenFromGettingLtftFormWhenTokenLacksTraineeId() throws Exception {
    String token = TestJwtUtil.generateToken("{}");
    mockMvc.perform(get("/api/ltft/formX")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shoulNotFindLtftFormWhenNoneExist() throws Exception {
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(get("/api/ltft/formX")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldNotFindLtftFormNotOwnedByUser() throws Exception {
    ObjectId id = ObjectId.get();
    LtftForm ltft = LtftForm.builder()
        .id(id)
        .traineeId(UUID.randomUUID().toString())
        .build();
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(get("/api/ltft/" + id)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldFindLtftFormOwnedByUser() throws Exception {
    ObjectId id = ObjectId.get();
    LtftForm ltft = LtftForm.builder()
        .id(id)
        .traineeId(TRAINEE_ID)
        .name("name")
        .discussions(LtftForm.LtftDiscussions.builder()
            .tpdName("tpd")
            .other(List.of(
                LtftForm.LtftPersonRole.builder()
                    .name("other person")
                    .build()))
            .build())
        .build();
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(get("/api/ltft/" + id)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.traineeId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.name").value("name"))
        .andExpect(jsonPath("$.discussions.tpdName").value("tpd"))
        .andExpect(jsonPath("$.discussions.other", hasSize(1)))
        .andExpect(jsonPath("$.discussions.other[0].name").value("other person"));
  }

}
