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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
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

  @MockBean
  private SnsTemplate snsTemplate;

  @MockBean
  private S3Client s3Client;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), FormRPartB.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
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

    mockMvc.perform(request(method, uriTemplate, FORM_ID)
            .with(TestJwtUtil.createAdminToken(List.of(DBC_1), List.of(role))))
        .andExpect(status().isOk());
  }
}
