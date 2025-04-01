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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.number.IsCloseTo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.StatusInfoDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Discussions;

@SpringBootTest
@ActiveProfiles("test")
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

  @MockBean
  SnsTemplate snsTemplate;

  @Autowired
  private MongoTemplate template;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), LtftForm.class);
  }


  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/ltft/formX
      """)
  void shouldReturnForbiddenWhenNoToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      POST | /api/ltft
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/submit
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/unsubmit
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/withdraw
      """)
  void shouldReturnForbiddenWhenNoTokenInUpdateRequests(HttpMethod method, URI uri)
      throws Exception {
    mockMvc.perform(request(method, uri)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      POST | /api/ltft
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/submit
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/unsubmit
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/withdraw
      """)
  void shouldReturnForbiddenWhenTokenLacksTraineeIdInUpdateRequests(HttpMethod method, URI uri)
      throws Exception {
    String token = TestJwtUtil.generateToken("{}");
    mockMvc.perform(request(method, uri)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/submit
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/unsubmit
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/withdraw
      """)
  void shouldReturnBadRequestWhenUpdatingFormOwnedByDifferentTrainee(HttpMethod method, URI uri)
      throws Exception {
    LtftFormDto form = LtftFormDto.builder()
        .id(UUID.fromString("ec5c8db7-9848-419b-85ce-c5b53b1e3794"))
        .traineeTisId("another trainee")
        .build();
    String formToUpdateJson = mapper.writeValueAsString(form);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(request(method, uri)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeBadRequestWhenCreatingLtftFormForDifferentTrainee() throws Exception {
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"traineeTisId\": \"another id\"}"))
        .andExpect(status().isBadRequest())
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
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId("another trainee");
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(get("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldFindLtftFormOwnedByUser() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);

    LtftContent content = LtftContent.builder()
        .name("name")
        .discussions(Discussions.builder()
            .tpdName("tpd")
            .other(List.of(Person.builder()
                .name("other person")
                .build()
            ))
            .build()
        )
        .build();
    ltft.setContent(content);
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(get("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.name").value("name"))
        .andExpect(jsonPath("$.discussions.tpdName").value("tpd"))
        .andExpect(jsonPath("$.discussions.other", hasSize(1)))
        .andExpect(jsonPath("$.discussions.other[0].name").value("other person"));
  }

  @Test
  void shouldBeBadRequestWhenCreatingLtftFormWithId() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type", is("about:blank")))
        .andExpect(jsonPath("$.title", is("Validation failure")))
        .andExpect(jsonPath("$.status", is(HttpStatus.BAD_REQUEST.value())))
        .andExpect(jsonPath("$.instance", is("/api/ltft")))
        .andExpect(jsonPath("$.properties.errors").isArray())
        .andExpect(jsonPath("$.properties.errors", hasSize(1)))
        .andExpect(jsonPath("$.properties.errors[0].pointer", is("#/id")))
        .andExpect(
            jsonPath("$.properties.errors[0].detail", is("must be null")));
  }

  @Test
  void shouldCreateLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("test"))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.created").exists())
        .andExpect(jsonPath("$.lastModified").exists());

    assertThat("Unexpected saved record count.", template.count(new Query(), LtftForm.class),
        is(1L));
    List<LtftForm> savedRecords = template.find(new Query(), LtftForm.class);
    assertThat("Unexpected saved record name.", savedRecords.get(0).getContent().name(),
        is("test"));
    assertThat("Unexpected saved record trainee id.", savedRecords.get(0).getTraineeTisId(),
        is(TRAINEE_ID));
    assertThat("Unexpected saved record id.", savedRecords.get(0).getId(), is(notNullValue()));
  }

  @Test
  void shouldIgnoreFormRefWhenCreatingLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .formRef("test-ref")
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formRef", nullValue()));
  }

  @Test
  void shouldIgnoreRevisionWhenCreatingLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .revision(123)
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision", is(0)));
  }

  @Test
  void shouldIgnoreAssignedAdminWhenCreatingLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .assignedAdmin(PersonDto.builder()
            .name("Ad Min")
            .email("ad.min@example.com")
            .role("ADMIN")
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignedAdmin", nullValue()));
  }

  @Test
  void shouldIgnoreStatusWhenCreatingLtftFormForTrainee() throws Exception {
    StatusInfoDto approved = StatusInfoDto.builder()
        .state(LifecycleState.APPROVED)
        .revision(123)
        .detail(LftfStatusInfoDetailDto.builder()
            .reason("test reason")
            .message("test message")
            .build())
        .timestamp(Instant.EPOCH)
        .modifiedBy(PersonDto.builder()
            .name("Trey Knee")
            .email("trey.knee@example.com")
            .role("test role")
            .build())
        .build();

    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .status(StatusDto.builder()
            .current(approved)
            .submitted(Instant.EPOCH)
            .history(List.of(approved))
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID, "anthony.gilliam@example.com",
        "Anthony", "Gilliam");
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status.current.state", is("DRAFT")))
        .andExpect(jsonPath("$.status.current.detail", nullValue()))
        .andExpect(jsonPath("$.status.current.modifiedBy.name", is("Anthony Gilliam")))
        .andExpect(jsonPath("$.status.current.modifiedBy.email", is("anthony.gilliam@example.com")))
        .andExpect(jsonPath("$.status.current.modifiedBy.role", is("TRAINEE")))
        .andExpect(jsonPath("$.status.current.timestamp",
            TimestampCloseTo.closeTo(Instant.now().getEpochSecond(), 1)))
        .andExpect(jsonPath("$.status.current.revision", is(0)))
        .andExpect(jsonPath("$.status.history", hasSize(1)))
        .andExpect(jsonPath("$.status.history[0].state", is("DRAFT")))
        .andExpect(jsonPath("$.status.history[0].detail", nullValue()))
        .andExpect(jsonPath("$.status.history[0].modifiedBy.name", is("Anthony Gilliam")))
        .andExpect(
            jsonPath("$.status.history[0].modifiedBy.email", is("anthony.gilliam@example.com")))
        .andExpect(jsonPath("$.status.history[0].modifiedBy.role", is("TRAINEE")))
        .andExpect(jsonPath("$.status.history[0].timestamp",
            TimestampCloseTo.closeTo(Instant.now().getEpochSecond(), 1)))
        .andExpect(jsonPath("$.status.history[0].revision", is(0)))
        .andExpect(jsonPath("$.status.submitted", nullValue()))
        .andReturn();
  }

  @Test
  void shouldIgnoreCreatedWhenCreatingLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .created(Instant.EPOCH)
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.created", TimestampCloseTo.closeTo(Instant.now().getEpochSecond(), 1)));
  }

  @Test
  void shouldIgnoreLastModifiedWhenCreatingLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .lastModified(Instant.EPOCH)
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastModified",
            TimestampCloseTo.closeTo(Instant.now().getEpochSecond(), 1)));
  }

  @Test
  void shouldBeBadRequestWhenUpdatingLtftFormWithoutId() throws Exception {
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .build();
    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/someId")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type", is("about:blank")))
        .andExpect(jsonPath("$.title", is("Bad Request")))
        .andExpect(jsonPath("$.status", is(HttpStatus.BAD_REQUEST.value())))
        .andExpect(jsonPath("$.detail", is("Failed to convert 'formId' with value: 'someId'")))
        .andExpect(jsonPath("$.instance", is("/api/ltft/someId")));
  }

  @Test
  void shouldBeBadRequestWhenUpdatingLtftFormWithInconsistentIds() throws Exception {
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();
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
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();
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
    LtftForm form = new LtftForm();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.DRAFT);
    LtftForm formSaved = template.save(form);

    UUID savedId = formSaved.getId();
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .id(savedId)
        .traineeTisId(TRAINEE_ID)
        .name("updated")
        .build();

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + savedId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedId.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.name").value("updated"))
        .andExpect(jsonPath("$.created").value(
            formSaved.getCreated().truncatedTo(ChronoUnit.MILLIS).toString()))
        .andExpect(jsonPath("$.lastModified",
            greaterThan(formSaved.getLastModified().toString())));

    assertThat("Unexpected saved record count.", template.count(new Query(), LtftForm.class),
        is(1L));
    List<LtftForm> savedRecords = template.find(new Query(), LtftForm.class);
    assertThat("Unexpected saved record name.", savedRecords.get(0).getContent().name(),
        is("updated"));
    assertThat("Unexpected saved record trainee id.", savedRecords.get(0).getTraineeTisId(),
        is(TRAINEE_ID));
    assertThat("Unexpected saved record id.", savedRecords.get(0).getId(),
        is(formSaved.getId()));
  }

  @Test
  void shouldIgnoreReadOnlyFieldsWhenUpdatingLtftFormForTrainee() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.DRAFT);
    LtftForm formSaved = template.save(form);

    UUID savedId = formSaved.getId();
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .id(savedId)
        .traineeTisId(TRAINEE_ID)
        .formRef("ref_123")
        .revision(3)
        .assignedAdmin(PersonDto.builder().name("Ad Min").build())
        .status(StatusDto.builder().build())
        .created(Instant.EPOCH)
        .lastModified(Instant.EPOCH)
        .build();

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + savedId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedId.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.formRef", nullValue()))
        .andExpect(jsonPath("$.revision").value(0))
        .andExpect(jsonPath("$.assignedAdmin", nullValue()))
        .andExpect(jsonPath("$.status.current.state", is("DRAFT")))
        .andExpect(jsonPath("$.created").value(
            formSaved.getCreated().truncatedTo(ChronoUnit.MILLIS).toString()))
        .andExpect(jsonPath("$.lastModified",
            greaterThan(formSaved.getLastModified().toString())));
  }

  @Test
  void shouldReturnNotFoundWhenDeletingNonExistentLtftForm() throws Exception {
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(delete("/api/ltft/" + UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"DRAFT"})
  void shouldReturnBadRequestWhenServiceCantDeleteLtftForm(LifecycleState lifecycleState)
      throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(lifecycleState);
    LtftForm formSaved = template.save(form);

    UUID savedId = formSaved.getId();
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(delete("/api/ltft/" + savedId)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldDeleteLtftForm() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.DRAFT);
    LtftForm formSaved = template.save(form);

    UUID savedId = formSaved.getId();
    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(delete("/api/ltft/" + savedId)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").doesNotExist());

    assertThat("Unexpected saved record count.", template.count(new Query(), LtftForm.class),
        is(0L));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldReturnBadRequestWhenSubmittingLtftFormInInvalidState(LifecycleState state)
      throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(state);
    ltft.setContent(LtftContent.builder().name("test").build());
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/{id}/submit", ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldSubmitLtftForm(LifecycleState state) throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(state);
    ltft.setContent(LtftContent.builder().name("test").build());
    template.insert(ltft);

    LtftFormDto.StatusDto.LftfStatusInfoDetailDto detail
        = new LtftFormDto.StatusDto.LftfStatusInfoDetailDto("reason", "message");
    String detailJson = mapper.writeValueAsString(detail);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID, "email", "given", "family");
    mockMvc.perform(put("/api/ltft/{id}/submit", ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(detailJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.formRef", notNullValue()))
        .andExpect(jsonPath("$.status.current.state").value(LifecycleState.SUBMITTED.name()))
        .andExpect(jsonPath("$.status.current.detail.reason").value("reason"))
        .andExpect(jsonPath("$.status.current.detail.message").value("message"))
        .andExpect(jsonPath("$.status.current.modifiedBy.name").value("given family"))
        .andExpect(jsonPath("$.status.current.modifiedBy.email").value("email"))
        .andExpect(jsonPath("$.status.current.modifiedBy.role").value("TRAINEE"))
        .andExpect(jsonPath("$.status.submitted", notNullValue()));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/unsubmit
      PUT  | /api/ltft/ec5c8db7-9848-419b-85ce-c5b53b1e3794/withdraw
      """)
  void shouldReturnBadRequestWhenNoRequiredDetailInUpdateRequests(HttpMethod method, URI uri)
      throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(UUID.fromString("ec5c8db7-9848-419b-85ce-c5b53b1e3794"));
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(LifecycleState.SUBMITTED);
    ltft.setContent(LtftContent.builder().name("test").build());
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);

    mockMvc.perform(request(method, uri)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"SUBMITTED"})
  void shouldReturnBadRequestWhenUnsubmittingLtftFormInInvalidState(LifecycleState state)
      throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(state);
    ltft.setContent(LtftContent.builder().name("test").build());
    template.insert(ltft);

    LtftFormDto.StatusDto.LftfStatusInfoDetailDto detail
        = new LtftFormDto.StatusDto.LftfStatusInfoDetailDto("reason", "message");
    String detailJson = mapper.writeValueAsString(detail);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/{id}/unsubmit", ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(detailJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"SUBMITTED"})
  void shouldUnsubmitLtftForm(LifecycleState state) throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(state);
    ltft.setContent(LtftContent.builder().name("test").build());
    template.insert(ltft);

    LtftFormDto.StatusDto.LftfStatusInfoDetailDto detail
        = new LtftFormDto.StatusDto.LftfStatusInfoDetailDto("reason", "message");
    String detailJson = mapper.writeValueAsString(detail);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID, "email", "given", "family");
    mockMvc.perform(put("/api/ltft/{id}/unsubmit", ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(detailJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.status.current.state").value(LifecycleState.UNSUBMITTED.name()))
        .andExpect(jsonPath("$.status.current.detail.reason").value("reason"))
        .andExpect(jsonPath("$.status.current.detail.message").value("message"))
        .andExpect(jsonPath("$.status.current.modifiedBy.name").value("given family"))
        .andExpect(jsonPath("$.status.current.modifiedBy.email").value("email"))
        .andExpect(jsonPath("$.status.current.modifiedBy.role").value("TRAINEE"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"SUBMITTED", "UNSUBMITTED"})
  void shouldReturnBadRequestWhenWithdrawingLtftFormInInvalidState(LifecycleState state)
      throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(state);
    ltft.setContent(LtftContent.builder().name("test").build());
    template.insert(ltft);

    LtftFormDto.StatusDto.LftfStatusInfoDetailDto detail
        = new LtftFormDto.StatusDto.LftfStatusInfoDetailDto("reason", "message");
    String detailJson = mapper.writeValueAsString(detail);

    String token = TestJwtUtil.generateTokenForTisId(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/{id}/withdraw", ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(detailJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"SUBMITTED", "UNSUBMITTED"})
  void shouldWithdrawLtftForm(LifecycleState state) throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(state);
    ltft.setContent(LtftContent.builder().name("test").build());
    template.insert(ltft);

    LtftFormDto.StatusDto.LftfStatusInfoDetailDto detail
        = new LtftFormDto.StatusDto.LftfStatusInfoDetailDto("reason", "message");
    String detailJson = mapper.writeValueAsString(detail);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID, "email", "given", "family");
    mockMvc.perform(put("/api/ltft/{id}/withdraw", ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(detailJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ID.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.status.current.state").value(LifecycleState.WITHDRAWN.name()))
        .andExpect(jsonPath("$.status.current.detail.reason").value("reason"))
        .andExpect(jsonPath("$.status.current.detail.message").value("message"))
        .andExpect(jsonPath("$.status.current.modifiedBy.name").value("given family"))
        .andExpect(jsonPath("$.status.current.modifiedBy.email").value("email"))
        .andExpect(jsonPath("$.status.current.modifiedBy.role").value("TRAINEE"));
  }

  /**
   * A custom matcher which wraps {@link IsCloseTo} to allow easier use with string timestamps.
   */
  private static class TimestampCloseTo extends TypeSafeMatcher<String> {

    private final IsCloseTo isCloseTo;

    /**
     * Construct a matcher that matches when a timestamp value is equal, within the error range.
     *
     * @param value The expected value of matching doubles after conversion.
     * @param error The delta (+/-) within which matches will be allowed.
     */
    TimestampCloseTo(double value, double error) {
      isCloseTo = new IsCloseTo(value, error);
    }

    @Override
    protected boolean matchesSafely(String timestamp) {
      Instant parsed = Instant.parse(timestamp);
      return isCloseTo.matchesSafely((double) parsed.getEpochSecond());
    }

    @Override
    public void describeTo(Description description) {
      isCloseTo.describeTo(description);
    }

    /**
     * Get an instance of the closeTo matcher.
     *
     * @param operand The expected value of matching doubles after conversion.
     * @param error   The delta (+/-) within which matches will be allowed.
     * @return the matcher instance.
     */
    public static Matcher<String> closeTo(double operand, double error) {
      return new TimestampCloseTo(operand, error);
    }
  }
}
