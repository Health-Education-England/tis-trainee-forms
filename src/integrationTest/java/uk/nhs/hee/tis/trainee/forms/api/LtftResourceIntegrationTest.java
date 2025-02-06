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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
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
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class LtftResourceIntegrationTest {
  private static final String TRAINEE_ID = "40";
  private static final UUID ID = UUID.randomUUID();

  @Autowired
  private ObjectMapper mapper;

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
  void shouldBeForbiddenFromCreatingLtftFormWhenNoToken() throws Exception {
    mockMvc.perform(post("/api/ltft")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeForbiddenFromCreatingLtftFormWhenTokenLacksTraineeId() throws Exception {
    String token = TestJwtUtil.generateToken("{}");
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeForbiddenFromUpdatingLtftFormWhenNoToken() throws Exception {
    mockMvc.perform(put("/api/ltft/someId")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeForbiddenFromUpdatingLtftFormWhenTokenLacksTraineeId() throws Exception {
    String token = TestJwtUtil.generateToken("{}");
    mockMvc.perform(put("/api/ltft/someId")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldNotFindLtftFormWhenNoneExist() throws Exception {
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(get("/api/ltft/" + UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldNotFindLtftFormNotOwnedByUser() throws Exception {
    LtftForm ltft = LtftForm.builder()
        .id(ID)
        .traineeId(UUID.randomUUID().toString())
        .build();
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(get("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldFindLtftFormOwnedByUser() throws Exception {
    LtftForm ltft = LtftForm.builder()
        .id(ID)
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
    mockMvc.perform(get("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.traineeId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.name").value("name"))
        .andExpect(jsonPath("$.discussions.tpdName").value("tpd"))
        .andExpect(jsonPath("$.discussions.other", hasSize(1)))
        .andExpect(jsonPath("$.discussions.other[0].name").value("other person"));
  }

  @Test
  void shouldBeBadRequestWhenCreatingLtftFormForDifferentTrainee() throws Exception {
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
      mockMvc.perform(post("/api/ltft")
              .header(HttpHeaders.AUTHORIZATION, token)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"traineeId\": \"another id\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeBadRequestWhenCreatingLtftFormWithId() throws Exception {
    LtftFormDto formToSave = new LtftFormDto();
    formToSave.setId(ID);
    formToSave.setTraineeId(TRAINEE_ID);
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("must be null"));
  }

  @Test
  void shouldCreateLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = new LtftFormDto();
    formToSave.setTraineeId(TRAINEE_ID);
    formToSave.setName("test");
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("test"))
        .andExpect(jsonPath("$.traineeId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.created").exists())
        .andExpect(jsonPath("$.lastModified").exists());

    assertThat("Unexpected saved record count.", template.count(new Query(), LtftForm.class),
        is(1L));
    List<LtftForm> savedRecords = template.find(new Query(), LtftForm.class);
    assertThat("Unexpected saved record name.", savedRecords.get(0).getName(), is("test"));
    assertThat("Unexpected saved record trainee id.", savedRecords.get(0).getTraineeId(),
        is(TRAINEE_ID));
    assertThat("Unexpected saved record id.", savedRecords.get(0).getId(), is(notNullValue()));
  }

  @Test
  void shouldBeBadRequestWhenUpdatingLtftFormForDifferentTrainee() throws Exception {
    LtftFormDto formToUpdate = new LtftFormDto();
    formToUpdate.setTraineeId("another trainee");
    formToUpdate.setId(ID);
    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeBadRequestWhenUpdatingLtftFormWithoutId() throws Exception {
    LtftFormDto formToUpdate = new LtftFormDto();
    formToUpdate.setTraineeId(TRAINEE_ID);
    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/someId")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeBadRequestWhenUpdatingLtftFormWithInconsistentIds() throws Exception {
    LtftFormDto formToUpdate = new LtftFormDto();
    formToUpdate.setTraineeId(TRAINEE_ID);
    formToUpdate.setId(ID);
    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeBadRequestWhenUpdatingLtftFormNotAlreadyExistingForTrainee() throws Exception {
    LtftFormDto formToUpdate = new LtftFormDto();
    formToUpdate.setTraineeId(TRAINEE_ID);
    formToUpdate.setId(ID);
    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldUpdateLtftFormForTrainee() throws Exception {
    LtftForm form = LtftForm.builder()
        .id(ID)
        .traineeId(TRAINEE_ID)
        .build();
    LtftForm formSaved = template.save(form);

    LtftFormDto formToUpdate = new LtftFormDto();
    formToUpdate.setTraineeId(TRAINEE_ID);
    formToUpdate.setId(ID);
    formToUpdate.setName("updated");

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.traineeId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.name").value("updated"))
        .andExpect(jsonPath("$.created").value(
            formSaved.getCreated().truncatedTo(ChronoUnit.MILLIS).toString()))
        .andExpect(jsonPath("$.lastModified",
            greaterThan(formSaved.getLastModified().truncatedTo(ChronoUnit.MILLIS).toString())));

    assertThat("Unexpected saved record count.", template.count(new Query(), LtftForm.class),
        is(1L));
    List<LtftForm> savedRecords = template.find(new Query(), LtftForm.class);
    assertThat("Unexpected saved record name.", savedRecords.get(0).getName(), is("updated"));
    assertThat("Unexpected saved record trainee id.", savedRecords.get(0).getTraineeId(),
        is(TRAINEE_ID));
    assertThat("Unexpected saved record id.", savedRecords.get(0).getId(),
        is(formSaved.getId()));
  }
}
