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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartARepositoryImpl;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class AdminFormRPartAResourceIntegrationTest {

  private static final UUID FORM_ID = UUID.randomUUID();
  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String FORENAME = "John";
  private static final String SURNAME = "Doe";

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
  private S3FormRPartARepositoryImpl s3FormRPartARepository;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), FormRPartA.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT | /api/admin/formr-parta/123/unsubmit
      DELETE | /api/admin/formr-parta/123
      """)
  void shouldReturnForbiddenWhenNoToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT | /api/admin/formr-parta/123/unsubmit
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
      PUT | /api/admin/formr-parta/123/unsubmit
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
      PUT | /api/admin/formr-parta/123/unsubmit
      DELETE | /api/admin/formr-parta/123
      """)
  void shouldReturnBadRequestWhenHasInvalidFormId(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of())))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT | /api/admin/formr-parta/{id}/unsubmit
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
      PUT | /api/admin/formr-parta/{id}/unsubmit
      DELETE | /api/admin/formr-parta/{id}
      """)
  void shouldReturnNotFoundWhenHasRequiredPermissionAndFormMissing(HttpMethod method,
      String uriTemplate)
      throws Exception {
    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("TSS Support Admin"))))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT | /api/admin/formr-parta/{formId}/unsubmit
      DELETE | /api/admin/formr-parta/{formId}
      """)
  void shouldReturnOkWhenHasRequiredPermissionAndFormFound(HttpMethod method, String uriTemplate)
      throws Exception {
    FormRPartA form = new FormRPartA();
    form.setId(FORM_ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setSubmissionDate(LocalDateTime.now());
    template.insert(form);

    when(s3FormRPartARepository.save(org.mockito.ArgumentMatchers.any(FormRPartA.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("TSS Support Admin"))))
        .andExpect(status().isOk());
  }

  @Test
  void shouldGetFormRPartAById() throws Exception {
    FormRPartA form = new FormRPartA();
    form.setTraineeTisId(TRAINEE_ID);
    form.setForename(FORENAME);
    form.setSurname(SURNAME);
    form.setLifecycleState(LifecycleState.DRAFT);
    FormRPartA savedForm = template.save(form);

    when(s3FormRPartARepository.findByIdAndTraineeTisId(
        savedForm.getId().toString(), TRAINEE_ID))
        .thenReturn(Optional.empty());

    Jwt token = TestJwtUtil.createTokenForTrainee(TRAINEE_ID);
    mockMvc.perform(get("/api/formr-parta/" + savedForm.getId())
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
  void shouldGetAllFormRPartAsForTraineeAsAdmin() throws Exception {
    FormRPartA form1 = new FormRPartA();
    form1.setTraineeTisId(TRAINEE_ID);
    form1.setForename(FORENAME);
    form1.setSurname(SURNAME);
    form1.setLifecycleState(LifecycleState.SUBMITTED);
    form1.setSubmissionDate(LocalDateTime.now());
    template.save(form1);

    FormRPartA form2 = new FormRPartA();
    form2.setTraineeTisId(TRAINEE_ID);
    form2.setForename("Jane");
    form2.setSurname("Smith");
    form2.setLifecycleState(LifecycleState.SUBMITTED);
    form2.setSubmissionDate(LocalDateTime.now());
    template.save(form2);

    mockMvc.perform(get("/api/admin/formr-parta")
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("TSS Support Admin"))))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void shouldGetEmptyListWhenNoFormsExistForTraineeAsAdmin() throws Exception {
    mockMvc.perform(get("/api/admin/formr-parta")
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("TSS Support Admin"))))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void shouldGetFormRPartAByIdAsAdmin() throws Exception {
    FormRPartA form = new FormRPartA();
    form.setTraineeTisId(TRAINEE_ID);
    form.setForename(FORENAME);
    form.setSurname(SURNAME);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setSubmissionDate(LocalDateTime.now());
    FormRPartA savedForm = template.save(form);

    when(s3FormRPartARepository.findByIdAndTraineeTisId(
        savedForm.getId().toString(), TRAINEE_ID))
        .thenReturn(Optional.empty());

    mockMvc.perform(get("/api/admin/formr-parta/" + savedForm.getId())
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("TSS Support Admin"))))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(savedForm.getId().toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.forename").value(FORENAME))
        .andExpect(jsonPath("$.surname").value(SURNAME))
        .andExpect(jsonPath("$.lifecycleState").value("SUBMITTED"));
  }

  @Test
  void shouldReturnNotFoundWhenGettingNonExistentFormByIdAsAdmin() throws Exception {
    UUID nonExistentId = UUID.randomUUID();

    when(s3FormRPartARepository.findByIdAndTraineeTisId(
        nonExistentId.toString(), TRAINEE_ID))
        .thenReturn(Optional.empty());

    mockMvc.perform(get("/api/admin/formr-parta/" + nonExistentId)
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("TSS Support Admin"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @Test
  void shouldGetFormRPartAByIdFromS3WhenNotInDatabaseAsAdmin() throws Exception {
    UUID formId = UUID.randomUUID();

    FormRPartA s3Form = new FormRPartA();
    s3Form.setId(formId);
    s3Form.setTraineeTisId(TRAINEE_ID);
    s3Form.setForename(FORENAME);
    s3Form.setSurname(SURNAME);
    s3Form.setLifecycleState(LifecycleState.SUBMITTED);
    s3Form.setLastModifiedDate(LocalDateTime.now());

    when(s3FormRPartARepository.findByIdAndTraineeTisId(
        formId.toString(), TRAINEE_ID))
        .thenReturn(Optional.of(s3Form));

    mockMvc.perform(get("/api/admin/formr-parta/" + formId)
            .param("traineeId", TRAINEE_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of("TSS Support Admin"))))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(formId.toString()))
        .andExpect(jsonPath("$.traineeTisId").value(TRAINEE_ID))
        .andExpect(jsonPath("$.forename").value(FORENAME))
        .andExpect(jsonPath("$.surname").value(SURNAME))
        .andExpect(jsonPath("$.lifecycleState").value("SUBMITTED"));
  }
}
