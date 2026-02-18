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
 *
 */

package uk.nhs.hee.tis.trainee.forms.api;

import static java.lang.Math.abs;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class AdminFormRPartBResourceIntegrationTest {

  private static final UUID FORM_ID = UUID.randomUUID();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();

  private static final String DBC_1 = "1-abc123";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MongoTemplate template;

  @MockitoBean
  private SnsTemplate snsTemplate;

  @MockitoBean
  private S3Client s3Client;

  @MockitoBean
  SnsClient snsClient;

  @BeforeEach
  void setUp() {
    snsClient = mock(SnsClient.class);
  }

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), FormRPartB.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-partb/123
      PUT    | /api/admin/formr-partb/123/unsubmit
      DELETE | /api/admin/formr-partb/123
      """)
  void shouldReturnForbiddenWhenNoToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-partb/123
      PUT    | /api/admin/formr-partb/123/unsubmit
      DELETE | /api/admin/formr-partb/123
      """)
  void shouldReturnForbiddenWhenEmptyToken(HttpMethod method, URI uri) throws Exception {
    Jwt token = TestJwtUtil.createToken("{}");
    mockMvc.perform(request(method, uri)
            .with(jwt().jwt(token)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-partb/123
      PUT    | /api/admin/formr-partb/123/unsubmit
      DELETE | /api/admin/formr-partb/123
      """)
  void shouldReturnForbiddenWhenNoGroupsInToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri)
            .with(TestJwtUtil.createAdminToken(List.of(), List.of())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT    | /api/admin/formr-partb/123/unsubmit
      DELETE | /api/admin/formr-partb/123
      """)
  void shouldReturnBadRequestWhenHasInvalidFormId(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of())))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT    | /api/admin/formr-partb/{id}/unsubmit
      DELETE | /api/admin/formr-partb/{id}
      """)
  void shouldReturnForbiddenWhenNoRequiredPermission(HttpMethod method, String uriTemplate)
      throws Exception {
    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-partb/{formId}          | HEE Admin
      GET    | /api/admin/formr-partb/{formId}          | HEE Admin Revalidation
      GET    | /api/admin/formr-partb/{formId}          | HEE Admin Sensitive
      GET    | /api/admin/formr-partb/{formId}          | HEE TIS Admin
      PUT    | /api/admin/formr-partb/{formId}/unsubmit | HEE Admin
      PUT    | /api/admin/formr-partb/{formId}/unsubmit | HEE Admin Revalidation
      PUT    | /api/admin/formr-partb/{formId}/unsubmit | HEE Admin Sensitive
      PUT    | /api/admin/formr-partb/{formId}/unsubmit | HEE TIS Admin
      DELETE | /api/admin/formr-partb/{formId}          | TSS Support Admin
      """)
  void shouldReturnNotFoundWhenHasRequiredPermissionAndFormMissing(HttpMethod method,
      String uriTemplate, String role) throws Exception {
    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of(role))))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT    | /api/admin/formr-partb/{formId}/unsubmit | HEE Admin
      PUT    | /api/admin/formr-partb/{formId}/unsubmit | HEE Admin Revalidation
      PUT    | /api/admin/formr-partb/{formId}/unsubmit | HEE Admin Sensitive
      PUT    | /api/admin/formr-partb/{formId}/unsubmit | HEE TIS Admin
      DELETE | /api/admin/formr-partb/{formId}          | TSS Support Admin
      """)
  void shouldReturnOkWhenHasRequiredPermissionAndFormFound(HttpMethod method, String uriTemplate,
      String role) throws Exception {
    FormRPartB form = new FormRPartB();
    form.setId(FORM_ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setSubmissionDate(LocalDateTime.now());
    template.insert(form);

    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(any());

    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of(role))))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-partb/{formId}          | HEE Admin
      GET    | /api/admin/formr-partb/{formId}          | HEE Admin Revalidation
      GET    | /api/admin/formr-partb/{formId}          | HEE Admin Sensitive
      GET    | /api/admin/formr-partb/{formId}          | HEE TIS Admin
      """)
  void shouldReturnOkWhenFormFound(HttpMethod method, String uriTemplate,
      String role) throws Exception {
    FormRPartB form = new FormRPartB();
    form.setId(FORM_ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setSubmissionDate(LocalDateTime.now());
    template.insert(form);

    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of(role))))
        .andExpect(status().isOk());
  }

  @ParameterizedTest(name = "Should not return {0} forms in list")
  @EnumSource(value = LifecycleState.class, names = {"DRAFT", "DELETED"})
  void shouldNotReturnDraftOrDeletedFormsInList(LifecycleState excludedState) throws Exception {

    FormRPartB submittedForm = new FormRPartB();
    submittedForm.setId(UUID.randomUUID());
    submittedForm.setTraineeTisId(TRAINEE_ID);
    submittedForm.setLifecycleState(SUBMITTED);
    submittedForm.setSubmissionDate(LocalDateTime.now());
    template.insert(submittedForm);

    FormRPartB excludedForm = new FormRPartB();
    excludedForm.setId(UUID.randomUUID());
    excludedForm.setTraineeTisId(TRAINEE_ID);
    excludedForm.setLifecycleState(excludedState);
    excludedForm.setSubmissionDate(excludedState == DELETED
        ? LocalDateTime.now().minusDays(5)
        : null);
    template.insert(excludedForm);

    mockMvc.perform(get("/api/admin/formr-partb")
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("HEE Admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(submittedForm.getId().toString()))
        .andExpect(jsonPath("$[0].lifecycleState").value("SUBMITTED"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED"})
  void shouldReturnFormsWithLifecycleStateInList(LifecycleState includedState) throws Exception {
    FormRPartB includedForm = new FormRPartB();
    includedForm.setId(UUID.randomUUID());
    includedForm.setTraineeTisId(TRAINEE_ID);
    includedForm.setLifecycleState(includedState);
    includedForm.setSubmissionDate(LocalDateTime.now());
    template.insert(includedForm);

    FormRPartB draftForm = new FormRPartB();
    draftForm.setId(UUID.randomUUID());
    draftForm.setTraineeTisId(TRAINEE_ID);
    draftForm.setLifecycleState(DRAFT);
    template.insert(draftForm);

    FormRPartB deletedForm = new FormRPartB();
    deletedForm.setId(UUID.randomUUID());
    deletedForm.setTraineeTisId(TRAINEE_ID);
    deletedForm.setLifecycleState(DELETED);
    deletedForm.setSubmissionDate(LocalDateTime.now().minusDays(2));
    template.insert(deletedForm);

    mockMvc.perform(get("/api/admin/formr-partb")
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("HEE Admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(includedForm.getId().toString()))
        .andExpect(jsonPath("$[0].lifecycleState").value(includedState.toString()));
  }

  @Test
  void shouldReturnFormsListWithAllDtoDetails() throws Exception {
    FormRPartB submittedForm = new FormRPartB();
    submittedForm.setId(UUID.randomUUID());
    submittedForm.setTraineeTisId(TRAINEE_ID);
    submittedForm.setLifecycleState(SUBMITTED);
    LocalDateTime submissionDate = LocalDateTime.now();
    submittedForm.setSubmissionDate(submissionDate);
    submittedForm.setIsArcp(true);
    submittedForm.setProgrammeSpecialty("Cardiology");
    UUID programmeMembershipId = UUID.randomUUID();
    submittedForm.setProgrammeMembershipId(programmeMembershipId);
    template.insert(submittedForm);

    String response = mockMvc.perform(get("/api/admin/formr-partb")
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("HEE Admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(submittedForm.getId().toString()))
        .andExpect(jsonPath("$[0].lifecycleState").value("SUBMITTED"))
        .andExpect(jsonPath("$[0].traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$[0].isArcp").value("true"))
        .andExpect(jsonPath("$[0].programmeName").value("Cardiology"))
        .andExpect(jsonPath("$[0].programmeStartDate").doesNotExist())
        .andExpect(jsonPath("$[0].programmeMembershipId")
            .value(programmeMembershipId.toString()))
        .andExpect(jsonPath("$[0].formType").value("formr-partb"))
        .andReturn().getResponse().getContentAsString();

    // Parse the returned submissionDate and compare within 1 second
    JsonNode root = new ObjectMapper().readTree(response);
    String returnedDateStr = root.get(0).get("submissionDate").asText();
    LocalDateTime returnedDate = LocalDateTime.parse(returnedDateStr);
    long secondsDiff = abs(Duration.between(submissionDate, returnedDate).getSeconds());
    assertTrue(secondsDiff <= 1,
        "submissionDate should be within 1 second. " +
            "Expected: " + submissionDate + ", Actual: " + returnedDate);
  }
}
