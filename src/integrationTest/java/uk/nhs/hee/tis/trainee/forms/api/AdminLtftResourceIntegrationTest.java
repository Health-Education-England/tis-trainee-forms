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

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
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

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), LtftForm.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      GET | /api/admin/ltft
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
      GET | /api/admin/ltft
      GET | /api/admin/ltft/count
      """)
  void shouldReturnBadRequestWhenInvalidStatusFilter(HttpMethod method, URI uri) throws Exception {
    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(request(method, uri)
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", "INVALID_FILTER"))
        .andExpect(status().isBadRequest());
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
    template.insert(createLtftForm(SUBMITTED, DBC_2));

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
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1));
    template.insert(createLtftForm(SUBMITTED, DBC_2));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(LifecycleState.values().length + 1)));
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldOnlyCountLtftsWithMatchingDbcWhenHasStatusFilter(LifecycleState status)
      throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(status, DBC_2));

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
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_2));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1, DBC_2));
    mockMvc.perform(get("/api/admin/ltft/count")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_PLAIN))
        .andExpect(content().string(String.valueOf(LifecycleState.values().length + 1)));
  }

  @Test
  void shouldCountMatchingLtftsWhenMultipleStatusFilters() throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1));

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
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
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
    template.insert(createLtftForm(SUBMITTED, DBC_2));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
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
            .selected(List.of("Caring responsibilities"))
            .build())
        .discussions(Discussions.builder()
            .tpdEmail("tpd@example.com")
            .build())
        .assignedAdmin(Person.builder()
            .name("Ad Min").email("ad.min@example.com").role("ADMIN")
            .build())
        .build();
    form.setContent(content);

    StatusInfo statusInfo = StatusInfo.builder()
        .state(SUBMITTED).timestamp(Instant.now())
        .build();
    form.setStatus(Status.builder()
        .current(statusInfo).history(List.of(statusInfo))
        .build()
    );

    form = template.insert(form);

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
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
        .andExpect(jsonPath("$.content[0].submissionDate", is(LocalDate.now().toString())))
        .andExpect(jsonPath("$.content[0].reason", is("TODO")))
        .andExpect(jsonPath("$.content[0].daysToStart", is(40)))
        .andExpect(jsonPath("$.content[0].shortNotice", is(false)))
        .andExpect(jsonPath("$.content[0].tpd.email", is("tpd@example.com")))
        .andExpect(jsonPath("$.content[0].tpd.emailStatus", is("TODO")))
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
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1));
    template.insert(createLtftForm(SUBMITTED, DBC_2));

    int expectedCount = LifecycleState.values().length + 1;

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(expectedCount)))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(expectedCount)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldOnlyReturnSummariesWithMatchingDbcWhenHasStatusFilter(LifecycleState status)
      throws Exception {
    List<LtftForm> ltfts = Arrays.stream(LifecycleState.values())
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(status, DBC_2));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", status.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
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
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_2));

    int expectedCount = LifecycleState.values().length + 1;

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1, DBC_2));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
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
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    String statusFilter = "%s,%s".formatted(SUBMITTED, LifecycleState.UNSUBMITTED);
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].status", is(SUBMITTED.toString())))
        .andExpect(jsonPath("$.content[1].status", is(UNSUBMITTED.toString())))
        .andExpect(jsonPath("$.content[2].status", is(SUBMITTED.toString())))
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
        .map(s -> createLtftForm(s, DBC_1))
        .toList();
    template.insertAll(ltfts);

    template.insert(createLtftForm(SUBMITTED, DBC_1));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    String statusFilter = "%s,%s".formatted(SUBMITTED, LifecycleState.UNSUBMITTED);
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("status", statusFilter)
            .param("size", "1")
            .param("page", String.valueOf(pageNumber)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
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
  void shouldSortLtftSummariesByCreatedWhenNoSortProvided(String sort) throws Exception {
    LtftForm form1 = template.insert(createLtftForm(SUBMITTED, DBC_1));
    LtftForm form2 = template.insert(createLtftForm(SUBMITTED, DBC_1));
    LtftForm form3 = template.insert(createLtftForm(SUBMITTED, DBC_1));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("sort", sort))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].id", is(form1.getId().toString())))
        .andExpect(jsonPath("$.content[1].id", is(form2.getId().toString())))
        .andExpect(jsonPath("$.content[2].id", is(form3.getId().toString())))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(3)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  @Test
  void shouldSortLtftSummariesByProvidedSortWhenSortProvided() throws Exception {
    LtftForm form1 = template.insert(createLtftForm(UNSUBMITTED, DBC_1));
    LtftForm form2 = template.insert(createLtftForm(SUBMITTED, DBC_1));
    LtftForm form3 = template.insert(createLtftForm(DRAFT, DBC_1));

    String token = TestJwtUtil.generateAdminTokenForGroups(List.of(DBC_1));
    mockMvc.perform(get("/api/admin/ltft")
            .header(HttpHeaders.AUTHORIZATION, token)
            .param("sort", "status.current.state"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content", hasSize(3)))
        .andExpect(jsonPath("$.content[0].id", is(form3.getId().toString())))
        .andExpect(jsonPath("$.content[1].id", is(form2.getId().toString())))
        .andExpect(jsonPath("$.content[2].id", is(form1.getId().toString())))
        .andExpect(jsonPath("$.page", aMapWithSize(4)))
        .andExpect(jsonPath("$.page.size", is(2000)))
        .andExpect(jsonPath("$.page.number", is(0)))
        .andExpect(jsonPath("$.page.totalElements", is(3)))
        .andExpect(jsonPath("$.page.totalPages", is(1)));
  }

  /**
   * Create a form with the given details, other fields will get sensible defaults.
   *
   * @param state The current state of the form.
   * @param dbc   The designated body code to include in the form's programme membership.
   * @return The created form.
   */
  private LtftForm createLtftForm(LifecycleState state, String dbc) {
    LtftForm ltft = new LtftForm();

    LtftContent content = LtftContent.builder()
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
        .history(List.of(statusInfo))
        .build()
    );

    return ltft;
  }
}
