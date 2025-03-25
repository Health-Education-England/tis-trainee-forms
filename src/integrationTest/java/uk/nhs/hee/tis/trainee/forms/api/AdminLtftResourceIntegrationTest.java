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
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PDF;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Declarations;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Discussions;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.PersonalDetails;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.ProgrammeMembership;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Reasons;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class AdminLtftResourceIntegrationTest {

  private static final String DBC_1 = "1-abc123";
  private static final String DBC_2 = "1-yxz789";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MongoTemplate template;

  @Value("${application.timezone}")
  private ZoneId timezone;

  @MockBean
  SnsTemplate snsTemplate;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), LtftForm.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/admin/ltft
      GET | /api/admin/ltft/123
      PUT | /api/admin/ltft/123/approve
      PUT | /api/admin/ltft/123/unsubmit
      GET | /api/admin/ltft/count
      """)
  void shouldReturnForbiddenWhenNoToken(HttpMethod method, URI uri) throws Exception {
    mockMvc.perform(request(method, uri))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/admin/ltft
      GET | /api/admin/ltft/123
      PUT | /api/admin/ltft/123/approve
      PUT | /api/admin/ltft/123/unsubmit
      GET | /api/admin/ltft/count
      """)
  void shouldReturnForbiddenWhenEmptyToken(HttpMethod method, URI uri) throws Exception {
    String token = TestJwtUtil.generateToken("{}");
    mockMvc.perform(request(method, uri)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/admin/ltft
      GET | /api/admin/ltft/123
      PUT | /api/admin/ltft/123/approve
      PUT | /api/admin/ltft/123/unsubmit
      GET | /api/admin/ltft/count
      """)
  void shouldReturnForbiddenWhenNoGroupsInToken(HttpMethod method, URI uri) throws Exception {
    String token = TestJwtUtil.generateAdminTokenForGroups(List.of());
    mockMvc.perform(request(method, uri)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$").doesNotExist());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/admin/ltft/123
      PUT | /api/admin/ltft/123/approve
      PUT | /api/admin/ltft/123/unsubmit
      """)
  void shouldReturnBadRequestWhenInvalidFormId(HttpMethod method, URI uri) throws Exception {
    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(request(method, uri)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      PUT | /api/admin/ltft/{id}/unsubmit
      """)
  void shouldReturnBadRequestWhenRequiredReasonMissing(HttpMethod method, String uriTemplate)
      throws Exception {
    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(request(method, uriTemplate, UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/admin/ltft/{id}
      PUT | /api/admin/ltft/{id}/approve
      PUT | /api/admin/ltft/{id}/unsubmit
      """)
  void shouldReturnNotFoundWhenFormIdNotFound(HttpMethod method, String uriTemplate)
      throws Exception {
    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(request(method, uriTemplate, UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(APPLICATION_JSON)
            .content("{}")) // required by some endpoints
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/admin/ltft/{id}
      PUT | /api/admin/ltft/{id}/approve
      PUT | /api/admin/ltft/{id}/unsubmit
      """)
  void shouldReturnNotFoundWhenLtftDoesNotMatchDbc(HttpMethod method, String uriTemplate)
      throws Exception {
    LtftForm form = createLtftForm(SUBMITTED, DBC_2, null);
    form = template.save(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(request(method, uriTemplate, form.getId())
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(APPLICATION_JSON)
            .content("{}")) // required by some endpoints
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldCountZeroWhenNoLtfts(String statusFilter) throws Exception {
    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string("0"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldCountZeroWhenNoLtftsWithMatchingDbc(String statusFilter) throws Exception {
    template.insert(createLtftForm(SUBMITTED, DBC_2, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string("0"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldCountZeroWhenLtftWithMatchingDbcIsDraft(String statusFilter) throws Exception {
    template.insert(createLtftForm(DRAFT, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string("0"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldOnlyCountLtftsWithMatchingDbcWhenNoStatusFilter(String statusFilter) throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1, null));
    template.insert(createLtftForm(SUBMITTED, DBC_2, null));

    // Total number of states, plus an additional SUBMITTED, minus DRAFT.
    int expectedCount = LifecycleState.values().length;

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(expectedCount)));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = "DRAFT")
  void shouldOnlyCountLtftsWithMatchingDbcWhenHasStatusFilter(LifecycleState status)
      throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(status, DBC_2, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", status.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(1)));
  }

  @Test
  void shouldCountMatchingLtftsWhenMultipleDbcs() throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_2, null));

    // Total number of states, plus an additional SUBMITTED, minus DRAFT.
    int expectedCount = LifecycleState.values().length;

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1, DBC_2));
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(expectedCount)));
  }

  @Test
  void shouldCountMatchingLtftsWhenMultipleStatusFilters() throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    String statusFilter = "%s,%s".formatted(SUBMITTED, LifecycleState.UNSUBMITTED);
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(3)));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldReturnNoSummariesWhenNoLtfts(String statusFilter) throws Exception {
    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(0)))
        .andExpect(jsonPath("$.page.totalPages", is(0)));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldReturnNoSummariesWhenNoLtftsWithMatchingDbc(String statusFilter) throws Exception {
    template.insert(createLtftForm(SUBMITTED, DBC_2, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(0)))
        .andExpect(jsonPath("$.page.totalPages", is(0)));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldReturnNoSummariesWhenLtftWithMatchingDbcIsDraft(String statusFilter) throws Exception {
    template.insert(createLtftForm(DRAFT, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(0)))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(0)))
        .andExpect(jsonPath("$.page.totalPages", is(0)));
  }

  @Test
  void shouldReturnSummaryContentWhenLtftWithMatchingDbc() throws Exception {
    LocalDate startDate = LocalDate.now().plusWeeks(20);

    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder()
            .forenames("Anthony").surname("Gilliam").gmcNumber("1234567").gdcNumber("D123456")
            .build())
        .programmeMembership(ProgrammeMembership.builder()
            .name("General Practice").designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder()
            .startDate(startDate)
            .build())
        .reasons(Reasons.builder()
            .selected(List.of("Other", "Caring responsibilities"))
            .build())
        .discussions(Discussions.builder()
            .tpdEmail("tpd@example.com")
            .build())
        .assignedAdmin(Person.builder()
            .name("Ad Min").email("ad.min@example.com").role("ADMIN")
            .build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));
    LocalDate latestSubmittedDate = LocalDate.ofInstant(latestSubmitted, timezone);

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .submitted(latestSubmitted)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id", is(form.getId().toString())))
        .andExpect(jsonPath("$.content[0].personalDetails.id", is("47165")))
        .andExpect(jsonPath("$.content[0].personalDetails.forenames", is("Anthony")))
        .andExpect(jsonPath("$.content[0].personalDetails.surname", is("Gilliam")))
        .andExpect(jsonPath("$.content[0].personalDetails.gmcNumber", is("1234567")))
        .andExpect(jsonPath("$.content[0].personalDetails.gdcNumber", is("D123456")))
        .andExpect(jsonPath("$.content[0].programmeName", is("General Practice")))
        .andExpect(jsonPath("$.content[0].proposedStartDate", is(startDate.toString())))
        .andExpect(jsonPath("$.content[0].submissionDate", is(latestSubmittedDate.toString())))
        .andExpect(jsonPath("$.content[0].reason", is("Caring responsibilities, Other")))
        .andExpect(jsonPath("$.content[0].daysToStart", is(140)))
        .andExpect(jsonPath("$.content[0].shortNotice", is(false)))
        .andExpect(jsonPath("$.content[0].tpd.email", is("tpd@example.com")))
        .andExpect(jsonPath("$.content[0].tpd.emailStatus", is("UNKNOWN")))
        .andExpect(jsonPath("$.content[0].status", is(SUBMITTED.name())))
        .andExpect(jsonPath("$.content[0].assignedAdmin.name", is("Ad Min")))
        .andExpect(jsonPath("$.content[0].assignedAdmin.email", is("ad.min@example.com")))
        .andExpect(jsonPath("$.content[0].assignedAdmin.role").doesNotExist())
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(1)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldOnlyReturnSummariesWithMatchingDbcWhenNoStatusFilter(String statusFilter)
      throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1, null));
    template.insert(createLtftForm(SUBMITTED, DBC_2, null));

    // Total number of states, plus an additional SUBMITTED, minus DRAFT.
    int expectedCount = LifecycleState.values().length;

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(expectedCount)))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(expectedCount)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = "DRAFT")
  void shouldOnlyReturnSummariesWithMatchingDbcWhenHasStatusFilter(LifecycleState status)
      throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(status, DBC_2, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", status.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].status", is(status.toString())))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(1)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @Test
  void shouldReturnMatchingLtftSummariesWhenMultipleDbcs() throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_2, null));

    // Total number of states, plus an additional SUBMITTED, minus DRAFT.
    int expectedCount = LifecycleState.values().length;

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1, DBC_2));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(expectedCount)))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(expectedCount)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @Test
  void shouldReturnMatchingLtftSummariesWhenMultipleStatusFilters() throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    String statusFilter = "%s,%s".formatted(SUBMITTED, LifecycleState.UNSUBMITTED);
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(
            jsonPath("$.content[0].status", oneOf(SUBMITTED.toString(), UNSUBMITTED.toString())))
        .andExpect(
            jsonPath("$.content[1].status", oneOf(SUBMITTED.toString(), UNSUBMITTED.toString())))
        .andExpect(
            jsonPath("$.content[2].status", oneOf(SUBMITTED.toString(), UNSUBMITTED.toString())))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(3)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void shouldPageLtftSummariesWhenTooManyResults(int pageNumber) throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1, null))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    String statusFilter = "%s,%s".formatted(SUBMITTED, LifecycleState.UNSUBMITTED);
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter)
            .param("size", "1")
            .param("page", String.valueOf(pageNumber)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(1)))
        .andExpect(jsonPath("$.page.number", is(pageNumber)))
        .andExpect(jsonPath("$.page.totalElements", is(3)))
        .andExpect(jsonPath("$.page.totalPages", is(3)));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldSortLtftSummariesByStartDateWhenNoSortProvided(String sort) throws Exception {
    LtftForm form1 = new LtftForm();
    form1.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(SUBMITTED)
            .build())
        .submitted(Instant.now())
        .build());
    form1.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now()).build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .build());
    form1 = template.insert(form1);

    LtftForm form2 = new LtftForm();
    form2.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(SUBMITTED)
            .build())
        .submitted(Instant.now())
        .build());
    form2.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now().minusYears(1)).build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .build());
    form2 = template.insert(form2);

    LtftForm form3 = new LtftForm();
    form3.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(SUBMITTED)
            .build())
        .submitted(Instant.now())
        .build());
    form3.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .startDate(LocalDate.now().plusYears(1)).build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .build());
    form3 = template.insert(form3);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("sort", sort))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].id", is(form2.getId().toString())))
        .andExpect(jsonPath("$.content[1].id", is(form1.getId().toString())))
        .andExpect(jsonPath("$.content[2].id", is(form3.getId().toString())))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(3)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @Test
  void shouldSortLtftSummariesByProvidedSortWhenSortProvided() throws Exception {
    LtftForm form1 = createLtftForm(SUBMITTED, DBC_1, LocalDate.now().plusYears(1));
    template.insert(form1);

    LtftForm form2 = createLtftForm(SUBMITTED, DBC_1, LocalDate.now().minusYears(1));
    template.insert(form2);

    LtftForm form3 = createLtftForm(SUBMITTED, DBC_1, LocalDate.now());
    template.insert(form3);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("sort", "proposedStartDate"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].id", is(form2.getId().toString())))
        .andExpect(jsonPath("$.content[1].id", is(form3.getId().toString())))
        .andExpect(jsonPath("$.content[2].id", is(form1.getId().toString())))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(3)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @Test
  void shouldReturnNotFoundGettingDetailJsonWhenLtftWithMatchingDbcIsDraft() throws Exception {
    LtftForm form = createLtftForm(DRAFT, DBC_1, null);
    form = template.save(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldGetDetailJsonWhenLtftWithMatchingDbcIsNotDraft() throws Exception {
    LocalDate startDate = LocalDate.now().plusWeeks(20);

    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder()
            .forenames("Anthony").surname("Gilliam").gmcNumber("1234567").gdcNumber("D123456")
            .build())
        .programmeMembership(ProgrammeMembership.builder()
            .name("General Practice").designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder()
            .startDate(startDate)
            .build())
        .reasons(Reasons.builder()
            .selected(List.of("Other", "Caring responsibilities"))
            .build())
        .discussions(Discussions.builder()
            .tpdEmail("tpd@example.com")
            .build())
        .assignedAdmin(Person.builder()
            .name("Ad Min").email("ad.min@example.com").role("ADMIN")
            .build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.id", is(form.getId().toString())))
        .andExpect(jsonPath("$.traineeTisId", is("47165")))
        .andExpect(jsonPath("$.personalDetails.id", is("47165")))
        .andExpect(jsonPath("$.personalDetails.forenames", is("Anthony")))
        .andExpect(jsonPath("$.personalDetails.surname", is("Gilliam")))
        .andExpect(jsonPath("$.personalDetails.gmcNumber", is("1234567")))
        .andExpect(jsonPath("$.personalDetails.gdcNumber", is("D123456")))
        .andExpect(jsonPath("$.programmeMembership.name", is("General Practice")))
        .andExpect(jsonPath("$.change.startDate", is(startDate.toString())))
        .andExpect(jsonPath("$.reasons.selected", hasSize(2)))
        .andExpect(jsonPath("$.reasons.selected", hasItems("Caring responsibilities", "Other")))
        .andExpect(jsonPath("$.discussions.tpdEmail", is("tpd@example.com")))
        .andExpect(jsonPath("$.status.current.state", is(SUBMITTED.name())))
        .andExpect(jsonPath("$.assignedAdmin.name", is("Ad Min")))
        .andExpect(jsonPath("$.assignedAdmin.email", is("ad.min@example.com")))
        .andExpect(jsonPath("$.assignedAdmin.role", is("ADMIN")));
  }

  @Test
  void shouldReturnNotFoundGettingDetailPdfWhenLtftWithMatchingDbcIsDraft() throws Exception {
    LtftForm form = createLtftForm(DRAFT, DBC_1, null);
    form = template.save(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldGetDetailPdfFormDetailsWhenLtftWithMatchingDbcIsNotDraft() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");
    form.setFormRef("ltft_47165_001");

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder().build())
        .reasons(Reasons.builder().build())
        .declarations(Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .assignedAdmin(Person.builder().build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    MvcResult result = mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = PDDocument.load(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected header.", pdfText,
        startsWith("LTFT Application Detail" + System.lineSeparator()));
    assertThat("Unexpected form ref.", pdfText,
        containsString("Ref: ltft_47165_001" + System.lineSeparator()));
    assertThat("Unexpected status.", pdfText,
        containsString("Status: SUBMITTED" + System.lineSeparator()));

    DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm (z)");
    String createdString = ZonedDateTime.ofInstant(form.getCreated(), timezone).format(datePattern);
    assertThat("Unexpected created timestamp.", pdfText,
        containsString("Created: " + createdString + System.lineSeparator()));

    String modifiedString = ZonedDateTime.ofInstant(form.getLastModified(), timezone)
        .format(datePattern);
    assertThat("Unexpected modified timestamp.", pdfText,
        containsString("Modified: " + modifiedString + System.lineSeparator()));
  }

  @Test
  void shouldGetDetailPdfPersonalDetailsWhenLtftWithMatchingDbcIsNotDraft() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder()
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
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder().build())
        .reasons(Reasons.builder().build())
        .declarations(Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .assignedAdmin(Person.builder().build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    MvcResult result = mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = PDDocument.load(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Personal Details" + System.lineSeparator()));
    assertThat("Unexpected title.", pdfText,
        containsString("Title" + System.lineSeparator() + "Dr"));
    assertThat("Unexpected name.", pdfText,
        containsString("Name" + System.lineSeparator() + "Anthony Gilliam"));
    assertThat("Unexpected email.", pdfText,
        containsString("E-Mail" + System.lineSeparator() + "anthony.gilliam@example.com"));
    assertThat("Unexpected telephone.", pdfText,
        containsString("Telephone" + System.lineSeparator() + "07700900000"));
    assertThat("Unexpected mobile.", pdfText,
        containsString("Mobile" + System.lineSeparator() + "07700900001"));
    assertThat("Unexpected GMC.", pdfText,
        containsString("GMC" + System.lineSeparator() + "1234567"));
    assertThat("Unexpected GDC.", pdfText,
        containsString("GDC" + System.lineSeparator() + "D123456"));
    assertThat("Unexpected visa holder.", pdfText,
        containsString("Holds a Skilled Worker visa" + System.lineSeparator() + "true"));
  }

  @Test
  void shouldGetDetailPdfProgrammeDetailsWhenLtftWithMatchingDbcIsNotDraft() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");

    LocalDate startDate = LocalDate.now();
    LocalDate endDate = startDate.plusYears(1);

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .name("General Practice")
            .startDate(startDate)
            .endDate(endDate)
            .wte(0.75)
            .build())
        .change(CctChange.builder().build())
        .reasons(Reasons.builder().build())
        .declarations(Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .assignedAdmin(Person.builder().build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    MvcResult result = mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = PDDocument.load(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Programme Membership" + System.lineSeparator()));
    assertThat("Unexpected name.", pdfText,
        containsString("Programme Name" + System.lineSeparator() + "General Practice"));
    assertThat("Unexpected wte.", pdfText,
        containsString("WTE (Current)" + System.lineSeparator() + "0.75"));

    DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    String startDateString = startDate.format(datePattern);
    assertThat("Unexpected start date.", pdfText,
        containsString("Start Date" + System.lineSeparator() + startDateString));

    String endDateString = endDate.format(datePattern);
    assertThat("Unexpected end date.", pdfText,
        containsString("End Date" + System.lineSeparator() + endDateString));
  }

  @Test
  void shouldGetDetailPdfChangeDetailsWhenLtftWithMatchingDbcIsNotDraft() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");

    LocalDate startDate = LocalDate.now();
    LocalDate endDate = startDate.plusYears(1);
    LocalDate cctDate = endDate.plusYears(1);

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder()
            .startDate(startDate)
            .endDate(endDate)
            .wte(0.75)
            .cctDate(cctDate)
            .build())
        .reasons(Reasons.builder().build())
        .declarations(Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .assignedAdmin(Person.builder().build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    MvcResult result = mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = PDDocument.load(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Proposed Changes" + System.lineSeparator()));
    assertThat("Unexpected wte.", pdfText, containsString("WTE" + System.lineSeparator() + "0.75"));

    DateTimeFormatter datePattern = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    String startDateString = startDate.format(datePattern);
    assertThat("Unexpected start date.", pdfText,
        containsString("Start Date" + System.lineSeparator() + startDateString));

    String endDateString = endDate.format(datePattern);
    assertThat("Unexpected end date.", pdfText,
        containsString("End Date" + System.lineSeparator() + endDateString));

    String cctDateString = cctDate.format(datePattern);
    assertThat("Unexpected CCT date.", pdfText,
        containsString("Programme end date" + System.lineSeparator() + cctDateString));
  }

  @Test
  void shouldGetDetailPdfReasonDetailsWhenLtftWithMatchingDbcIsNotDraft() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder().build())
        .reasons(Reasons.builder()
            .selected(List.of("Test1", "Test2", "Other"))
            .otherDetail("other-detail")
            .build())
        .declarations(Declarations.builder().build())
        .discussions(Discussions.builder().build())
        .assignedAdmin(Person.builder().build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    MvcResult result = mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = PDDocument.load(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Reasons" + System.lineSeparator()));
    assertThat("Unexpected selected.", pdfText,
        containsString("Selected" + System.lineSeparator() + "Test1<br>Test2<br>Other"));
    assertThat("Unexpected other reason.", pdfText,
        containsString("Other Reason" + System.lineSeparator() + "other-detail"));
  }

  @Test
  void shouldGetDetailPdfDeclarationDetailsWhenLtftWithMatchingDbcIsNotDraft() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder().build())
        .reasons(Reasons.builder().build())
        .declarations(Declarations.builder()
            .discussedWithTpd(true)
            .informationIsCorrect(true)
            .notGuaranteed(true)
            .build())
        .discussions(Discussions.builder().build())
        .assignedAdmin(Person.builder().build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    MvcResult result = mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = PDDocument.load(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Declarations" + System.lineSeparator()));
    assertThat("Unexpected declaration.", pdfText,
        containsString("Information is correct" + System.lineSeparator() + "true"));
    assertThat("Unexpected declaration.", pdfText,
        containsString("Discussed with TPD" + System.lineSeparator() + "true"));
    assertThat("Unexpected declaration.", pdfText,
        containsString("Not guaranteed" + System.lineSeparator() + "true"));
  }

  @Test
  void shouldGetDetailPdfDiscussionsDetailsWhenLtftWithMatchingDbcIsNotDraft() throws Exception {
    LtftForm form = new LtftForm();
    form.setTraineeTisId("47165");

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder().build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_1)
            .build())
        .change(CctChange.builder().build())
        .reasons(Reasons.builder().build())
        .declarations(Declarations.builder().build())
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
        .assignedAdmin(Person.builder().build())
        .build();
    form.setContent(content);

    Instant latestSubmitted = Instant.now().plus(Duration.ofDays(7));

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo)
        .history(List.of(
            statusInfo,
            StatusInfo.builder().state(SUBMITTED).timestamp(latestSubmitted).build()))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    MvcResult result = mockMvc.perform(get("/api/admin/ltft/{id}", form.getId())
            .header(HttpHeaders.ACCEPT, APPLICATION_PDF)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_PDF))
        .andReturn();

    byte[] response = result.getResponse().getContentAsByteArray();
    PDDocument pdf = PDDocument.load(response);
    PDFTextStripper textStripper = new PDFTextStripper();
    textStripper.setAddMoreFormatting(false);
    String pdfText = textStripper.getText(pdf);

    assertThat("Unexpected section header.", pdfText,
        containsString("Discussions" + System.lineSeparator()));
    assertThat("Unexpected TPD name.", pdfText,
        containsString("TPD name" + System.lineSeparator() + "Tee Pee-Dee"));
    assertThat("Unexpected TPD email.", pdfText,
        containsString("TPD email" + System.lineSeparator() + "tpd@example.com"));
    assertThat("Unexpected other discussions.", pdfText, containsString(
        "Other" + System.lineSeparator() + "Ed Super, ed.super@example.com (Educational Supervisor)"
            + System.lineSeparator() + "Person Two, person.2@example.com (Test Data)"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = "SUBMITTED")
  void shouldNotApproveLtftWhenStateTransitionNotAllowed(LifecycleState currentState)
      throws Exception {
    LtftForm form = template.insert(createLtftForm(currentState, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(put("/api/admin/ltft/{id}/approve", form.getId())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type", is("about:blank")))
        .andExpect(jsonPath("$.title", is("Validation failure")))
        .andExpect(jsonPath("$.status", is(HttpStatus.BAD_REQUEST.value())))
        .andExpect(jsonPath("$.instance", is("/api/admin/ltft/%s/approve".formatted(form.getId()))))
        .andExpect(jsonPath("$.properties.errors").isArray())
        .andExpect(jsonPath("$.properties.errors", hasSize(1)))
        .andExpect(jsonPath("$.properties.errors[0].pointer", is("#/status/current/state")))
        .andExpect(
            jsonPath("$.properties.errors[0].detail", is("can not be transitioned to APPROVED")));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = "SUBMITTED")
  void shouldApproveLtftWhenStateTransitionAllowed(LifecycleState currentState) throws Exception {
    LtftForm form = template.insert(createLtftForm(currentState, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(put("/api/admin/ltft/{id}/approve", form.getId())
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status.current.state", is(APPROVED.toString())))
        .andExpect(jsonPath("$.status.current.modifiedBy.name", is("Ad Min")))
        .andExpect(jsonPath("$.status.current.modifiedBy.email", is("ad.min@example.com")))
        .andExpect(jsonPath("$.status.current.modifiedBy.role", is("ADMIN")))
        .andExpect(jsonPath("$.status.current.revision", is(0)))
        .andExpect(jsonPath("$.status.current.timestamp", notNullValue()))
        .andExpect(jsonPath("$.status.history[0].state", is(currentState.toString())))
        .andExpect(jsonPath("$.status.history[1].state", is(APPROVED.toString())))
        .andExpect(jsonPath("$.status.history[1].detail").doesNotExist())
        .andExpect(jsonPath("$.status.history[1].modifiedBy.name", is("Ad Min")))
        .andExpect(jsonPath("$.status.history[1].modifiedBy.email", is("ad.min@example.com")))
        .andExpect(jsonPath("$.status.history[1].modifiedBy.role", is("ADMIN")))
        .andExpect(jsonPath("$.status.current.revision", is(0)))
        .andExpect(jsonPath("$.status.current.timestamp", notNullValue()));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = "SUBMITTED")
  void shouldNotUnsubmitLtftWhenStateTransitionNotAllowed(LifecycleState currentState)
      throws Exception {
    LtftForm form = template.insert(createLtftForm(currentState, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(put("/api/admin/ltft/{id}/unsubmit", form.getId())
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "reason": "test reason"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type", is("about:blank")))
        .andExpect(jsonPath("$.title", is("Validation failure")))
        .andExpect(jsonPath("$.status", is(HttpStatus.BAD_REQUEST.value())))
        .andExpect(
            jsonPath("$.instance", is("/api/admin/ltft/%s/unsubmit".formatted(form.getId()))))
        .andExpect(jsonPath("$.properties.errors").isArray())
        .andExpect(jsonPath("$.properties.errors", hasSize(1)))
        .andExpect(jsonPath("$.properties.errors[0].pointer", is("#/status/current/state")))
        .andExpect(jsonPath("$.properties.errors[0].detail",
            is("can not be transitioned to UNSUBMITTED")));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = "SUBMITTED")
  void shouldNotUnsubmitLtftWhenStateTransitionAllowedButNoReasonGiven(LifecycleState currentState)
      throws Exception {
    LtftForm form = template.insert(createLtftForm(currentState, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(put("/api/admin/ltft/{id}/unsubmit", form.getId())
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "reason": null
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type", is("about:blank")))
        .andExpect(jsonPath("$.title", is("Validation failure")))
        .andExpect(jsonPath("$.status", is(HttpStatus.BAD_REQUEST.value())))
        .andExpect(
            jsonPath("$.instance", is("/api/admin/ltft/%s/unsubmit".formatted(form.getId()))))
        .andExpect(jsonPath("$.properties.errors").isArray())
        .andExpect(jsonPath("$.properties.errors", hasSize(1)))
        .andExpect(jsonPath("$.properties.errors[0].pointer", is("#/detail/reason")))
        .andExpect(jsonPath("$.properties.errors[0].detail",
            is("must not be null when transitioning to UNSUBMITTED")));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = "SUBMITTED")
  void shouldUnsubmitLtftWhenStateTransitionAllowedAndReasonGiven(LifecycleState currentState)
      throws Exception {
    LtftForm form = template.insert(createLtftForm(currentState, DBC_1, null));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(put("/api/admin/ltft/{id}/unsubmit", form.getId())
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(APPLICATION_JSON)
            .content("""
                {
                  "reason": "test reason",
                  "message": "test message"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status.current.state", is(UNSUBMITTED.toString())))
        .andExpect(jsonPath("$.status.current.modifiedBy.name", is("Ad Min")))
        .andExpect(jsonPath("$.status.current.modifiedBy.email", is("ad.min@example.com")))
        .andExpect(jsonPath("$.status.current.modifiedBy.role", is("ADMIN")))
        .andExpect(jsonPath("$.status.current.revision", is(0)))
        .andExpect(jsonPath("$.status.current.timestamp", notNullValue()))
        .andExpect(jsonPath("$.status.history[0].state", is(currentState.toString())))
        .andExpect(jsonPath("$.status.history[1].state", is(UNSUBMITTED.toString())))
        .andExpect(jsonPath("$.status.history[1].detail.reason", is("test reason")))
        .andExpect(jsonPath("$.status.history[1].detail.message", is("test message")))
        .andExpect(jsonPath("$.status.history[1].modifiedBy.name", is("Ad Min")))
        .andExpect(jsonPath("$.status.history[1].modifiedBy.email", is("ad.min@example.com")))
        .andExpect(jsonPath("$.status.history[1].modifiedBy.role", is("ADMIN")))
        .andExpect(jsonPath("$.status.current.revision", is(0)))
        .andExpect(jsonPath("$.status.current.timestamp", notNullValue()));
  }

  /**
   * Create a form with the given details, other fields will get sensible defaults.
   *
   * @param state The current state of the form.
   * @param dbc   The designated body code to include in the form's programme membership.
   * @return The created form.
   */
  private LtftForm createLtftForm(LifecycleState state, String dbc, LocalDate changeStartDate) {
    LtftForm ltft = new LtftForm();

    LtftContent content = LtftContent.builder()
        .change(CctChange.builder()
            .startDate(changeStartDate)
            .build())
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(dbc)
            .build())
        .build();
    ltft.setContent(content);

    StatusInfo statusInfo = StatusInfo.builder()
        .state(state)
        .timestamp(Instant.now())
        .build();
    ltft.setStatus(Status.builder()
        .current(statusInfo)
        .submitted(state != DRAFT ? Instant.now() : null)
        .history(List.of(statusInfo))
        .build()
    );

    return ltft;
  }
}
