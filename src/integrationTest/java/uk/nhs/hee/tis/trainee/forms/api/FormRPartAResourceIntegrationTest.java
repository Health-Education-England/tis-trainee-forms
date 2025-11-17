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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.LocalDate;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartARepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class FormRPartAResourceIntegrationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final String TRAINEE_ID = "47165";
  private static final String FORENAME = "John";
  private static final String SURNAME = "Doe";

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

  @MockBean
  S3FormRPartARepositoryImpl s3FormRPartARepository;

  @MockBean
  SnsClient snsClient;

  @MockBean
  PdfService pdfService;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), FormRPartA.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/formr-parta/someId
      """)
  void shouldReturnForbiddenWhenNoToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      POST | /api/formr-parta
      PUT  | /api/formr-parta
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
      POST | /api/formr-parta
      PUT  | /api/formr-parta
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

  @Test
  void shouldNotFindFormNotOwnedByUser() throws Exception {
    FormRPartA form = new FormRPartA();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setForename(FORENAME);
    form.setSurname(SURNAME);
    form.setLifecycleState(LifecycleState.DRAFT);
    template.save(form);

    String token = TestJwtUtil.generateTokenForTrainee("another trainee");
    mockMvc.perform(get("/api/ltft/" + ID)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldReturnNotFoundWhenGettingNonExistentFormRPartA() throws Exception {
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(get("/api/formr-parta/" + UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldReturnForbiddenWhenCreatingFormRPartAForDifferentTrainee() throws Exception {
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-parta")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"traineeTisId\": \"another id\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldCreateFormRPartA() throws Exception {
    FormRPartADto formToSave = new FormRPartADto();
    formToSave.setTraineeTisId(TRAINEE_ID);
    formToSave.setForename(FORENAME);
    formToSave.setSurname(SURNAME);
    formToSave.setLifecycleState(LifecycleState.DRAFT);

    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-parta")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.forename").value(FORENAME))
        .andExpect(jsonPath("$.surname").value(SURNAME))
        .andExpect(jsonPath("$.lifecycleState").value("DRAFT"));

    assertThat("Unexpected saved record count.", template.count(new Query(), FormRPartA.class),
        is(1L));
  }

  @Test
  void shouldReturnBadRequestWhenCreatingFormRPartAWithId() throws Exception {
    FormRPartADto formToSave = new FormRPartADto();
    formToSave.setId(ID.toString());
    formToSave.setTraineeTisId(TRAINEE_ID);
    formToSave.setForename(FORENAME);
    formToSave.setSurname(SURNAME);
    formToSave.setLifecycleState(LifecycleState.DRAFT);
    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-parta")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldGetFormRPartAById() throws Exception {
    FormRPartA form = new FormRPartA();
    form.setTraineeTisId(TRAINEE_ID);
    form.setForename(FORENAME);
    form.setSurname(SURNAME);
    form.setLifecycleState(LifecycleState.DRAFT);
    FormRPartA savedForm = template.save(form);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(get("/api/formr-parta/" + savedForm.getId())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(savedForm.getId().toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.forename").value(FORENAME))
        .andExpect(jsonPath("$.surname").value(SURNAME))
        .andExpect(jsonPath("$.lifecycleState").value("DRAFT"));
  }

  @Test
  void shouldGetAllFormRPartAsForTrainee() throws Exception {
    FormRPartA form1 = new FormRPartA();
    form1.setTraineeTisId(TRAINEE_ID);
    form1.setForename(FORENAME);
    form1.setSurname(SURNAME);
    form1.setLifecycleState(LifecycleState.DRAFT);
    template.save(form1);

    FormRPartA form2 = new FormRPartA();
    form2.setTraineeTisId(TRAINEE_ID);
    form2.setForename("Jane");
    form2.setSurname("Smith");
    form2.setLifecycleState(LifecycleState.SUBMITTED);
    template.save(form2);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(get("/api/formr-partas")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void shouldUpdateFormRPartA() throws Exception {
    FormRPartA form = new FormRPartA();
    form.setTraineeTisId(TRAINEE_ID);
    form.setForename(FORENAME);
    form.setSurname(SURNAME);
    form.setLifecycleState(LifecycleState.DRAFT);
    FormRPartA savedForm = template.save(form);

    FormRPartADto formToUpdate = new FormRPartADto();
    formToUpdate.setId(savedForm.getId().toString());
    formToUpdate.setTraineeTisId(TRAINEE_ID);
    formToUpdate.setForename("Jane");
    formToUpdate.setSurname("Smith");
    formToUpdate.setLifecycleState(LifecycleState.DRAFT);

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(put("/api/formr-parta")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(savedForm.getId().toString()))
        .andExpect(jsonPath("$.forename").value("Jane"))
        .andExpect(jsonPath("$.surname").value("Smith"));
  }

  @Test
  void shouldReturnForbiddenWhenUpdatingFormRPartAForDifferentTrainee() throws Exception {
    FormRPartA form = new FormRPartA();
    form.setTraineeTisId("another trainee");
    FormRPartA savedForm = template.save(form);

    FormRPartADto formToUpdate = new FormRPartADto();
    formToUpdate.setId(savedForm.getId().toString());
    formToUpdate.setTraineeTisId("another trainee");

    String formToUpdateJson = mapper.writeValueAsString(formToUpdate);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(put("/api/formr-parta")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToUpdateJson))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldDeleteDraftFormRPartA() throws Exception {
    FormRPartA form = new FormRPartA();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.DRAFT);
    FormRPartA savedForm = template.save(form);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(delete("/api/formr-parta/" + savedForm.getId())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNoContent());

    assertThat("Unexpected saved record count.", template.count(new Query(), FormRPartA.class),
        is(0L));
  }

  @Test
  void shouldReturnNotFoundWhenDeletingNonExistentFormRPartA() throws Exception {
    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(delete("/api/formr-parta/" + UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldReturnBadRequestWhenDeletingSubmittedFormRPartA() throws Exception {
    FormRPartA form = new FormRPartA();
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    FormRPartA savedForm = template.save(form);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(delete("/api/formr-parta/" + savedForm.getId())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldPublishEventWhenSubmittingFormRPartA() throws Exception {
    FormRPartADto formToSave = new FormRPartADto();
    formToSave.setTraineeTisId(TRAINEE_ID);
    formToSave.setForename(FORENAME);
    formToSave.setSurname(SURNAME);
    formToSave.setWholeTimeEquivalent("1.0");
    formToSave.setGmcNumber("1234567");
    formToSave.setEmail("john.doe@example.com");
    formToSave.setTelephoneNumber("07700900000");
    formToSave.setMobileNumber("07700900001");
    formToSave.setDateOfBirth(LocalDate.parse("1990-01-01"));
    formToSave.setGender("Male");
    formToSave.setImmigrationStatus("British");
    formToSave.setQualification("MBBS");
    formToSave.setCollege("Royal College of Physicians");
    formToSave.setDateAttained(LocalDate.parse("2015-06-01"));
    formToSave.setMedicalSchool("University of London");
    formToSave.setAddress1("10 High Street");
    formToSave.setAddress2("Apartment 1");
    formToSave.setPostCode("SW1A 1AA");
    formToSave.setLocalOfficeName("London Local Office");
    formToSave.setProgrammeSpecialty("General Practice");
    formToSave.setProgrammeMembershipType("Internal");
    formToSave.setDeclarationType("Voluntary");
    formToSave.setStartDate(LocalDate.parse("2020-08-01"));
    formToSave.setTrainingGrade("ST1");
    formToSave.setLifecycleState(LifecycleState.SUBMITTED);

    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-parta")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lifecycleState").value("SUBMITTED"));

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest publishRequest = requestCaptor.getValue();
    JsonNode publishedJson = mapper.readTree(publishRequest.message());
    assertThat("Unexpected trainee ID.", publishedJson.get("traineeTisId").asText(),
        is(TRAINEE_ID));
    assertThat("Unexpected forename.", publishedJson.get("forename").asText(), is(FORENAME));
    assertThat("Unexpected surname.", publishedJson.get("surname").asText(), is(SURNAME));
    //no checks on the remaining fields

    Map<String, MessageAttributeValue> messageAttributes = publishRequest.messageAttributes();
    assertThat("Message attributes should contain formType.",
        messageAttributes.containsKey("formType"), is(true));
    assertThat("formType should be 'formr-a'.",
        messageAttributes.get("formType").stringValue(), is("formr-a"));
  }

  @Test
  void shouldNotPublishEventWhenSavingDraftFormRPartA() throws Exception {
    FormRPartADto formToSave = new FormRPartADto();
    formToSave.setTraineeTisId(TRAINEE_ID);
    formToSave.setForename(FORENAME);
    formToSave.setSurname(SURNAME);
    formToSave.setLifecycleState(LifecycleState.DRAFT);

    String formToSaveJson = mapper.writeValueAsString(formToSave);

    String token = TestJwtUtil.generateTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(post("/api/formr-parta")
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(formToSaveJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.lifecycleState").value("DRAFT"));

    verifyNoInteractions(snsClient);
  }
}
