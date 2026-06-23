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

package uk.nhs.hee.tis.trainee.forms.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto.FormFeatures;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto.FormFeatures.LtftFeatures;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.StatusInfoDto;
import uk.nhs.hee.tis.trainee.forms.dto.ReviewWorkflowDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.ReviewStageStatus;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.ProgrammeMembership;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LtftServiceIntegrationTest {

  // DBC codes from application.yml review-workflows config
  private static final String DBC_THREE_STAGES = "1-1RSSQ6R"; // Thames Valley: 3 stages
  private static final String DBC_ONE_STAGE = "1-1RUZV1D";    // KSS: 1 stage
  private static final String DBC_NO_WORKFLOW = "unknown-dbc"; // not in config

  private static final String TRAINEE_ID = "47165";
  private static final UUID PM_UUID = UUID.randomUUID();

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private LtftService service;

  @Autowired
  private AdminIdentity adminIdentity;

  @Autowired
  private TraineeIdentity traineeIdentity;

  @Autowired
  private MongoTemplate template;

  @MockitoBean
  private SnsTemplate snsTemplate;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    traineeIdentity.setTraineeId(TRAINEE_ID);
    traineeIdentity.setFeatures(FeaturesDto.builder()
        .forms(FormFeatures.builder()
            .ltft(LtftFeatures.builder()
                .enabled(true)
                .qualifyingProgrammes(Set.of(PM_UUID.toString()))
                .build())
            .build())
        .build());
  }

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), LtftForm.class);
    template.findAllAndRemove(new Query(), LtftSubmissionHistory.class);
  }

  @Test
  void shouldNotGenerateFormRefForDrafts() {
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("my test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftFormDto saved = service.createLtftForm(dto).orElseThrow();
    assertThat("Unexpected form ref.", saved.formRef(), nullValue());
  }

  @Test
  void shouldNotCountDraftsWhenGeneratingFormRefSuffix() {
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("my test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftFormDto draft1 = service.createLtftForm(dto).orElseThrow();
    LtftFormDto draft2 = service.createLtftForm(dto).orElseThrow();
    assertThat("Unexpected form ID.", draft1.id(), not(draft2.id()));

    LtftFormDto submitted1 = service.submitLtftForm(draft1.id(), null).orElseThrow();
    assertThat("Unexpected form ID.", submitted1.id(), is(draft1.id()));
    assertThat("Unexpected form ref.", submitted1.formRef(), is("ltft_" + TRAINEE_ID + "_001"));
  }

  @Test
  void shouldCountSubmittedWhenGeneratingFormRefSuffix() {
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("my test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftFormDto draft1 = service.createLtftForm(dto).orElseThrow();
    LtftFormDto draft2 = service.createLtftForm(dto).orElseThrow();
    assertThat("Unexpected form ID.", draft1.id(), not(draft2.id()));

    LtftFormDto submitted1 = service.submitLtftForm(draft1.id(), null).orElseThrow();
    assertThat("Unexpected form ID.", submitted1.id(), is(draft1.id()));
    assertThat("Unexpected form ref.", submitted1.formRef(), is("ltft_" + TRAINEE_ID + "_001"));

    LtftFormDto submitted2 = service.submitLtftForm(draft2.id(), null).orElseThrow();
    assertThat("Unexpected form ID.", submitted2.id(), is(draft2.id()));
    assertThat("Unexpected form ref.", submitted2.formRef(), is("ltft_" + TRAINEE_ID + "_002"));
  }

  @Test
  void shouldIncrementRevisionWhenUnsubmittedNotChangeFormRefAndTakeSnapshotsOnEachSubmission() {
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("my test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftFormDto draft = service.createLtftForm(dto).orElseThrow();

    LtftFormDto submitted = service.submitLtftForm(draft.id(), null).orElseThrow();
    assertThat("Unexpected form ID.", submitted.id(), is(draft.id()));
    assertThat("Unexpected form ref.", submitted.formRef(), is("ltft_" + TRAINEE_ID + "_001"));

    LtftFormDto.StatusDto.LftfStatusInfoDetailDto reason
        = new LtftFormDto.StatusDto.LftfStatusInfoDetailDto("reason", "message");
    LtftFormDto unsubmitted = service.unsubmitLtftForm(submitted.id(), reason).orElseThrow();
    assertThat("Unexpected form ID.", unsubmitted.id(), is(submitted.id()));
    assertThat("Unexpected form ref.", unsubmitted.formRef(), is(submitted.formRef()));

    LtftFormDto resubmitted = service.submitLtftForm(draft.id(), null).orElseThrow();
    assertThat("Unexpected form ID.", resubmitted.id(), is(draft.id()));
    assertThat("Unexpected form ref.", resubmitted.formRef(), is("ltft_" + TRAINEE_ID + "_001"));

    Query query = new Query().with(Sort.by(Sort.Direction.ASC, "revision"));
    List<LtftSubmissionHistory> savedSubmissionHistories = template.find(query,
        LtftSubmissionHistory.class);

    assertThat("Unexpected number of submission histories.",
        savedSubmissionHistories.size(), is(2));
    assertThat("Unexpected history revision.", savedSubmissionHistories.get(0).getRevision(),
        is(0));
    assertThat("Unexpected history revision.", savedSubmissionHistories.get(1).getRevision(),
        is(1));
    assertThat("Unexpected history form ref.", savedSubmissionHistories.get(0).getFormRef(),
        is(submitted.formRef()));
    assertThat("Unexpected history form ref.", savedSubmissionHistories.get(1).getFormRef(),
        is(submitted.formRef()));

    List<LtftForm> savedLtftForms = template.findAll(LtftForm.class);
    assertThat("Unexpected number of ltft forms.", savedLtftForms.size(),
        is(1));
    assertThat("Unexpected ltft revision.", savedLtftForms.get(0).getRevision(),
        is(1));
    assertThat("Unexpected ltft form ref.", savedLtftForms.get(0).getFormRef(),
        is(submitted.formRef()));
  }

  @Test
  void shouldReturnConsistentAdminSummariesWhenPagedAndSortedOnNonUniqueField() {
    String dbc = UUID.randomUUID().toString();
    adminIdentity.setGroups(Set.of(dbc));

    for (int i = 0; i < 10; i++) {
      LtftForm ltft = new LtftForm();
      ltft.setId(UUID.randomUUID());
      ltft.setContent(LtftContent.builder()
          .change(CctChange.builder()
              .startDate(LocalDate.now())
              .build())
          .programmeMembership(ProgrammeMembership.builder()
              .designatedBodyCode(dbc)
              .build())
          .build());
      ltft.setLifecycleState(LifecycleState.SUBMITTED);

      template.save(ltft);
    }

    Set<UUID> ids = new HashSet<>();
    Sort sort = Sort.by("daysToStart");

    for (int i = 0; i < 10; i++) {
      PageRequest pageable = PageRequest.of(i, 1, sort);
      Page<LtftAdminSummaryDto> summaries = service.getAdminLtftSummaries(Map.of(), pageable);
      LtftAdminSummaryDto summary = summaries.getContent().get(0);
      ids.add(summary.id());
    }

    assertThat("Unexpected ID count.", ids, hasSize(10));
  }

  @Test
  void shouldMoveLtftFormsAndSubmissionHistoryBetweenTrainees() {
    String fromTraineeId = TRAINEE_ID;
    String toTraineeId = "50";

    // Create and submit a form for the source trainee
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(fromTraineeId)
        .name("test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftFormDto draft = service.createLtftForm(dto).orElseThrow();
    service.submitLtftForm(draft.id(), null).orElseThrow();

    // Create another draft form
    service.createLtftForm(dto).orElseThrow();

    Map<String, Integer> movedStats = service.moveLtftForms(fromTraineeId, toTraineeId);

    List<LtftForm> originalTraineeForms = template.find(
        Query.query(Criteria.where("traineeTisId").is(fromTraineeId)),
        LtftForm.class);
    List<LtftForm> newTraineeForms = template.find(
        Query.query(Criteria.where("traineeTisId").is(toTraineeId)),
        LtftForm.class);

    assertThat("Unexpected forms remaining for original trainee",
        originalTraineeForms, hasSize(0));
    assertThat("Unexpected number of moved forms",
        newTraineeForms, hasSize(2));

    // Check submission history was moved
    List<LtftSubmissionHistory> originalTraineeHistory = template.find(
        Query.query(Criteria.where("traineeTisId").is(fromTraineeId)),
        LtftSubmissionHistory.class);
    List<LtftSubmissionHistory> newTraineeHistory = template.find(
        Query.query(Criteria.where("traineeTisId").is(toTraineeId)),
        LtftSubmissionHistory.class);

    assertThat("Unexpected submission history remaining for original trainee",
        originalTraineeHistory, hasSize(0));
    assertThat("Unexpected number of moved submission history records",
        newTraineeHistory, hasSize(1));
    Map<String, Integer> expectedStats = Map.of("ltft", 2, "ltft-submission", 1);
    assertThat("Unexpected move stats", movedStats, is(expectedStats));
  }

  @Test
  void shouldNotMoveFormsWhenFromTraineeHasNoForms() {
    String toTraineeId = "50";

    Map<String, Integer> movedStats = service.moveLtftForms(TRAINEE_ID, toTraineeId);

    List<LtftForm> newTraineeForms = template.find(
        Query.query(Criteria.where("traineeTisId").is(toTraineeId)),
        LtftForm.class);
    List<LtftSubmissionHistory> newTraineeHistory = template.find(
        Query.query(Criteria.where("traineeTisId").is(toTraineeId)),
        LtftSubmissionHistory.class);

    assertThat("Unexpected forms created for target trainee",
        newTraineeForms, hasSize(0));
    assertThat("Unexpected submission history created for target trainee",
        newTraineeHistory, hasSize(0));
    Map<String, Integer> expectedStats = Map.of("ltft", 0, "ltft-submission", 0);
    assertThat("Unexpected move stats", movedStats, is(expectedStats));
  }

  @Test
  void shouldNotMoveFormsWhenEitherTraineeIdIsNull() {
    String fromTraineeId = TRAINEE_ID;

    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(fromTraineeId)
        .name("test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    service.createLtftForm(dto);

    service.moveLtftForms(fromTraineeId, null);
    service.moveLtftForms(null, "50");
    service.moveLtftForms(null, null);

    List<LtftForm> originalTraineeForms = template.find(
        Query.query(Criteria.where("traineeTisId").is(fromTraineeId)),
        LtftForm.class);

    assertThat("Unexpected forms moved when trainee ID null",
        originalTraineeForms, hasSize(1));
  }

  /**
   * Save a SUBMITTED form with the given DBC and review stage directly to MongoDB, bypassing the
   * service layer to allow precise state control.
   */
  private LtftForm savedSubmittedFormWithReviewStage(String dbc, int stageIndex,
      String stageLabel) {
    LtftForm form = new LtftForm();
    form.setContent(LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(dbc)
            .build())
        .build());
    form.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(LifecycleState.SUBMITTED)
            .reviewStage(new ReviewStageStatus(stageIndex, stageLabel))
            .build())
        .history(List.of())
        .build());
    return template.save(form);
  }

  // -- getReviewWorkflow --

  @Test
  void shouldReturnEmptyWhenFormNotInAdminDbcsForReviewWorkflow() {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    LtftForm form = savedSubmittedFormWithReviewStage(DBC_NO_WORKFLOW, 0, "Triage");

    Optional<ReviewWorkflowDto> result = service.getReviewWorkflow(form.getId());

    assertThat("Unexpected result presence.", result.isPresent(), is(false));
  }

  @Test
  void shouldReturnEmptyStagesForDbcWithNoConfiguredWorkflow() {
    adminIdentity.setGroups(Set.of(DBC_NO_WORKFLOW));

    LtftForm form = savedSubmittedFormWithReviewStage(DBC_NO_WORKFLOW, 0, "Triage");

    Optional<ReviewWorkflowDto> result = service.getReviewWorkflow(form.getId());

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected stages.", result.get().stages(), empty());
    assertThat("Unexpected current stage.", result.get().currentStage(), nullValue());
  }

  @Test
  void shouldReturnConfiguredStageLabelsForKnownDbc() {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    LtftForm form = new LtftForm();
    form.setContent(LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_THREE_STAGES)
            .build())
        .build());
    template.save(form); // not submitted — no review stage

    Optional<ReviewWorkflowDto> result = service.getReviewWorkflow(form.getId());

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected stages.", result.get().stages(), contains(
        "Programme/Education Team Triage",
        "Programme Manager Review",
        "Associate Dean Approval"));
    assertThat("Unexpected current stage.", result.get().currentStage(), nullValue());
  }

  @Test
  void shouldReturnCurrentStageIndexWhenFormIsSubmittedWithReviewStage() {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    // Form is at stage 1 (Programme Manager Review) — visible position is 1.
    LtftForm form = savedSubmittedFormWithReviewStage(
        DBC_THREE_STAGES, 1, "Programme Manager Review");

    Optional<ReviewWorkflowDto> result = service.getReviewWorkflow(form.getId());

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected current stage.", result.get().currentStage(), is(1));
  }

  // -- advanceReviewStage --

  @Test
  void shouldReturnEmptyWhenFormNotFoundForAdvanceReviewStage()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    Optional<LtftFormDto> result = service.advanceReviewStage(UUID.randomUUID(), null);

    assertThat("Unexpected result presence.", result.isPresent(), is(false));
  }

  @Test
  void shouldThrowExceptionWhenFormNotSubmittedForAdvanceReviewStage() {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    LtftForm form = new LtftForm();
    form.setContent(LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_THREE_STAGES)
            .build())
        .build());
    form.setLifecycleState(LifecycleState.DRAFT);
    template.save(form);

    assertThrows(MethodArgumentNotValidException.class,
        () -> service.advanceReviewStage(form.getId(), null));
  }

  @Test
  void shouldThrowExceptionWhenAtFinalReviewStageForAdvanceReviewStage() {
    adminIdentity.setGroups(Set.of(DBC_ONE_STAGE));

    // KSS has only one stage (index 0 = final).
    LtftForm form = savedSubmittedFormWithReviewStage(
        DBC_ONE_STAGE, 0, "Completeness checks");

    assertThrows(MethodArgumentNotValidException.class,
        () -> service.advanceReviewStage(form.getId(), null));
  }

  @Test
  void shouldAdvanceReviewStageAndPersistToDatabase() throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    LtftForm form = savedSubmittedFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Programme/Education Team Triage");

    Optional<LtftFormDto> result = service.advanceReviewStage(form.getId(), null);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));

    // Verify the DTO reflects the new stage.
    StatusInfoDto current = result.get().status().current();
    assertThat("Unexpected state.", current.state(), is(LifecycleState.SUBMITTED));
    assertThat("Unexpected review stage.", current.reviewStage(),
        is(new ReviewStageStatus(1, "Programme Manager Review")));

    // Verify the change was persisted to MongoDB.
    LtftForm persisted = template.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected persisted form.", persisted, notNullValue());
    assertThat("Unexpected persisted review stage.",
        persisted.getStatus().current().reviewStage(),
        is(new ReviewStageStatus(1, "Programme Manager Review")));
  }

  @Test
  void shouldNotCreateSubmissionHistoryEntryWhenAdvancingReviewStage()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    LtftForm form = savedSubmittedFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Programme/Education Team Triage");

    service.advanceReviewStage(form.getId(), null);

    List<LtftSubmissionHistory> histories = template.findAll(LtftSubmissionHistory.class);
    assertThat("Unexpected submission history entries.", histories, empty());
  }

  @Test
  void shouldStoreDetailAndModifiedByWhenAdvancingReviewStageWithDetail()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    LtftForm form = savedSubmittedFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Programme/Education Team Triage");

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("Triage complete",
        "All checks passed.");

    Optional<LtftFormDto> result = service.advanceReviewStage(form.getId(), detail);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    StatusInfoDto current = result.get().status().current();
    assertThat("Unexpected detail reason.", current.detail().reason(), is("Triage complete"));
    assertThat("Unexpected detail message.", current.detail().message(), is("All checks passed."));
    assertThat("Unexpected modifiedBy name.", current.modifiedBy().name(), is("Ad Min"));
    assertThat("Unexpected modifiedBy email.", current.modifiedBy().email(), is("ad.min@test.com"));
    assertThat("Unexpected modifiedBy role.", current.modifiedBy().role(), is("ADMIN"));
  }

  // -- review stage set/cleared during lifecycle transitions --

  @Test
  void shouldSetFirstReviewStageWhenFormSubmittedWithConfiguredDbc() {
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .designatedBodyCode(DBC_THREE_STAGES)
            .build())
        .build();

    LtftFormDto draft = service.createLtftForm(dto).orElseThrow();
    LtftFormDto submitted = service.submitLtftForm(draft.id(), null).orElseThrow();

    StatusInfoDto current = submitted.status().current();
    assertThat("Unexpected state.", current.state(), is(LifecycleState.SUBMITTED));
    assertThat("Unexpected review stage index.", current.reviewStage().index(), is(0));
    assertThat("Unexpected review stage label.", current.reviewStage().label(),
        is("Programme/Education Team Triage"));
  }

  @Test
  void shouldNotSetReviewStageWhenFormSubmittedWithUnconfiguredDbc() {
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .designatedBodyCode(DBC_NO_WORKFLOW)
            .build())
        .build();

    LtftFormDto draft = service.createLtftForm(dto).orElseThrow();
    LtftFormDto submitted = service.submitLtftForm(draft.id(), null).orElseThrow();

    assertThat("Unexpected review stage for unconfigured DBC.",
        submitted.status().current().reviewStage(), nullValue());
  }

  @Test
  void shouldThrowWhenApprovingFormNotAtFinalReviewStage() {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    // Form at stage 0 — not the final stage (final is index 2).
    LtftForm form = savedSubmittedFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Programme/Education Team Triage");

    assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(form.getId(), LifecycleState.APPROVED, null));
  }

  @Test
  void shouldAllowApprovalWhenAtFinalReviewStage() throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_ONE_STAGE));

    // KSS single stage: index 0 = final.
    LtftForm form = savedSubmittedFormWithReviewStage(DBC_ONE_STAGE, 0, "Completeness checks");

    Optional<LtftFormDto> result = service.updateStatusAsAdmin(
        form.getId(), LifecycleState.APPROVED, null);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected state.", result.get().status().current().state(),
        is(LifecycleState.APPROVED));
  }

  @Test
  void shouldClearReviewStageWhenFormTransitionsOutOfSubmitted()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_ONE_STAGE));

    // Single-stage workflow: at final stage, can approve.
    LtftForm form = savedSubmittedFormWithReviewStage(DBC_ONE_STAGE, 0, "Completeness checks");

    Optional<LtftFormDto> result = service.updateStatusAsAdmin(
        form.getId(), LifecycleState.APPROVED, null);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected review stage after approval.",
        result.get().status().current().reviewStage(), nullValue());
  }

  @Test
  void shouldAllowUnsubmitFromAnyReviewStageAndClearReviewStage()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    // Form at non-final stage (index 0) — UNSUBMIT is always allowed.
    LtftForm form = savedSubmittedFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Programme/Education Team Triage");

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("trainee request", "notes");
    Optional<LtftFormDto> result = service.updateStatusAsAdmin(
        form.getId(), LifecycleState.UNSUBMITTED, detail);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected state.", result.get().status().current().state(),
        is(LifecycleState.UNSUBMITTED));
    assertThat("Unexpected review stage after unsubmit.",
        result.get().status().current().reviewStage(), nullValue());
  }

  @Test
  void shouldRestartFromFirstReviewStageWhenResubmittingAfterUnsubmit()
      throws MethodArgumentNotValidException {
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("test form")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .designatedBodyCode(DBC_THREE_STAGES)
            .build())
        .build();

    LtftFormDto draft = service.createLtftForm(dto).orElseThrow();
    LtftFormDto submitted = service.submitLtftForm(draft.id(), null).orElseThrow();

    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));
    // Advance to stage 1 then unsubmit.
    service.advanceReviewStage(submitted.id(), null).orElseThrow();
    LftfStatusInfoDetailDto reason = new LftfStatusInfoDetailDto("reason", "message");
    service.unsubmitLtftForm(submitted.id(), reason).orElseThrow();

    // Re-submit: review stage should restart from stage 0.
    LtftFormDto resubmitted = service.submitLtftForm(draft.id(), null).orElseThrow();

    StatusInfoDto current = resubmitted.status().current();
    assertThat("Unexpected review stage index on re-submit.",
        current.reviewStage().index(), is(0));
    assertThat("Unexpected review stage label on re-submit.",
        current.reviewStage().label(), is("Programme/Education Team Triage"));
  }
}
