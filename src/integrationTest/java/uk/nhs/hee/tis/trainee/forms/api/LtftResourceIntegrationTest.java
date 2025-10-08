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

import static java.lang.Thread.sleep;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;
import static org.springframework.http.MediaType.APPLICATION_PDF;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.hee.tis.trainee.forms.TestJwtUtil.FEATURES_LTFT_PROGRAMME_INCLUDED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.VALID;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.StatusInfoDto;
import uk.nhs.hee.tis.trainee.forms.dto.RedactedPersonDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChangeType;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.ProgrammeMembership;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Discussions;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class LtftResourceIntegrationTest {

  private static final String DBC_1 = "1-abc123";
  private static final String TRAINEE_ID = "40";
  private static final UUID ID = UUID.randomUUID();
  private static final UUID PM_UUID = FEATURES_LTFT_PROGRAMME_INCLUDED;

  @Autowired
  private ObjectMapper mapper;

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MockMvc mockMvc;

  @Value("${application.timezone}")
  private ZoneId timezone;

  @MockBean
  SnsTemplate snsTemplate;

  @Autowired
  private MongoTemplate template;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), LtftForm.class);
    template.findAllAndRemove(new Query(), LtftSubmissionHistory.class);
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
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(request(method, uri)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeBadRequestWhenCreatingLtftFormForDifferentTrainee() throws Exception {
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"traineeTisId\": \"another id\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldBeBadRequestWhenCreatingLtftFormForNonLtftProgrammeMembership() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(UUID.randomUUID())
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldNotFindLtftFormWhenNoneExist() throws Exception {
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
  void shouldExcludeAdminDetailsWhenLtftFormFound() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setContent(LtftContent.builder().name("name").build());

    Person admin = Person.builder()
        .name("assigned admin")
        .email("assigned.admin@example.com")
        .role("ADMIN")
        .build();
    ltft.setAssignedAdmin(admin, admin);
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(get("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id", is(ID.toString())))
        .andExpect(jsonPath("$.status.current.assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.role")
            .value("ADMIN"))
        .andExpect(jsonPath("$.status.history[0].assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.role")
            .value("ADMIN"));
  }

  @Test
  void shouldBeBadRequestWhenCreatingLtftFormWithId() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .revision(123)
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);

    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision", is(0)));
  }

  @Test
  void shouldIgnoreAssignedAdminWhenCreatingLtftFormForTrainee() throws Exception {
    RedactedPersonDto admin = RedactedPersonDto.builder()
        .name("assigned admin")
        .email("assigned.admin@example.com")
        .role("ADMIN")
        .build();
    StatusInfoDto current = StatusInfoDto.builder()
        .assignedAdmin(admin)
        .modifiedBy(admin)
        .build();

    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .status(StatusDto.builder()
            .current(current)
            .history(List.of(current))
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status.current.assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.role")
            .value("TRAINEE"))
        .andExpect(jsonPath("$.status.history[0].assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.role")
            .value("TRAINEE"));
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
        .build();

    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
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
        .andExpect(jsonPath("$.status.current.detail.reason", nullValue()))
        .andExpect(jsonPath("$.status.current.detail.message", nullValue()))
        .andExpect(jsonPath("$.status.current.timestamp",
            TimestampCloseTo.closeTo(Instant.now().getEpochSecond(), 1)))
        .andExpect(jsonPath("$.status.current.revision", is(0)))
        .andExpect(jsonPath("$.status.history", hasSize(1)))
        .andExpect(jsonPath("$.status.history[0].state", is("DRAFT")))
        .andExpect(jsonPath("$.status.history[0].detail.reason", nullValue()))
        .andExpect(jsonPath("$.status.history[0].detail.message", nullValue()))
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
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .created(Instant.EPOCH)
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.created", TimestampCloseTo.closeTo(Instant.now().getEpochSecond(), 1)));
  }

  @Test
  void shouldExcludeAdminDetailsWhenLtftFormCreated() throws Exception {
    RedactedPersonDto admin = RedactedPersonDto.builder()
        .name("assigned admin")
        .email("assigned.admin@example.com")
        .role("ADMIN")
        .build();
    StatusInfoDto current = StatusInfoDto.builder()
        .assignedAdmin(admin)
        .modifiedBy(admin)
        .build();

    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .status(StatusDto.builder()
            .current(current)
            .history(List.of(current))
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("test"))
        .andExpect(jsonPath("$.status.current.assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.role")
            .value("TRAINEE"))
        .andExpect(jsonPath("$.status.history[0].assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.role")
            .value("TRAINEE"));
  }

  @Test
  void shouldIgnoreLastModifiedWhenCreatingLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .lastModified(Instant.EPOCH)
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastModified",
            TimestampCloseTo.closeTo(Instant.now().getEpochSecond(), 1)));
  }

  @Test
  void shouldIgnoreTpdEmailStatusWhenCreatingLtftFormForTrainee() throws Exception {
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .tpdEmailStatus(VALID)
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tpdEmailStatus", nullValue()));
  }

  @Test
  void shouldBeBadRequestWhenUpdatingLtftFormWithoutId() throws Exception {
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .build();
    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .name("updated")
        .build();

    sleep(10); // Ensure lastModified is different from created
    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
  void shouldNotUpdateLtftFormForTraineeIfNotLtftProgrammeMembership() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.DRAFT);
    LtftForm formSaved = template.save(form);

    UUID savedId = formSaved.getId();
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .id(savedId)
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(UUID.randomUUID())
            .build())
        .name("updated")
        .build();

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + savedId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldIgnoreReadOnlyFieldsWhenUpdatingLtftFormForTrainee() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.DRAFT);
    LtftForm formSaved = template.save(form);

    RedactedPersonDto admin = RedactedPersonDto.builder()
        .name("assigned admin")
        .email("assigned.admin@example.com")
        .role("ADMIN")
        .build();
    StatusInfoDto current = StatusInfoDto.builder()
        .assignedAdmin(admin)
        .modifiedBy(admin)
        .build();

    UUID savedId = formSaved.getId();
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .id(savedId)
        .traineeTisId(TRAINEE_ID)
        .formRef("ref_123")
        .revision(3)
        .status(StatusDto.builder()
            .current(current)
            .history(List.of(current))
            .build())
        .created(Instant.EPOCH)
        .lastModified(Instant.EPOCH)
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .tpdEmailStatus(VALID)
        .build();

    sleep(10); // Ensure lastModified is different from created
    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + savedId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedId.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.formRef", nullValue()))
        .andExpect(jsonPath("$.revision").value(0))
        .andExpect(jsonPath("$.status.current.state", is("DRAFT")))
        .andExpect(jsonPath("$.status.current.assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.tpdEmailStatus").doesNotExist())
        .andExpect(jsonPath("$.created").value(
            formSaved.getCreated().truncatedTo(ChronoUnit.MILLIS).toString()))
        .andExpect(jsonPath("$.lastModified",
            greaterThan(formSaved.getLastModified().toString())));
  }

  @Test
  void shouldExcludeAdminDetailsWhenLtftFormUpdated() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.DRAFT);

    Person admin = Person.builder()
        .name("assigned admin")
        .email("assigned.admin@example.com")
        .role("ADMIN")
        .build();
    form.setAssignedAdmin(admin, admin);
    LtftForm formSaved = template.save(form);

    UUID savedId = formSaved.getId();
    LtftFormDto formToUpdate = LtftFormDto.builder()
        .id(savedId)
        .traineeTisId(TRAINEE_ID)
        .name("updated")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/" + savedId)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedId.toString()))
        .andExpect(jsonPath("$.status.current.assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.role")
            .value("ADMIN"))
        .andExpect(jsonPath("$.status.history[0].assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.email").doesNotExist());
  }

  @Test
  void shouldReturnNotFoundWhenDeletingNonExistentLtftForm() throws Exception {
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
        .andExpect(jsonPath("$.status.current.state").value(SUBMITTED.name()))
        .andExpect(jsonPath("$.status.current.detail.reason").value("reason"))
        .andExpect(jsonPath("$.status.current.detail.message").value("message"))
        .andExpect(jsonPath("$.status.submitted", notNullValue()));
  }

  @Test
  void shouldExcludeAdminDetailsWhenLtftFormSubmitted() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(LifecycleState.DRAFT);
    ltft.setContent(LtftContent.builder().name("test").build());

    Person admin = Person.builder()
        .name("assigned admin")
        .email("assigned.admin@example.com")
        .role("ADMIN")
        .build();
    ltft.setAssignedAdmin(admin, admin);
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
        .andExpect(jsonPath("$.status.current.assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.role")
            .value("TRAINEE"))
        .andExpect(jsonPath("$.status.history[0].assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.email").doesNotExist());
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
    ltft.setLifecycleState(SUBMITTED);
    ltft.setContent(LtftContent.builder().name("test").build());
    template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);

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

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(put("/api/ltft/{id}/unsubmit", ID)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(detailJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"SUBMITTED"})
  void shouldUnsubmitLtftFormAndIncrementRevision(LifecycleState state) throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(state);
    ltft.setContent(LtftContent.builder().name("test").build());
    ltft.setRevision(0);
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
        .andExpect(jsonPath("$.revision").value("1"))
        .andExpect(jsonPath("$.status.current.state").value(UNSUBMITTED.name()))
        .andExpect(jsonPath("$.status.current.revision").value("1"))
        .andExpect(jsonPath("$.status.current.detail.reason").value("reason"))
        .andExpect(jsonPath("$.status.current.detail.message").value("message"));
  }

  @Test
  void shouldExcludeAdminDetailsWhenLtftFormUnsubmitted() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(SUBMITTED);
    ltft.setContent(LtftContent.builder().name("test").build());
    ltft.setRevision(0);

    Person admin = Person.builder()
        .name("assigned admin")
        .email("assigned.admin@example.com")
        .role("ADMIN")
        .build();
    ltft.setAssignedAdmin(admin, admin);
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
        .andExpect(jsonPath("$.status.current.assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.role")
            .value("TRAINEE"))
        .andExpect(jsonPath("$.status.history[0].assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.email").doesNotExist());
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

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
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
        .andExpect(jsonPath("$.status.current.detail.message").value("message"));
  }

  @Test
  void shouldExcludeAdminDetailsWhenLtftFormWithdrawn() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setId(ID);
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setLifecycleState(SUBMITTED);
    ltft.setContent(LtftContent.builder().name("test").build());

    Person admin = Person.builder()
        .name("assigned admin")
        .email("assigned.admin@example.com")
        .role("ADMIN")
        .build();
    ltft.setAssignedAdmin(admin, admin);
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
        .andExpect(jsonPath("$.status.current.assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.email").doesNotExist())
        .andExpect(jsonPath("$.status.current.modifiedBy.role")
            .value("TRAINEE"))
        .andExpect(jsonPath("$.status.history[0].assignedAdmin").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.name").doesNotExist())
        .andExpect(jsonPath("$.status.history[0].modifiedBy.email").doesNotExist());
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

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldReturnPdfAndShowCorrectStatusForAllNonDraftLtftStatus(LifecycleState status) throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setFormRef("ltft_47165_001");

    LtftContent content = LtftContent.builder()
        .name("Reducing Hours")
        .personalDetails(LtftContent.PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder().build())
        .change(CctChange.builder().build())
        .reasons(LtftContent.Reasons.builder().build())
        .declarations(LtftContent.Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .build();
    ltft.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    AbstractAuditedForm.Status.StatusInfo statusInfo = AbstractAuditedForm.Status.StatusInfo.builder()
        .state(status)
        .assignedAdmin(Person.builder().build())
        .timestamp(Instant.now())
        .build();
    ltft.setStatus(AbstractAuditedForm.Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            AbstractAuditedForm.Status.StatusInfo.builder().state(status).timestamp(latestSubmitted).build()))
        .build()
    );

    ltft = template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    MvcResult result = mockMvc.perform(get("/api/ltft/" + ltft.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = Loader.loadPDF(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected sub title.", pdfText,
        containsString(status + " Application" + System.lineSeparator()));
    DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm (z)");
    String modifiedString = ZonedDateTime.ofInstant(ltft.getLastModified(), timezone)
        .format(datePattern);
    if (status == DRAFT) {
      assertThat("Unexpected modified timestamp.", pdfText,
          not(containsString(status + " date " + modifiedString + System.lineSeparator())));
      assertThat("Unexpected form ref.", pdfText,
          not(containsString("Reference")));
    }
    else {
      assertThat("Unexpected modified timestamp.", pdfText,
          containsString(status + " date " + modifiedString + System.lineSeparator()));
    }
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"UNSUBMITTED", "WITHDRAWN", "REJECTED"})
  void shouldGetDetailPdfFormStatusReasonIfLtftUnsubmittedRejected(LifecycleState status) throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setFormRef("ltft_47165_001");

    LtftContent content = LtftContent.builder()
        .name("Reducing Hours")
        .personalDetails(LtftContent.PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder().build())
        .change(CctChange.builder().build())
        .reasons(LtftContent.Reasons.builder().build())
        .declarations(LtftContent.Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .build();
    ltft.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    AbstractAuditedForm.Status.StatusInfo statusInfo = AbstractAuditedForm.Status.StatusInfo.builder()
        .state(status)
        .assignedAdmin(Person.builder().build())
        .detail(AbstractAuditedForm.Status.StatusDetail.builder()
            .reason("changePercentage")
            .message("Testing Message")
            .build())
        .modifiedBy(Person.builder()
            .role("TRAINEE").build())
        .timestamp(Instant.now())
        .build();
    ltft.setStatus(AbstractAuditedForm.Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            AbstractAuditedForm.Status.StatusInfo.builder().state(status).timestamp(latestSubmitted).build()))
        .build()
    );

    ltft = template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    MvcResult result = mockMvc.perform(get("/api/ltft/" + ltft.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = Loader.loadPDF(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected header.", pdfText,
        startsWith("Changing hours (LTFT)" + System.lineSeparator()));
    assertThat("Unexpected sub title.", pdfText,
        containsString(status + " Application" + System.lineSeparator()));
    assertThat("Unexpected name.", pdfText,
        containsString("Name Reducing Hours" + System.lineSeparator()));

    DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm (z)");
    String createdString = ZonedDateTime.ofInstant(ltft.getCreated(), timezone).format(datePattern);
    assertThat("Unexpected created timestamp.", pdfText,
        containsString("Created date " + createdString + System.lineSeparator()));

    String modifiedString = ZonedDateTime.ofInstant(ltft.getLastModified(), timezone)
        .format(datePattern);
    assertThat("Unexpected modified timestamp.", pdfText,
        containsString(status + " date " + modifiedString + System.lineSeparator()));
    assertThat("Unexpected modified by.", pdfText,
        containsString(status + " by Me" + System.lineSeparator()));
    assertThat("Unexpected status reason.", pdfText,
        containsString("Reason Change WTE percentage" + System.lineSeparator()));
    assertThat("Unexpected status message.", pdfText,
        containsString("Message Testing Message" + System.lineSeparator()));
    assertThat("Unexpected form ref.", pdfText,
        containsString("Reference ltft_47165_001" + System.lineSeparator()));
  }

  @Test
  void shouldGetDetailPdfProgrammeDetails() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setAssignedAdmin(Person.builder().build(), null);

    LocalDate startDate = LocalDate.now();
    LocalDate endDate = startDate.plusYears(1);
    LocalDate changeStartDate = startDate.plusMonths(1);
    LocalDate cctDate = endDate.plusYears(2);

    LtftContent content = LtftContent.builder()
        .personalDetails(LtftContent.PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .name("General Practice")
            .startDate(startDate)
            .endDate(endDate)
            .wte(0.85)
            .build())
        .change(CctChange.builder()
            .type(CctChangeType.LTFT)
            .startDate(changeStartDate)
            .endDate(endDate)
            .wte(0.75)
            .cctDate(cctDate)
            .build())
        .reasons(LtftContent.Reasons.builder().build())
        .declarations(LtftContent.Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .build();
    ltft.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    AbstractAuditedForm.Status.StatusInfo statusInfo = AbstractAuditedForm.Status.StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    ltft.setStatus(AbstractAuditedForm.Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            AbstractAuditedForm.Status.StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    ltft = template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    MvcResult result = mockMvc.perform(get("/api/ltft/" + ltft.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = Loader.loadPDF(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("CCT Calculation Summary" + System.lineSeparator()));
    assertThat("Unexpected sub header.", pdfText,
        containsString("Linked Programme" + System.lineSeparator()));
    assertThat("Unexpected name.", pdfText,
        containsString("Programme Name General Practice" + System.lineSeparator()));
    DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    String startDateString = startDate.format(datePattern);
    assertThat("Unexpected start date.", pdfText,
        containsString("Start Date " + startDateString + System.lineSeparator()));
    String endDateString = endDate.format(datePattern);
    assertThat("Unexpected end date.", pdfText,
        containsString("Completion Date " + endDateString + System.lineSeparator()));

    assertThat("Unexpected sub header.", pdfText,
        containsString("Current WTE percentage" + System.lineSeparator()));
    assertThat("Unexpected current wte.", pdfText,
        containsString("WTE 85%" + System.lineSeparator()));

    assertThat("Unexpected sub header.", pdfText,
        containsString("Proposed Changes" + System.lineSeparator()));
    assertThat("Unexpected change type.", pdfText,
        containsString("Change Type Changing hours (LTFT)" + System.lineSeparator()));
    String changeStartDateString = changeStartDate.format(datePattern);
    assertThat("Unexpected change date.", pdfText,
        containsString("Change Date " + changeStartDateString + System.lineSeparator()));
    assertThat("Unexpected proposed wte.", pdfText,
        containsString("Proposed WTE 75%" + System.lineSeparator()));
    String cctDateString = cctDate.format(datePattern);
    assertThat("Unexpected new completion date.", pdfText,
        containsString("New Completion Date " + cctDateString + System.lineSeparator()));
  }

  @Test
  void shouldGetDetailPdfDiscussionsDetails() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setTraineeTisId(TRAINEE_ID);

    LtftContent content = LtftContent.builder()
        .personalDetails(LtftContent.PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder().build())
        .change(CctChange.builder().build())
        .reasons(LtftContent.Reasons.builder().build())
        .declarations(LtftContent.Declarations.builder().build())
        .discussions(Discussions.builder()
            .tpdName("Tee Pee-Dee")
            .tpdEmail("tpd@example.com")
            .other(List.of(
                Person.builder()
                    .name("Ed Super")
                    .email("ed.super@example.com")
                    .role("Educational Supervisor")
                    .build(),
                Person.builder()
                    .name("Person Two")
                    .email("person.2@example.com")
                    .role("Test Data")
                    .build()
            ))
            .build())
        .build();
    ltft.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    AbstractAuditedForm.Status.StatusInfo statusInfo = AbstractAuditedForm.Status.StatusInfo.builder()
        .state(SUBMITTED)
        .assignedAdmin(Person.builder().build())
        .timestamp(Instant.now())
        .build();
    ltft.setStatus(AbstractAuditedForm.Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            AbstractAuditedForm.Status.StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    ltft = template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    MvcResult result = mockMvc.perform(get("/api/ltft/" + ltft.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = Loader.loadPDF(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Discussing your proposals" + System.lineSeparator()));
    assertThat("Unexpected TPD name.", pdfText,
        containsString("TPD Name Tee Pee-Dee" + System.lineSeparator()));
    assertThat("Unexpected TPD email.", pdfText,
        containsString("TPD Email Address tpd@example.com" + System.lineSeparator()));
    assertThat("Unexpected other discussions.", pdfText, containsString(
        "Other Discussions Name: Ed Super"
            + System.lineSeparator() + "Email: ed.super@example.com"
            + System.lineSeparator() + "Role: Educational Supervisor"
            + System.lineSeparator() + "Name: Person Two"
            + System.lineSeparator() + "Email: person.2@example.com"
            + System.lineSeparator() + "Role: Test Data" + System.lineSeparator()));
  }

  @Test
  void shouldGetDetailPdfReasonDetails() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setAssignedAdmin(Person.builder().build(), null);

    LtftContent content = LtftContent.builder()
        .personalDetails(LtftContent.PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder().build())
        .change(CctChange.builder().build())
        .reasons(LtftContent.Reasons.builder()
            .selected(List.of("Test1", "Test2", "Other"))
            .otherDetail("other-detail")
            .supportingInformation("supporting-information")
            .build())
        .declarations(LtftContent.Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .build();
    ltft.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    AbstractAuditedForm.Status.StatusInfo statusInfo = AbstractAuditedForm.Status.StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    ltft.setStatus(AbstractAuditedForm.Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            AbstractAuditedForm.Status.StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    ltft = template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    MvcResult result = mockMvc.perform(get("/api/ltft/" + ltft.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = Loader.loadPDF(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Reason(s) for applying" + System.lineSeparator()));
    assertThat("Unexpected visa holder.", pdfText.replaceAll("[\r\n]+", ""),
        containsString("Why are you applying for Changing hours (LTFT)?Test1Test2Other"));
    assertThat("Unexpected other reason.", pdfText,
        containsString("Other reason other-detail" + System.lineSeparator()));
    assertThat("Unexpected visa holder.", pdfText.replaceAll("[\r\n]+", ""),
        containsString("Please provide any additional information to support your application (if needed).supporting-information"));
  }

  @Test
  void shouldGetDetailPdfPersonalDetails() throws Exception {
      LtftForm ltft = new LtftForm();
      ltft.setTraineeTisId(TRAINEE_ID);

      LtftContent content = LtftContent.builder()
          .personalDetails(LtftContent.PersonalDetails.builder()
              .title("Dr")
              .forenames("Anthony")
              .surname("Gilliam")
              .email("anthony.gilliam@example.com")
              .gmcNumber("1234567")
              .gdcNumber("D123456")
              .telephoneNumber("07700900000")
              .mobileNumber("07700900001")
              .skilledWorkerVisaHolder(true)
              .build())
          .programmeMembership(ProgrammeMembership.builder().build())
          .change(CctChange.builder().build())
          .reasons(LtftContent.Reasons.builder().build())
          .declarations(LtftContent.Declarations.builder().build())
          .discussions(LtftContent.Discussions.builder().build())
          .build();
      ltft.setContent(content);

      Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

      AbstractAuditedForm.Status.StatusInfo statusInfo = AbstractAuditedForm.Status.StatusInfo.builder()
          .state(SUBMITTED)
          .assignedAdmin(Person.builder().build())
          .timestamp(Instant.now())
          .build();
      ltft.setStatus(AbstractAuditedForm.Status.builder()
            .current(statusInfo)
            .history(List.of(
                statusInfo,
                AbstractAuditedForm.Status.StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
            .build()
        );

      ltft = template.insert(ltft);

      String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
      MvcResult result = mockMvc.perform(get("/api/ltft/" + ltft.getId())
              .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
              .header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk())
          .andExpect(content().contentType(APPLICATION_PDF))
          .andReturn();

      byte[] response = result.getResponse().getContentAsByteArray();
      PDDocument pdf = Loader.loadPDF(response);
      PDFTextStripper textStripper = new PDFTextStripper();
      textStripper.setAddMoreFormatting(false);
      String pdfText = textStripper.getText(pdf);

      assertThat("Unexpected section header.", pdfText,
          containsString("Personal Details" + System.lineSeparator()));
      assertThat("Unexpected forename.", pdfText,
          containsString("Forename Anthony" + System.lineSeparator()));
      assertThat("Unexpected surname.", pdfText,
          containsString("Surname (GMC-Registered) Gilliam" + System.lineSeparator()));
      assertThat("Unexpected contact telphone.", pdfText,
          containsString("Contact Telephone 07700900000" + System.lineSeparator()));
      assertThat("Unexpected contact mobile.", pdfText,
          containsString("Contact Mobile 07700900001" + System.lineSeparator()));
    assertThat("Unexpected email.", pdfText,
        containsString("Email Address anthony.gilliam@example.com" + System.lineSeparator()));
      assertThat("Unexpected GMC.", pdfText,
          containsString("GMC Number 1234567" + System.lineSeparator()));
      assertThat("Unexpected GDC.", pdfText,
          containsString("GDC Number (if applicable) D123456" + System.lineSeparator()));
      assertThat("Unexpected visa holder.", pdfText.replaceAll("[\r\n]+", ""),
          containsString("Are you a Tier 2 / Skilled Worker Visa holder?true"));
  }

  @Test
  void shouldGetDetailPdfDeclarationDetails() throws Exception {
    LtftForm ltft = new LtftForm();
    ltft.setTraineeTisId(TRAINEE_ID);
    ltft.setAssignedAdmin(Person.builder().build(), null);

    LtftContent content = LtftContent.builder()
        .personalDetails(LtftContent.PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder().build())
        .reasons(LtftContent.Reasons.builder().build())
        .declarations(LtftContent.Declarations.builder()
            .discussedWithTpd(true)
            .informationIsCorrect(true)
            .notGuaranteed(true)
            .build())
        .discussions(Discussions.builder().build())
        .build();
    ltft.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    AbstractAuditedForm.Status.StatusInfo statusInfo = AbstractAuditedForm.Status.StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    ltft.setStatus(AbstractAuditedForm.Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            AbstractAuditedForm.Status.StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    ltft = template.insert(ltft);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    MvcResult result = mockMvc.perform(get("/api/ltft/" + ltft.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = Loader.loadPDF(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Declarations" + System.lineSeparator()));
    assertThat("Unexpected declaration.", pdfText.replaceAll("[\r\n]+", ""),
        containsString("I confirm that the information I have provided is correct and accurate to the best of my knowledge.true"));
    assertThat("Unexpected declaration.", pdfText.replaceAll("[\r\n]+", ""),
        containsString("I have discussed the proposals outlined in the CCT Calculation with my Training Programme Director (TPD).true"));
    assertThat("Unexpected declaration.", pdfText.replaceAll("[\r\n]+", ""),
        containsString("I understand that approval of my application is not guaranteed.true"));
  }

  @Test
  void shouldMoveLtftForms() throws Exception {
    LtftForm form = new LtftForm();
    String fromTraineeId = "40";
    form.setTraineeTisId(fromTraineeId);
    template.insert(form);
    LtftForm form2 = new LtftForm();
    form2.setTraineeTisId(fromTraineeId);
    template.insert(form2);

    String toTraineeId = "50";

    //note, no authorization required for this operation
    mockMvc.perform(patch("/api/ltft/move/{fromTraineeId}/to/{toTraineeId}",
            fromTraineeId, toTraineeId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.ltft").value(2))
        .andExpect(jsonPath("$.ltft-submission").value(0));

    List<LtftForm> formsForFromTrainee = template.find(
        Query.query(Criteria.where("traineeTisId").is(fromTraineeId)),
        LtftForm.class);
    List<LtftForm> formsForToTrainee = template.find(
        Query.query(Criteria.where("traineeTisId").is(toTraineeId)),
        LtftForm.class);

    assertThat("Form should be removed from source trainee",
        formsForFromTrainee, hasSize(0));
    assertThat("Form should be moved to target trainee",
        formsForToTrainee, hasSize(2));
  }

  @Test
  void shouldMoveSubmissionHistory() throws Exception {
    // Create forms for source trainee
    String fromTraineeId = TRAINEE_ID;
    String toTraineeId = "50";

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);

    // Create and submit form
    LtftFormDto formToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();
    String formToSaveJson = mapper.writeValueAsString(formToSave);
    mockMvc.perform(post("/api/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isOk());

    List<LtftForm> savedRecords = template.find(new Query(), LtftForm.class);
    UUID form1id = savedRecords.get(0).getId();

    String reasonJson = "{\"reason\": \"its ok\", \"message\": \"some message\"}";
    mockMvc.perform(put("/api/ltft/{id}/submit", form1id)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(reasonJson))
        .andExpect(status().isOk());

    List<LtftSubmissionHistory> historyForFromTrainee = template.find(
        Query.query(Criteria.where("traineeTisId").is(fromTraineeId)),
        LtftSubmissionHistory.class);
    assertThat("Submission history should exist for source trainee",
        historyForFromTrainee, hasSize(1));

    mockMvc.perform(patch("/api/ltft/move/{fromTraineeId}/to/{toTraineeId}",
            fromTraineeId, toTraineeId))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.ltft").value(1))
        .andExpect(jsonPath("$.ltft-submission").value(1));

    // Verify submission history was moved
    List<LtftSubmissionHistory> historyForFromTraineePostMove = template.find(
        Query.query(Criteria.where("traineeTisId").is(fromTraineeId)),
        LtftSubmissionHistory.class);
    List<LtftSubmissionHistory> historyForToTrainee = template.find(
        Query.query(Criteria.where("traineeTisId").is(toTraineeId)),
        LtftSubmissionHistory.class);

    assertThat("Submission history should be removed from source trainee",
        historyForFromTraineePostMove, hasSize(0));
    assertThat("Submission history should be moved to target trainee",
        historyForToTrainee, hasSize(1));
  }
}
