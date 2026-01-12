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

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
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
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class AdminFormRPartAResourceIntegrationTest {

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

  @MockBean
  private SnsTemplate snsTemplate;

  @MockBean
  private S3Client s3Client;

  @MockBean
  SnsClient snsClient;

  @BeforeEach
  void setUp() {
    snsClient = mock(SnsClient.class);
  }

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), FormRPartA.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-parta/123
      PUT    | /api/admin/formr-parta/123/unsubmit
      DELETE | /api/admin/formr-parta/123
      """)
  void shouldReturnForbiddenWhenNoToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-parta/123
      PUT    | /api/admin/formr-parta/123/unsubmit
      DELETE | /api/admin/formr-parta/123
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
      GET    | /api/admin/formr-parta/123
      PUT    | /api/admin/formr-parta/123/unsubmit
      DELETE | /api/admin/formr-parta/123
      """)
  void shouldReturnForbiddenWhenNoGroupsInToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri)
            .with(TestJwtUtil.createAdminToken(List.of(), List.of())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT    | /api/admin/formr-parta/123/unsubmit
      DELETE | /api/admin/formr-parta/123
      """)
  void shouldReturnBadRequestWhenHasInvalidFormId(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of())))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT    | /api/admin/formr-parta/{id}/unsubmit
      DELETE | /api/admin/formr-parta/{id}
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
      GET    | /api/admin/formr-parta/{formId}          | HEE Admin
      GET    | /api/admin/formr-parta/{formId}          | HEE Admin Revalidation
      GET    | /api/admin/formr-parta/{formId}          | HEE Admin Sensitive
      GET    | /api/admin/formr-parta/{formId}          | HEE TIS Admin
      PUT    | /api/admin/formr-parta/{formId}/unsubmit | HEE Admin
      PUT    | /api/admin/formr-parta/{formId}/unsubmit | HEE Admin Revalidation
      PUT    | /api/admin/formr-parta/{formId}/unsubmit | HEE Admin Sensitive
      PUT    | /api/admin/formr-parta/{formId}/unsubmit | HEE TIS Admin
      DELETE | /api/admin/formr-parta/{formId}          | TSS Support Admin
      """)
  void shouldReturnNotFoundWhenHasRequiredPermissionAndFormMissing(HttpMethod method,
      String uriTemplate, String role) throws Exception {
    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of(role))))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-parta/{formId}          | HEE Admin
      GET    | /api/admin/formr-parta/{formId}          | HEE Admin Revalidation
      GET    | /api/admin/formr-parta/{formId}          | HEE Admin Sensitive
      GET    | /api/admin/formr-parta/{formId}          | HEE TIS Admin
      """)
  void shouldReturnOkWhenRequiredFormFound(HttpMethod method, String uriTemplate,
      String role) throws Exception {
    FormRPartA form = new FormRPartA();
    form.setId(FORM_ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setSubmissionDate(LocalDateTime.now());
    template.insert(form);

    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of(role))))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT    | /api/admin/formr-parta/{formId}/unsubmit | HEE Admin
      PUT    | /api/admin/formr-parta/{formId}/unsubmit | HEE Admin Revalidation
      PUT    | /api/admin/formr-parta/{formId}/unsubmit | HEE Admin Sensitive
      PUT    | /api/admin/formr-parta/{formId}/unsubmit | HEE TIS Admin
      DELETE | /api/admin/formr-parta/{formId}          | TSS Support Admin
      """)
  void shouldReturnOkWhenHasRequiredPermissionAndFormFound(HttpMethod method, String uriTemplate,
      String role) throws Exception {
    FormRPartA form = new FormRPartA();
    form.setId(FORM_ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setSubmissionDate(LocalDateTime.now());
    template.insert(form);

    Mockito.when(snsClient.publish(ArgumentMatchers.any(PublishRequest.class)))
        .thenReturn(ArgumentMatchers.any());

    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of(role))))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET    | /api/admin/formr-parta/{formId} | HEE Admin
      GET    | /api/admin/formr-parta/{formId} | HEE Admin Revalidation
      GET    | /api/admin/formr-parta/{formId} | HEE Admin Sensitive
      GET    | /api/admin/formr-parta/{formId} | HEE TIS Admin
      """)
  void shouldReturnFormDetailsWhenHasRequiredPermissionAndFormFoundById(HttpMethod method,
      String uriTemplate, String role) throws Exception {
    String programmeMembershipId = UUID.randomUUID().toString();

    FormRPartA form = new FormRPartA();
    form.setId(FORM_ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setProgrammeMembershipId(UUID.fromString(programmeMembershipId));
    form.setSubmissionDate(LocalDateTime.now());
    template.insert(form);

    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of(role))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(FORM_ID.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.programmeMembershipId").value(programmeMembershipId));
  }

  @Test
  void shouldNotReturnDraftFormsInList() throws Exception {
    FormRPartA submittedForm = new FormRPartA();
    submittedForm.setId(UUID.randomUUID());
    submittedForm.setTraineeTisId(TRAINEE_ID);
    submittedForm.setLifecycleState(LifecycleState.SUBMITTED);
    submittedForm.setSubmissionDate(LocalDateTime.now());
    template.insert(submittedForm);

    FormRPartA draftForm = new FormRPartA();
    draftForm.setId(UUID.randomUUID());
    draftForm.setTraineeTisId(TRAINEE_ID);
    draftForm.setLifecycleState(LifecycleState.DRAFT);
    draftForm.setSubmissionDate(null);
    template.insert(draftForm);

    mockMvc.perform(get("/api/admin/formr-parta")
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("HEE Admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(submittedForm.getId().toString()))
        .andExpect(jsonPath("$[0].lifecycleState").value("SUBMITTED"));
  }

  @Test
  void shouldNotReturnDeletedFormsInList() throws Exception {
    FormRPartA submittedForm = new FormRPartA();
    submittedForm.setId(UUID.randomUUID());
    submittedForm.setTraineeTisId(TRAINEE_ID);
    submittedForm.setLifecycleState(LifecycleState.SUBMITTED);
    submittedForm.setSubmissionDate(LocalDateTime.now());
    template.insert(submittedForm);

    FormRPartA deletedForm = new FormRPartA();
    deletedForm.setId(UUID.randomUUID());
    deletedForm.setTraineeTisId(TRAINEE_ID);
    deletedForm.setLifecycleState(LifecycleState.DELETED);
    deletedForm.setSubmissionDate(LocalDateTime.now().minusDays(5));
    template.insert(deletedForm);

    mockMvc.perform(get("/api/admin/formr-parta")
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("HEE Admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(submittedForm.getId().toString()))
        .andExpect(jsonPath("$[0].lifecycleState").value("SUBMITTED"));
  }

  @Test
  void shouldOnlyReturnSubmittedAndUnsubmittedFormsInList() throws Exception {
    FormRPartA submittedForm = new FormRPartA();
    submittedForm.setId(UUID.randomUUID());
    submittedForm.setTraineeTisId(TRAINEE_ID);
    submittedForm.setLifecycleState(LifecycleState.SUBMITTED);
    submittedForm.setSubmissionDate(LocalDateTime.now());
    template.insert(submittedForm);

    FormRPartA unsubmittedForm = new FormRPartA();
    unsubmittedForm.setId(UUID.randomUUID());
    unsubmittedForm.setTraineeTisId(TRAINEE_ID);
    unsubmittedForm.setLifecycleState(LifecycleState.UNSUBMITTED);
    unsubmittedForm.setSubmissionDate(LocalDateTime.now().minusDays(1));
    template.insert(unsubmittedForm);

    FormRPartA draftForm = new FormRPartA();
    draftForm.setId(UUID.randomUUID());
    draftForm.setTraineeTisId(TRAINEE_ID);
    draftForm.setLifecycleState(LifecycleState.DRAFT);
    template.insert(draftForm);

    FormRPartA deletedForm = new FormRPartA();
    deletedForm.setId(UUID.randomUUID());
    deletedForm.setTraineeTisId(TRAINEE_ID);
    deletedForm.setLifecycleState(LifecycleState.DELETED);
    deletedForm.setSubmissionDate(LocalDateTime.now().minusDays(2));
    template.insert(deletedForm);

    mockMvc.perform(get("/api/admin/formr-parta")
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("HEE Admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.lifecycleState == 'DRAFT')]").doesNotExist())
        .andExpect(jsonPath("$[?(@.lifecycleState == 'DELETED')]").doesNotExist());
  }
}