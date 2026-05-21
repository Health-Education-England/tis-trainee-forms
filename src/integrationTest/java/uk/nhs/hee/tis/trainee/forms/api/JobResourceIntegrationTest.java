/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartARepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
class JobResourceIntegrationTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();

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
  private SnsClient snsClient;

  @MockitoBean
  private S3FormRPartARepositoryImpl s3FormRPartARepository;

  @MockitoBean
  private PdfService pdfService;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), FormRPartA.class);
    template.findAllAndRemove(new Query(), FormRPartB.class);
    template.findAllAndRemove(new Query(), LtftForm.class);
  }

  // Form-R Part A refresh

  @Test
  void shouldReturnZeroWhenNoFormRPartAsExistForRefresh() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/job/formr-parta/publish-refresh"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("0"));
    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldPublishAllEligibleFormRPartAsWhenNoSinceDateProvided() throws Exception {
    insertFormRPartA(UUID.randomUUID(), SUBMITTED, LocalDateTime.now().minusDays(10));
    insertFormRPartA(UUID.randomUUID(), UNSUBMITTED, LocalDateTime.now().minusDays(5));
    insertFormRPartA(UUID.randomUUID(), DELETED, LocalDateTime.now().minusDays(3));
    // DRAFT should not be published
    insertFormRPartA(UUID.randomUUID(), DRAFT, LocalDateTime.now());

    MvcResult result = mockMvc.perform(post("/api/job/formr-parta/publish-refresh"))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("3"));
    verify(snsClient, times(3)).publish(any(PublishRequest.class));
  }

  @Test
  void shouldPublishOnlyFormRPartAsModifiedOnOrAfterSinceDate() throws Exception {
    LocalDate since = LocalDate.now().minusDays(7);

    // Modified after cutoff — should be published
    insertFormRPartA(UUID.randomUUID(), SUBMITTED, LocalDateTime.now().minusDays(3));
    // Modified on the cutoff boundary — should be published
    insertFormRPartA(UUID.randomUUID(), UNSUBMITTED, since.atStartOfDay());
    // Modified before cutoff — should NOT be published
    insertFormRPartA(UUID.randomUUID(), SUBMITTED, LocalDateTime.now().minusDays(14));
    // DRAFT should never be published regardless of date
    insertFormRPartA(UUID.randomUUID(), DRAFT, LocalDateTime.now());

    MvcResult result = mockMvc.perform(post("/api/job/formr-parta/publish-refresh")
            .param("since", since.toString()))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("2"));
    verify(snsClient, times(2)).publish(any(PublishRequest.class));
  }

  @Test
  void shouldPublishFormRPartAAndIncludeFormTypeMessageAttribute() throws Exception {
    insertFormRPartA(UUID.randomUUID(), SUBMITTED, LocalDateTime.now());

    mockMvc.perform(post("/api/job/formr-parta/publish-refresh"))
        .andExpect(status().isOk());

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());

    PublishRequest request = captor.getValue();
    assertThat("Unexpected formType message attribute.",
        request.messageAttributes().get("formType").stringValue(), is("formr-a"));
  }

  // Form-R Part B refresh

  @Test
  void shouldReturnZeroWhenNoFormRPartBsExistForRefresh() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/job/formr-partb/publish-refresh"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("0"));
    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldPublishAllEligibleFormRPartBsWhenNoSinceDateProvided() throws Exception {
    insertFormRPartB(UUID.randomUUID(), SUBMITTED, LocalDateTime.now().minusDays(10));
    insertFormRPartB(UUID.randomUUID(), UNSUBMITTED, LocalDateTime.now().minusDays(5));
    insertFormRPartB(UUID.randomUUID(), DELETED, LocalDateTime.now().minusDays(3));
    // DRAFT should not be published
    insertFormRPartB(UUID.randomUUID(), DRAFT, LocalDateTime.now());

    MvcResult result = mockMvc.perform(post("/api/job/formr-partb/publish-refresh"))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("3"));
    verify(snsClient, times(3)).publish(any(PublishRequest.class));
  }

  @Test
  void shouldPublishOnlyFormRPartBsModifiedOnOrAfterSinceDate() throws Exception {
    LocalDate since = LocalDate.now().minusDays(7);

    // Modified after cutoff — should be published
    insertFormRPartB(UUID.randomUUID(), SUBMITTED, LocalDateTime.now().minusDays(3));
    // Modified on the cutoff boundary — should be published
    insertFormRPartB(UUID.randomUUID(), UNSUBMITTED, since.atStartOfDay());
    // Modified before cutoff — should NOT be published
    insertFormRPartB(UUID.randomUUID(), SUBMITTED, LocalDateTime.now().minusDays(14));
    // DRAFT should never be published regardless of date
    insertFormRPartB(UUID.randomUUID(), DRAFT, LocalDateTime.now());

    MvcResult result = mockMvc.perform(post("/api/job/formr-partb/publish-refresh")
            .param("since", since.toString()))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("2"));
    verify(snsClient, times(2)).publish(any(PublishRequest.class));
  }

  @Test
  void shouldPublishFormRPartBAndIncludeFormTypeMessageAttribute() throws Exception {
    insertFormRPartB(UUID.randomUUID(), SUBMITTED, LocalDateTime.now());

    mockMvc.perform(post("/api/job/formr-partb/publish-refresh"))
        .andExpect(status().isOk());

    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());

    PublishRequest request = captor.getValue();
    assertThat("Unexpected formType message attribute.",
        request.messageAttributes().get("formType").stringValue(), is("formr-b"));
  }

  // LTFT refresh

  @Test
  void shouldReturnZeroWhenNoLtftsExistForRefresh() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/job/ltft/publish-refresh"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("0"));
    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldPublishAllEligibleLtftsWhenNoSinceDateProvided() throws Exception {
    insertLtftForm(UUID.randomUUID(), SUBMITTED, Instant.now().minusSeconds(864000));
    insertLtftForm(UUID.randomUUID(), UNSUBMITTED, Instant.now().minusSeconds(432000));
    insertLtftForm(UUID.randomUUID(), DELETED, Instant.now().minusSeconds(259200));
    // DRAFT should not be published
    insertLtftForm(UUID.randomUUID(), DRAFT, Instant.now());

    MvcResult result = mockMvc.perform(post("/api/job/ltft/publish-refresh"))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("3"));
    verify(snsClient, times(3)).publish(any(PublishRequest.class));
  }

  @Test
  void shouldPublishOnlyLtftsModifiedOnOrAfterSinceDate() throws Exception {
    LocalDate since = LocalDate.now().minusDays(7);
    Instant cutoff = since.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

    // Modified after cutoff — should be published
    insertLtftForm(UUID.randomUUID(), SUBMITTED, Instant.now().minusSeconds(259200));
    // Modified on the cutoff boundary — should be published
    insertLtftForm(UUID.randomUUID(), UNSUBMITTED, cutoff);
    // Modified before cutoff — should NOT be published
    insertLtftForm(UUID.randomUUID(), SUBMITTED, Instant.now().minusSeconds(1728000));
    // DRAFT should never be published regardless of date
    insertLtftForm(UUID.randomUUID(), DRAFT, Instant.now());

    MvcResult result = mockMvc.perform(post("/api/job/ltft/publish-refresh")
            .param("since", since.toString()))
        .andExpect(status().isOk())
        .andReturn();

    assertThat("Unexpected publish count.", result.getResponse().getContentAsString(), is("2"));
    verify(snsClient, times(2)).publish(any(PublishRequest.class));
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private void insertFormRPartA(UUID id, LifecycleState state, LocalDateTime lastModifiedDate) {
    FormRPartA form = new FormRPartA();
    form.setId(id);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(state);
    form.setLastModifiedDate(lastModifiedDate);
    if (state == SUBMITTED || state == UNSUBMITTED || state == DELETED) {
      form.setSubmissionDate(lastModifiedDate != null ? lastModifiedDate : LocalDateTime.now());
    }
    template.insert(form);
  }

  private void insertFormRPartB(UUID id, LifecycleState state, LocalDateTime lastModifiedDate) {
    FormRPartB form = new FormRPartB();
    form.setId(id);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(state);
    form.setLastModifiedDate(lastModifiedDate);
    if (state == SUBMITTED || state == UNSUBMITTED || state == DELETED) {
      form.setSubmissionDate(lastModifiedDate != null ? lastModifiedDate : LocalDateTime.now());
    }
    template.insert(form);
  }

  private void insertLtftForm(UUID id, LifecycleState state, Instant lastModified) {
    LtftForm form = new LtftForm();
    form.setId(id);
    form.setTraineeTisId(TRAINEE_ID);

    StatusInfo statusInfo = StatusInfo.builder()
        .state(state)
        .timestamp(lastModified)
        .build();
    Status status = Status.builder()
        .current(statusInfo)
        .history(java.util.List.of(statusInfo))
        .build();
    form.setStatus(status);

    // Directly set the lastModified audit field via MongoTemplate
    // by inserting and then updating the document
    template.insert(form);
    template.updateFirst(
        org.springframework.data.mongodb.core.query.Query.query(
            org.springframework.data.mongodb.core.query.Criteria.where("_id").is(id)),
        new org.springframework.data.mongodb.core.query.Update().set("lastModified", lastModified),
        LtftForm.class);
  }
}

