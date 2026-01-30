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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartBRepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class FormRPartBResourceIntegrationTest {

  private static final String TRAINEE_ID = "47165";
  private static final String FORENAME = "John";
  private static final String SURNAME = "Doe";
  private static final String GMC_NUMBER = "1234567";
  private static final String EMAIL = "john.doe@example.com";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private ObjectMapper mapper;

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  SnsTemplate snsTemplate;

  @MockBean
  S3FormRPartBRepositoryImpl s3FormRPartBRepository;

  @MockBean
  SnsClient snsClient;

  @MockBean
  PdfService pdfService;

  @Autowired
  private MongoTemplate template;

  @MockBean
  private JwtDecoder jwtDecoder;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), FormRPartB.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/formr-partb/someId
      """)
  void shouldReturnForbiddenWhenNoToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      POST | /api/formr-partb
      PUT  | /api/formr-partb
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
      POST | /api/formr-partb
      PUT  | /api/formr-partb
      """)
  void shouldReturnForbiddenWhenTokenLacksTraineeIdInUpdateRequests(HttpMethod method, URI uri)
      throws Exception {
    Jwt token = TestJwtUtil.createToken("{}");
    mockMvc.perform(request(method, uri)
            .with(jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldReturnForbiddenWhenCreatingFormRPartBForDifferentTrainee() throws Exception {
    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-partb")
            .with(jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"traineeTisId\": \"another id\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldReturnNotFoundWhenGettingNonExistentFormRPartB() throws Exception {
    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(get("/api/formr-partb/" + UUID.randomUUID())
            .with(jwt().jwt(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldCreateFormRPartB() throws Exception {
    FormRPartBDto formToSave = buildMinimalFormRPartBDto(LifecycleState.DRAFT);

    String formToSaveJson = mapper.writeValueAsString(formToSave);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-partb")
            .with(jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.forename").value(FORENAME))
        .andExpect(jsonPath("$.surname").value(SURNAME))
        .andExpect(jsonPath("$.lifecycleState").value("DRAFT"));

    assertThat("Unexpected saved record count.", template.count(new Query(), FormRPartB.class),
        is(1L));
  }

  @Test
  void shouldReturnBadRequestWhenCreatingFormRPartBWithId() throws Exception {
    FormRPartBDto formToSave = buildMinimalFormRPartBDto(LifecycleState.DRAFT);
    formToSave.setId(UUID.randomUUID().toString());
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-partb")
            .with(jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldGetFormRPartBById() throws Exception {
    FormRPartB form = new FormRPartB();
    form.setTraineeTisId(TRAINEE_ID);
    form.setForename(FORENAME);
    form.setSurname(SURNAME);
    form.setLifecycleState(LifecycleState.DRAFT);
    FormRPartB savedForm = template.save(form);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(get("/api/formr-partb/" + savedForm.getId())
            .with(jwt().jwt(token)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(savedForm.getId().toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.forename").value(FORENAME))
        .andExpect(jsonPath("$.surname").value(SURNAME))
        .andExpect(jsonPath("$.lifecycleState").value("DRAFT"));
  }

  @Test
  void shouldGetAllFormRPartBsForTrainee() throws Exception {
    FormRPartB form1 = new FormRPartB();
    form1.setTraineeTisId(TRAINEE_ID);
    form1.setForename(FORENAME);
    form1.setSurname(SURNAME);
    form1.setLifecycleState(LifecycleState.DRAFT);
    template.save(form1);

    FormRPartB form2 = new FormRPartB();
    form2.setTraineeTisId(TRAINEE_ID);
    form2.setForename("Jane");
    form2.setSurname("Smith");
    form2.setLifecycleState(LifecycleState.SUBMITTED);
    template.save(form2);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(get("/api/formr-partbs")
            .with(jwt().jwt(token)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void shouldUpdateFormRPartB() throws Exception {
    FormRPartB form = new FormRPartB();
    form.setTraineeTisId(TRAINEE_ID);
    form.setForename(FORENAME);
    form.setSurname(SURNAME);
    form.setLifecycleState(LifecycleState.DRAFT);
    FormRPartB savedForm = template.save(form);

    FormRPartBDto formToUpdate = buildMinimalFormRPartBDto(LifecycleState.DRAFT);
    formToUpdate.setId(savedForm.getId().toString());
    formToUpdate.setForename("Jane");
    formToUpdate.setSurname("Smith");

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(put("/api/formr-partb")
            .with(jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedForm.getId().toString()))
        .andExpect(jsonPath("$.forename").value("Jane"))
        .andExpect(jsonPath("$.surname").value("Smith"));
  }

  @Test
  void shouldReturnForbiddenWhenUpdatingFormRPartBForDifferentTrainee() throws Exception {
    FormRPartB form = new FormRPartB();
    form.setTraineeTisId("another trainee");
    FormRPartB savedForm = template.save(form);

    FormRPartBDto formToUpdate = new FormRPartBDto();
    formToUpdate.setId(savedForm.getId().toString());
    formToUpdate.setTraineeTisId("another trainee");

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(put("/api/formr-partb")
            .with(jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldDeleteDraftFormRPartB() throws Exception {
    FormRPartB form = new FormRPartB();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.DRAFT);
    FormRPartB savedForm = template.save(form);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(delete("/api/formr-partb/" + savedForm.getId())
            .with(jwt().jwt(token)))
        .andExpect(status().isNoContent());

    assertThat("Unexpected saved record count.", template.count(new Query(), FormRPartB.class),
        is(0L));
  }

  @Test
  void shouldReturnNotFoundWhenDeletingNonExistentFormRPartB() throws Exception {
    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(delete("/api/formr-partb/" + UUID.randomUUID())
            .with(jwt().jwt(token)))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnBadRequestWhenDeletingSubmittedFormRPartB() throws Exception {
    FormRPartB form = new FormRPartB();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    FormRPartB savedForm = template.save(form);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(delete("/api/formr-partb/" + savedForm.getId())
            .with(jwt().jwt(token)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldPublishEventWhenSubmittingFormRPartB() throws Exception {
    FormRPartBDto formToSave = buildCompleteFormRPartBDto(LifecycleState.SUBMITTED);

    String formToSaveJson = mapper.writeValueAsString(formToSave);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-partb")
            .with(jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lifecycleState").value("SUBMITTED"));

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient, times(2)).publish(requestCaptor.capture());

    PublishRequest publishRequest1 = requestCaptor.getAllValues().get(0);
    JsonNode publishedJson1 = mapper.readTree(publishRequest1.message());

    PublishRequest publishRequest2 = requestCaptor.getAllValues().get(1);
    JsonNode publishedJson2 = mapper.readTree(publishRequest2.message());

    for (JsonNode contentNode : List.of(publishedJson1, publishedJson2.get("formContentDto"))) {
      assertThat("Unexpected trainee ID.", contentNode.get("traineeTisId").asText(),
          is(TRAINEE_ID));
      assertThat("Unexpected forename.", contentNode.get("forename").asText(), is(FORENAME));
      assertThat("Unexpected surname.", contentNode.get("surname").asText(), is(SURNAME));
      //no checks on the remaining fields
    }

    Map<String, MessageAttributeValue> messageAttributes = publishRequest1.messageAttributes();
    assertThat("Message attributes should contain formType.",
        messageAttributes.containsKey("formType"), is(true));
    assertThat("formType should be 'formr-b'.",
        messageAttributes.get("formType").stringValue(), is("formr-b"));

    messageAttributes = publishRequest2.messageAttributes();
    assertThat("Message attributes should contain event_type.",
        messageAttributes.containsKey("event_type"), is(true));
    assertThat("eventType should be 'formr-b'.",
        messageAttributes.get("event_type").stringValue(), is("FORM_R"));
  }

  @Test
  void shouldNotPublishEventWhenSavingDraftFormRPartB() throws Exception {
    FormRPartBDto formToSave = buildMinimalFormRPartBDto(LifecycleState.DRAFT);

    String formToSaveJson = mapper.writeValueAsString(formToSave);

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-partb")
            .with(jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lifecycleState").value("DRAFT"));

    verifyNoInteractions(snsClient);
  }

  /**
   * Build a minimal FormRPartBDto for test purposes (DRAFT only).
   *
   * @param lifecycleState The lifecycle state for the form.
   * @return the minimal Form R Part B DTO.
   */
  private FormRPartBDto buildMinimalFormRPartBDto(LifecycleState lifecycleState) {
    FormRPartBDto dto = new FormRPartBDto();
    dto.setTraineeTisId(TRAINEE_ID);
    dto.setForename(FORENAME);
    dto.setSurname(SURNAME);
    dto.setLifecycleState(lifecycleState);
    return dto;
  }

  /**
   * Build a complete FormRPartBDto for test purposes (SUBMITTED).
   *
   * @param lifecycleState The lifecycle state for the form.
   * @return the complete Form R Part B DTO with all required fields.
   */
  private FormRPartBDto buildCompleteFormRPartBDto(LifecycleState lifecycleState) {
    FormRPartBDto dto = new FormRPartBDto();
    dto.setTraineeTisId(TRAINEE_ID);
    dto.setForename(FORENAME);
    dto.setSurname(SURNAME);
    dto.setGmcNumber(GMC_NUMBER);
    dto.setEmail(EMAIL);
    dto.setLocalOfficeName("London Local Office");
    dto.setCurrRevalDate(LocalDate.now().plusYears(1));
    dto.setProgrammeSpecialty("General Practice");

    // Work
    List<WorkDto> work = new ArrayList<>();
    WorkDto workDto = new WorkDto();
    workDto.setStartDate(LocalDate.now());
    workDto.setEndDate(LocalDate.now().plusMonths(1));
    workDto.setTypeOfWork("work type");
    workDto.setTrainingPost("Training Post");
    workDto.setSite("Site Name");
    workDto.setSiteLocation("Site Location");
    work.add(workDto);
    dto.setWork(work);

    // Leave
    dto.setSicknessAbsence(0);
    dto.setParentalLeave(0);
    dto.setCareerBreaks(0);
    dto.setPaidLeave(0);
    dto.setUnauthorisedLeave(0);
    dto.setOtherLeave(0);
    dto.setTotalLeave(0);

    // Declarations
    dto.setIsHonest(true);
    dto.setIsHealthy(true);
    dto.setIsWarned(false);
    dto.setIsComplying(true);
    dto.setHavePreviousDeclarations(false);
    dto.setPreviousDeclarations(new ArrayList<>());
    dto.setHavePreviousUnresolvedDeclarations(false);
    dto.setHaveCurrentDeclarations(false);
    dto.setCurrentDeclarations(new ArrayList<>());
    dto.setHaveCurrentUnresolvedDeclarations(false);
    dto.setHaveCovidDeclarations(false);

    dto.setLifecycleState(lifecycleState);
    return dto;
  }
}

