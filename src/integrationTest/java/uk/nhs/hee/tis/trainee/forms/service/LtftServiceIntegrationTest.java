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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

  // DBC codes from application-test.yml review-workflows config (test-specific, not production)
  private static final String DBC_THREE_STAGES = "TEST-DBC-3-STAGES";
  private static final String DBC_ONE_STAGE = "TEST-DBC-1-STAGE";
  private static final String DBC_NO_WORKFLOW = "unknown-dbc"; // not in config
  private static final String DBC_DISABLED_FIRST = "TEST-DBC-DISABLED-FIRST";
  private static final String DBC_MIXED = "TEST-DBC-MIXED";
  private static final String DBC_ALL_DISABLED = "TEST-DBC-DISABLED";

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
   * Save an UNDER_REVIEW form with the given DBC and review stage directly to MongoDB, bypassing the
   * service layer to allow precise state control.
   */
  private LtftForm savedUnderReviewFormWithReviewStage(String dbc, int stageIndex,
      String stageLabel) {
    LtftForm form = new LtftForm();
    form.setContent(LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(dbc)
            .build())
        .build());
    form.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(LifecycleState.UNDER_REVIEW)
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

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_NO_WORKFLOW, 0, "Triage");

    Optional<ReviewWorkflowDto> result = service.getReviewWorkflow(form.getId());

    assertThat("Unexpected result presence.", result.isPresent(), is(false));
  }

  @Test
  void shouldReturnEmptyStagesForDbcWithNoConfiguredWorkflow() {
    adminIdentity.setGroups(Set.of(DBC_NO_WORKFLOW));

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_NO_WORKFLOW, 0, "Triage");

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
        "Stage One",
        "Stage Two",
        "Stage Three",
        "Review complete"));
    assertThat("Unexpected current stage.", result.get().currentStage(), nullValue());
  }

  @Test
  void shouldReturnCurrentStageIndexWhenFormIsSubmittedWithReviewStage() {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    // Form is at stage 1 (Stage Two) — visible position is 1.
    LtftForm form = savedUnderReviewFormWithReviewStage(
        DBC_THREE_STAGES, 1, "Stage Two");

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
  void shouldAdvanceToTerminalStageWhenAtFinalConfiguredStage()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_ONE_STAGE));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    // KSS has only one stage (index 0 = final configured stage).
    LtftForm form = savedUnderReviewFormWithReviewStage(
        DBC_ONE_STAGE, 0, "Single Review");

    Optional<LtftFormDto> result = service.advanceReviewStage(form.getId(), null);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    StatusInfoDto current = result.get().status().current();
    assertThat("Unexpected state.", current.state(), is(LifecycleState.UNDER_REVIEW));
    assertThat("Unexpected review stage index.", current.reviewStage().index(), is(1));
    assertThat("Unexpected review stage label.", current.reviewStage().label(),
        is("Review complete"));
  }

  @Test
  void shouldThrowExceptionWhenAlreadyAtTerminalStageForAdvanceReviewStage() {
    adminIdentity.setGroups(Set.of(DBC_ONE_STAGE));

    // KSS single stage: terminal stage is at index 1.
    LtftForm form = savedUnderReviewFormWithReviewStage(
        DBC_ONE_STAGE, 1, "Review complete");

    assertThrows(MethodArgumentNotValidException.class,
        () -> service.advanceReviewStage(form.getId(), null));
  }

  @Test
  void shouldAdvanceReviewStageAndPersistToDatabase() throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    LtftForm form = savedUnderReviewFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Stage One");

    Optional<LtftFormDto> result = service.advanceReviewStage(form.getId(), null);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));

    // Verify the DTO reflects the new stage.
    StatusInfoDto current = result.get().status().current();
    assertThat("Unexpected state.", current.state(), is(LifecycleState.UNDER_REVIEW));
    assertThat("Unexpected review stage.", current.reviewStage(),
        is(new ReviewStageStatus(1, "Stage Two")));

    // Verify the change was persisted to MongoDB.
    LtftForm persisted = template.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected persisted form.", persisted, notNullValue());
    assertThat("Unexpected persisted review stage.",
        persisted.getStatus().current().reviewStage(),
        is(new ReviewStageStatus(1, "Stage Two")));
  }

  @Test
  void shouldNotCreateSubmissionHistoryEntryWhenAdvancingReviewStage()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    LtftForm form = savedUnderReviewFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Stage One");

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

    LtftForm form = savedUnderReviewFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Stage One");

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
  void shouldSetFirstReviewStageWhenReviewStartedWithConfiguredDbc()
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

    assertThat("Unexpected review stage before review starts.",
        submitted.status().current().reviewStage(), nullValue());

    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));
    LtftFormDto underReview = service.startReview(submitted.id()).orElseThrow();

    StatusInfoDto current = underReview.status().current();
    assertThat("Unexpected state.", current.state(), is(LifecycleState.UNDER_REVIEW));
    assertThat("Unexpected review stage index.", current.reviewStage().index(), is(0));
    assertThat("Unexpected review stage label.", current.reviewStage().label(),
        is("Stage One"));
  }

  @Test
  void shouldNotSetReviewStageWhenReviewStartedWithUnconfiguredDbc()
      throws MethodArgumentNotValidException {
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

    adminIdentity.setGroups(Set.of(DBC_NO_WORKFLOW));
    LtftFormDto underReview = service.startReview(submitted.id()).orElseThrow();

    assertThat("Unexpected review stage for unconfigured DBC.",
        underReview.status().current().reviewStage(), nullValue());
  }

  @Test
  void shouldThrowWhenApprovingFormNotAtFinalReviewStage() {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    // Form at stage 0 — not the final stage (final is index 2).
    LtftForm form = savedUnderReviewFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Stage One");

    assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(form.getId(), LifecycleState.APPROVED, null));
  }

  @Test
  void shouldAllowApprovalWhenAtTerminalReviewStage() throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_ONE_STAGE));

    // KSS single stage: terminal stage is at index 1 (= stages.size()).
    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_ONE_STAGE, 1, "Review complete");

    Optional<LtftFormDto> result = service.updateStatusAsAdmin(
        form.getId(), LifecycleState.APPROVED, null);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected state.", result.get().status().current().state(),
        is(LifecycleState.APPROVED));
  }

  @Test
  void shouldThrowWhenApprovingFormAtFinalConfiguredStageBeforeTerminal() {
    adminIdentity.setGroups(Set.of(DBC_ONE_STAGE));

    // KSS single stage: at configured stage 0, not yet at terminal.
    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_ONE_STAGE, 0, "Single Review");

    assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(form.getId(), LifecycleState.APPROVED, null));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED"})
  void shouldAllowTerminalTransitionForPreWorkflowFormWithNoReviewStage(
      LifecycleState targetState) throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));

    // Simulate a pre-workflow form: UNDER_REVIEW with no reviewStage, for a DBC that now has a
    // 3-stage workflow. This mirrors forms that were already under review when review stages were
    // first deployed for the DBC.
    LtftForm form = new LtftForm();
    form.setContent(LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_THREE_STAGES)
            .build())
        .build());
    form.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(LifecycleState.UNDER_REVIEW)
            .build()) // no reviewStage — submitted before workflow was deployed
        .history(List.of())
        .build());
    template.save(form);

    // Provide a reason; required for REJECTED, harmless for APPROVED.
    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("pre-workflow transition", null);

    Optional<LtftFormDto> result = service.updateStatusAsAdmin(form.getId(), targetState, detail);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected state.", result.get().status().current().state(), is(targetState));
    assertThat("Unexpected review stage after transition.",
        result.get().status().current().reviewStage(), nullValue());
  }

  @Test
  void shouldAllowTraineeWithdrawForPreWorkflowFormWithNoReviewStage() {
    // Simulate a pre-workflow form: UNDER_REVIEW with no reviewStage. Withdraw is trainee-only.
    LtftForm form = new LtftForm();
    form.setTraineeTisId(TRAINEE_ID);
    form.setContent(LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .designatedBodyCode(DBC_THREE_STAGES)
            .build())
        .build());
    form.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(LifecycleState.UNDER_REVIEW)
            .build()) // no reviewStage — submitted before workflow was deployed
        .history(List.of())
        .build());
    template.save(form);

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("pre-workflow withdraw", null);

    Optional<LtftFormDto> result = service.withdrawLtftForm(form.getId(), detail);

    assertThat("Unexpected result presence.", result.isPresent(), is(true));
    assertThat("Unexpected state.", result.get().status().current().state(),
        is(LifecycleState.WITHDRAWN));
    assertThat("Unexpected review stage after withdraw.",
        result.get().status().current().reviewStage(), nullValue());
  }

  @Test
  void shouldClearReviewStageWhenFormTransitionsOutOfSubmitted()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_ONE_STAGE));

    // Single-stage workflow: at terminal stage (index 1), can approve.
    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_ONE_STAGE, 1, "Review complete");

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
    LtftForm form = savedUnderReviewFormWithReviewStage(
        DBC_THREE_STAGES, 0, "Stage One");

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
    // Start the review, advance to stage 1, then unsubmit (admin-only from UNDER_REVIEW).
    service.startReview(submitted.id()).orElseThrow();
    service.advanceReviewStage(submitted.id(), null).orElseThrow();
    LftfStatusInfoDetailDto reason = new LftfStatusInfoDetailDto("reason", "message");
    service.updateStatusAsAdmin(submitted.id(), LifecycleState.UNSUBMITTED, reason).orElseThrow();

    // Re-submit and re-start review: review stage should restart from stage 0.
    service.submitLtftForm(draft.id(), null).orElseThrow();
    LtftFormDto resubmitted = service.startReview(submitted.id()).orElseThrow();

    StatusInfoDto current = resubmitted.status().current();
    assertThat("Unexpected review stage index on re-submit.",
        current.reviewStage().index(), is(0));
    assertThat("Unexpected review stage label on re-submit.",
        current.reviewStage().label(), is("Stage One"));
  }

  // -- startReview --

  @Test
  void shouldTransitionToUnderReviewAndSelfAssignWhenStartingReview()
      throws MethodArgumentNotValidException {
    adminIdentity.setGroups(Set.of(DBC_THREE_STAGES));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

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

    LtftFormDto underReview = service.startReview(submitted.id()).orElseThrow();

    StatusInfoDto current = underReview.status().current();
    assertThat("Unexpected state.", current.state(), is(LifecycleState.UNDER_REVIEW));
    assertThat("Unexpected self-assigned admin email.", current.assignedAdmin().email(),
        is("ad.min@test.com"));
    assertThat("Unexpected first review stage index.", current.reviewStage().index(), is(0));
    assertThat("Unexpected first review stage label.", current.reviewStage().label(),
        is("Stage One"));
  }

  // -- disabled stage index consistency --

  @Test
  void shouldAssignVisibleIndexZeroWhenFirstConfiguredStageIsDisabled()
      throws MethodArgumentNotValidException {
    // TEST-DBC-DISABLED-FIRST: [Triage(disabled), Education Review(enabled), APD Approval(enabled)]
    // First enabled stage should get visible index 0, not absolute index 1.
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("disabled first stage test")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .designatedBodyCode(DBC_DISABLED_FIRST)
            .build())
        .build();

    LtftFormDto draft = service.createLtftForm(dto).orElseThrow();
    LtftFormDto submitted = service.submitLtftForm(draft.id(), null).orElseThrow();

    adminIdentity.setGroups(Set.of(DBC_DISABLED_FIRST));
    LtftFormDto underReview = service.startReview(submitted.id()).orElseThrow();

    StatusInfoDto current = underReview.status().current();
    assertThat("Unexpected state.", current.state(), is(LifecycleState.UNDER_REVIEW));
    assertThat("Unexpected review stage index — should be visible index 0.",
        current.reviewStage().index(), is(0));
    assertThat("Unexpected review stage label.",
        current.reviewStage().label(), is("Education Review"));
  }

  @Test
  void shouldReturnConsistentIndexBetweenFormStatusAndWorkflowEndpoint()
      throws MethodArgumentNotValidException {
    // Start review on a form for a DBC with a disabled first stage, then verify that the index in
    // the form's status matches the currentStage from the review-workflow endpoint.
    adminIdentity.setGroups(Set.of(DBC_DISABLED_FIRST));

    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("consistency test")
        .programmeMembership(LtftFormDto.ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .designatedBodyCode(DBC_DISABLED_FIRST)
            .build())
        .build();

    LtftFormDto draft = service.createLtftForm(dto).orElseThrow();
    LtftFormDto submitted = service.submitLtftForm(draft.id(), null).orElseThrow();
    LtftFormDto underReview = service.startReview(submitted.id()).orElseThrow();

    int formStatusIndex = underReview.status().current().reviewStage().index();
    Optional<ReviewWorkflowDto> workflow = service.getReviewWorkflow(underReview.id());

    assertThat("Workflow should be present.", workflow.isPresent(), is(true));
    assertThat("Form status index should match workflow endpoint currentStage.",
        formStatusIndex, is(workflow.get().currentStage()));
  }

  @Test
  void shouldAdvanceThroughStagesWithCorrectVisibleIndicesWhenMiddleStageDisabled()
      throws MethodArgumentNotValidException {
    // TEST-DBC-MIXED: A(enabled=0), B(disabled), C(enabled=1), D(enabled=2)
    // Advance: A(0) → C(1) → D(2) → terminal(3)
    adminIdentity.setGroups(Set.of(DBC_MIXED));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_MIXED, 0, "Stage A");

    // Advance from A(0) → should skip B(disabled) → C(1)
    Optional<LtftFormDto> step1 = service.advanceReviewStage(form.getId(), null);
    assertThat("Step 1 result presence.", step1.isPresent(), is(true));
    assertThat("Step 1 index.", step1.get().status().current().reviewStage().index(), is(1));
    assertThat("Step 1 label.", step1.get().status().current().reviewStage().label(),
        is("Stage C"));

    // Advance from C(1) → D(2)
    Optional<LtftFormDto> step2 = service.advanceReviewStage(form.getId(), null);
    assertThat("Step 2 result presence.", step2.isPresent(), is(true));
    assertThat("Step 2 index.", step2.get().status().current().reviewStage().index(), is(2));
    assertThat("Step 2 label.", step2.get().status().current().reviewStage().label(),
        is("Stage D"));

    // Advance from D(2) → terminal(3 = enabled count)
    Optional<LtftFormDto> step3 = service.advanceReviewStage(form.getId(), null);
    assertThat("Step 3 result presence.", step3.isPresent(), is(true));
    assertThat("Step 3 (terminal) index.", step3.get().status().current().reviewStage().index(),
        is(3));
    assertThat("Step 3 (terminal) label.", step3.get().status().current().reviewStage().label(),
        is("Review complete"));
  }

  @Test
  void shouldAdvanceThroughStagesWithCorrectVisibleIndicesWhenFirstStageDisabled()
      throws MethodArgumentNotValidException {
    // TEST-DBC-DISABLED-FIRST: [Triage(disabled), Education Review(enabled=0),
    //                           APD Approval(enabled=1)]
    // Advance: Education(0) → APD(1) → terminal(2)
    adminIdentity.setGroups(Set.of(DBC_DISABLED_FIRST));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_DISABLED_FIRST, 0, "Education Review");

    // Advance from Education Review(0) → APD Approval(1)
    Optional<LtftFormDto> step1 = service.advanceReviewStage(form.getId(), null);
    assertThat("Step 1 result presence.", step1.isPresent(), is(true));
    assertThat("Step 1 index.", step1.get().status().current().reviewStage().index(), is(1));
    assertThat("Step 1 label.", step1.get().status().current().reviewStage().label(),
        is("APD Approval"));

    // Advance from APD Approval(1) → terminal(2 = enabled count)
    Optional<LtftFormDto> step2 = service.advanceReviewStage(form.getId(), null);
    assertThat("Step 2 result presence.", step2.isPresent(), is(true));
    assertThat("Step 2 (terminal) index.", step2.get().status().current().reviewStage().index(),
        is(2));
    assertThat("Step 2 (terminal) label.", step2.get().status().current().reviewStage().label(),
        is("Review complete"));
  }

  @Test
  void shouldAdvanceFromDisabledStageToNextEnabledStageWithCorrectIndex()
      throws MethodArgumentNotValidException {
    // TEST-DBC-MIXED: A(enabled=0), B(disabled), C(enabled=1), D(enabled=2)
    // Form is at B (entered when B was enabled). Should advance to C with visible index 1.
    adminIdentity.setGroups(Set.of(DBC_MIXED));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    // Simulate a form that entered B when it was enabled (visible index was 1 at that time).
    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_MIXED, 1, "Stage B");

    Optional<LtftFormDto> result = service.advanceReviewStage(form.getId(), null);
    assertThat("Result presence.", result.isPresent(), is(true));
    // Note that reviewStage.index remains 1: this is because the disabled stage it moved out of
    // is no longer counted (and will no longer be visible in the review-workflow either).
    assertThat("Next stage index.", result.get().status().current().reviewStage().index(), is(1));
    assertThat("Next stage label.", result.get().status().current().reviewStage().label(),
        is("Stage C"));
  }

  @Test
  void shouldReturnCorrectWorkflowStagesAndCurrentPositionForDisabledFirstStage() {
    // Verify the workflow endpoint returns only enabled stages + terminal,
    // with the correct currentStage position.
    adminIdentity.setGroups(Set.of(DBC_DISABLED_FIRST));

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_DISABLED_FIRST, 0, "Education Review");

    Optional<ReviewWorkflowDto> workflow = service.getReviewWorkflow(form.getId());

    assertThat("Workflow should be present.", workflow.isPresent(), is(true));
    assertThat("Unexpected stages.", workflow.get().stages(),
        contains("Education Review", "APD Approval", "Review complete"));
    assertThat("Unexpected currentStage.", workflow.get().currentStage(), is(0));
  }

  @Test
  void shouldReturnCorrectWorkflowStagesAndCurrentPositionForMixedDisabledStages() {
    // TEST-DBC-MIXED at Stage D (visible index 2).
    adminIdentity.setGroups(Set.of(DBC_MIXED));

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_MIXED, 2, "Stage D");

    Optional<ReviewWorkflowDto> workflow = service.getReviewWorkflow(form.getId());

    assertThat("Workflow should be present.", workflow.isPresent(), is(true));
    assertThat("Unexpected stages.", workflow.get().stages(),
        contains("Stage A", "Stage C", "Stage D", "Review complete"));
    assertThat("Unexpected currentStage.", workflow.get().currentStage(), is(2));
  }

  // -- all-disabled DBC (TEST-DBC-DISABLED) in-flight form behaviour --

  @Test
  void shouldAppendTerminalStageInWorkflowWhenFormIsAtDisabledStageInAllDisabledDbc() {
    // TEST-DBC-DISABLED has a single disabled stage "Disabled Stage".
    // A form in-flight at that stage should see the terminal stage appended.
    adminIdentity.setGroups(Set.of(DBC_ALL_DISABLED));

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_ALL_DISABLED, 0, "Disabled Stage");

    Optional<ReviewWorkflowDto> workflow = service.getReviewWorkflow(form.getId());

    assertThat("Workflow should be present.", workflow.isPresent(), is(true));
    assertThat("Expected disabled stage and terminal stage.", workflow.get().stages(),
        contains("Disabled Stage", "Review complete"));
    assertThat("Expected current stage to be at the disabled stage.",
        workflow.get().currentStage(), is(0));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldDenyTerminalTransitionWhenFormIsAtDisabledStageInAllDisabledDbc(
      LifecycleState targetState) {
    // Form is in-flight at the disabled stage — must advance to "Review complete" first.
    adminIdentity.setGroups(Set.of(DBC_ALL_DISABLED));

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_ALL_DISABLED, 0, "Disabled Stage");

    assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(form.getId(), targetState, null),
        "Expected terminal transition to be denied from disabled stage.");
  }

  @Test
  void shouldAdvanceFromDisabledStageToTerminalInAllDisabledDbc()
      throws MethodArgumentNotValidException {
    // Form is in-flight at the disabled stage — advance should go to "Review complete".
    adminIdentity.setGroups(Set.of(DBC_ALL_DISABLED));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_ALL_DISABLED, 0, "Disabled Stage");

    Optional<LtftFormDto> result = service.advanceReviewStage(form.getId(), null);

    assertThat("Expected advancement to succeed.", result.isPresent(), is(true));
    assertThat("Unexpected terminal stage index.",
        result.get().status().current().reviewStage().index(), is(0));
    assertThat("Unexpected terminal stage label.",
        result.get().status().current().reviewStage().label(), is("Review complete"));
  }

  @Test
  void shouldAllowApprovalAfterAdvancingToTerminalInAllDisabledDbc()
      throws MethodArgumentNotValidException {
    // Full lifecycle: advance to terminal, then approve.
    adminIdentity.setGroups(Set.of(DBC_ALL_DISABLED));
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@test.com");

    LtftForm form = savedUnderReviewFormWithReviewStage(DBC_ALL_DISABLED, 0, "Disabled Stage");

    // Advance to terminal stage.
    service.advanceReviewStage(form.getId(), null);

    // Now approve should succeed.
    Optional<LtftFormDto> approved = service.updateStatusAsAdmin(
        form.getId(), LifecycleState.APPROVED, null);

    assertThat("Expected approval to succeed.", approved.isPresent(), is(true));
    assertThat("Unexpected state.", approved.get().status().current().state(),
        is(LifecycleState.APPROVED));
  }
}
