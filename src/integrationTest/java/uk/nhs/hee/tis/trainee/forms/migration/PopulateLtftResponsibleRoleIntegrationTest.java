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

package uk.nhs.hee.tis.trainee.forms.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.REJECTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNDER_REVIEW;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.WITHDRAWN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.ADMIN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.REVIEWER;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.TRAINEE;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.ReviewOutcome;
import uk.nhs.hee.tis.trainee.forms.model.ReviewOutcome.Outcome;
import uk.nhs.hee.tis.trainee.forms.service.LtftSubmissionHistoryService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PopulateLtftResponsibleRoleIntegrationTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private LtftSubmissionHistoryService submissionHistoryService;

  @MockitoBean
  private SnsTemplate snsTemplate;

  private PopulateLtftResponsibleRole migration;

  @BeforeEach
  void setUp() {
    migration = new PopulateLtftResponsibleRole(mongoTemplate);
  }

  @AfterEach
  void tearDown() {
    mongoTemplate.remove(new Query(), LtftForm.class);
    mongoTemplate.remove(new Query(), LtftSubmissionHistory.class);
  }

  /**
   * Provide every combination of LTFT entity type and final lifecycle state.
   *
   * @return The entity type and final state arguments.
   */
  static Stream<Arguments> entityTypesAndFinalStates() {
    return Stream.of(LtftForm.class, LtftSubmissionHistory.class)
        .flatMap(type -> Stream.of(APPROVED, REJECTED, WITHDRAWN)
            .map(state -> Arguments.of(type, state)));
  }

  @ParameterizedTest
  @MethodSource("entityTypesAndFinalStates")
  void shouldLeaveFinalStatesWithoutResponsibleRole(
      Class<? extends AbstractAuditedForm<?>> entityType, LifecycleState finalState) {
    AbstractAuditedForm<?> form = insertLegacyLtft(entityType, SUBMITTED, UNDER_REVIEW,
        finalState);

    migration.migrate();

    AbstractAuditedForm<?> migrated = mongoTemplate.findById(form.getId(), entityType);
    Assertions.assertNotNull(migrated);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), nullValue());
    assertThat("Unexpected history responsible roles.", roles(migrated),
        contains(ADMIN, ADMIN, null));
  }

  @ParameterizedTest
  @ValueSource(classes = {LtftForm.class, LtftSubmissionHistory.class})
  void shouldPopulateReviewerRoleOnlyUntilReviewOutcomeRecorded(
      Class<? extends AbstractAuditedForm<?>> entityType) {
    Person reviewer = Person.builder().name("Tee-Pee Dee").role("REVIEWER").build();
    ReviewOutcome outcome = ReviewOutcome.builder()
        .outcome(Outcome.RECOMMEND_REJECTION)
        .outcomeComments("Some free text value")
        .build();

    List<StatusInfo> history = List.of(
        statusInfo(SUBMITTED, null, null),
        statusInfo(SUBMITTED, reviewer, null),
        statusInfo(UNDER_REVIEW, reviewer, null),
        statusInfo(UNDER_REVIEW, reviewer, outcome),
        statusInfo(UNSUBMITTED, reviewer, outcome),
        statusInfo(SUBMITTED, reviewer, outcome),
        statusInfo(UNSUBMITTED, reviewer, null),
        statusInfo(SUBMITTED, reviewer, null));
    AbstractAuditedForm<?> form = insertLtft(entityType, history);

    migration.migrate();

    AbstractAuditedForm<?> migrated = mongoTemplate.findById(form.getId(), entityType);
    Assertions.assertNotNull(migrated);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), is(REVIEWER));
    assertThat("Unexpected history responsible roles.", roles(migrated),
        contains(ADMIN, REVIEWER, REVIEWER, ADMIN, TRAINEE, ADMIN, TRAINEE, REVIEWER));
  }

  @ParameterizedTest
  @ValueSource(classes = {LtftForm.class, LtftSubmissionHistory.class})
  void shouldNotOverwriteExistingResponsibleRoles(
      Class<? extends AbstractAuditedForm<?>> entityType) {
    // Simulates entries recorded by the application, the recorded role differs from the one the
    // migration would derive to prove that it is not overwritten.
    StatusInfo legacy = statusInfo(SUBMITTED, null);
    StatusInfo recorded = statusInfo(UNDER_REVIEW, REVIEWER);
    AbstractAuditedForm<?> form = insertLtft(entityType, List.of(legacy, recorded));

    migration.migrate();

    AbstractAuditedForm<?> migrated = mongoTemplate.findById(form.getId(), entityType);
    Assertions.assertNotNull(migrated);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), is(REVIEWER));
    assertThat("Unexpected history responsible roles.", roles(migrated),
        contains(ADMIN, REVIEWER));
  }

  @ParameterizedTest
  @ValueSource(classes = {LtftForm.class, LtftSubmissionHistory.class})
  void shouldNotFailForFormsWithoutStatus(Class<? extends AbstractAuditedForm<?>> entityType) {
    AbstractAuditedForm<?> form = newInstance(entityType);
    form.setFormRef(UUID.randomUUID().toString());
    form = mongoTemplate.insert(form);

    migration.migrate();

    AbstractAuditedForm<?> migrated = mongoTemplate.findById(form.getId(), entityType);
    Assertions.assertNotNull(migrated);
    assertThat("Unexpected status.", migrated.getStatus(), nullValue());
  }

  @ParameterizedTest
  @ValueSource(classes = {LtftForm.class, LtftSubmissionHistory.class})
  void shouldNotModifyLastModifiedWhenPopulatingResponsibleRole(
      Class<? extends AbstractAuditedForm<?>> entityType) {
    AbstractAuditedForm<?> form = insertLegacyLtft(entityType, SUBMITTED, UNDER_REVIEW);
    Instant lastModified = Objects.requireNonNull(
        mongoTemplate.findById(form.getId(), entityType)).getLastModified();

    migration.migrate();

    AbstractAuditedForm<?> migrated = mongoTemplate.findById(form.getId(), entityType);
    Assertions.assertNotNull(migrated);
    assertThat("Unexpected last modified.", migrated.getLastModified(), is(lastModified));
  }

  @Test
  void shouldMatchApplicationRecordedRolesForFirstSubmission() {
    Consumer<LtftForm> lifecycle = this::submit;

    String nativeRef = runLifecycle(lifecycle);
    String legacyRef = runLifecycle(lifecycle);
    removeResponsibleRoles(legacyRef);

    migration.migrate();

    assertThat("Unexpected native form roles.", roles(findForm(nativeRef)),
        contains(null, ADMIN));
    assertMigratedMatchesApplicationRecorded(legacyRef, nativeRef);
  }

  @Test
  void shouldMatchApplicationRecordedRolesForResubmission() {
    Person reviewer = Person.builder().name("Tee-Pee Dee").role("REVIEWER").build();
    Consumer<LtftForm> lifecycle = form -> {
      submit(form);
      form.setLifecycleState(UNDER_REVIEW, null, null, form.getRevision());
      form.setAssignedReviewer(reviewer, null);
      unsubmit(form);
      submit(form);
    };

    String nativeRef = runLifecycle(lifecycle);
    String legacyRef = runLifecycle(lifecycle);
    removeResponsibleRoles(legacyRef);

    migration.migrate();

    // The assigned reviewer is carried over to the resubmission, with no review outcome recorded.
    assertThat("Unexpected native form roles.", roles(findForm(nativeRef)),
        contains(null, ADMIN, ADMIN, REVIEWER, TRAINEE, REVIEWER));
    assertMigratedMatchesApplicationRecorded(legacyRef, nativeRef);
  }

  /**
   * Run an LTFT lifecycle using the application's domain methods, taking a submission snapshot on
   * each submission, so that responsible roles are recorded as the application would.
   *
   * @param lifecycle The lifecycle steps to apply after the form is created as a draft.
   * @return The form reference of the created form and its snapshots.
   */
  private String runLifecycle(Consumer<LtftForm> lifecycle) {
    LtftForm form = new LtftForm();
    form.setFormRef(UUID.randomUUID().toString());
    form.setLifecycleState(DRAFT, null, null, 0);
    lifecycle.accept(form);
    mongoTemplate.save(form);
    return form.getFormRef();
  }

  /**
   * Submit the form and take a submission snapshot, mirroring the application's submission flow.
   *
   * @param form The form to submit.
   */
  private void submit(LtftForm form) {
    form.setLifecycleState(SUBMITTED, null, null, form.getRevision());
    LtftForm savedForm = mongoTemplate.save(form);
    submissionHistoryService.takeSnapshot(savedForm);
  }

  /**
   * Unsubmit the form, incrementing the revision as the application does.
   *
   * @param form The form to unsubmit.
   */
  private void unsubmit(LtftForm form) {
    form.setRevision(form.getRevision() + 1);
    form.setLifecycleState(UNSUBMITTED, null, null, form.getRevision());
  }

  /**
   * Remove the responsible roles from a form and its snapshots, simulating data recorded before
   * the responsible role was introduced.
   *
   * @param formRef The form reference of the form and snapshots.
   */
  private void removeResponsibleRoles(String formRef) {
    Query query = Query.query(Criteria.where("formRef").is(formRef));
    Update update = new Update()
        .unset("status.current.responsibleRole")
        .unset("status.history.$[].responsibleRole");
    mongoTemplate.updateMulti(query, update, LtftForm.class);
    mongoTemplate.updateMulti(query, update, LtftSubmissionHistory.class);

    assertThat("Unexpected legacy form roles.", roles(findForm(formRef)),
        everyItem(nullValue()));
  }

  /**
   * Assert that the migrated form and snapshots have the same responsible roles, for both the
   * current status and the status history, as those recorded by the application.
   *
   * @param migratedRef The form reference of the migrated form and snapshots.
   * @param nativeRef   The form reference of the application recorded form and snapshots.
   */
  private void assertMigratedMatchesApplicationRecorded(String migratedRef, String nativeRef) {
    LtftForm migratedForm = findForm(migratedRef);
    LtftForm nativeForm = findForm(nativeRef);
    assertThat("Unexpected form current role.",
        migratedForm.getStatus().current().responsibleRole(),
        is(nativeForm.getStatus().current().responsibleRole()));
    assertThat("Unexpected form history roles.", roles(migratedForm), is(roles(nativeForm)));

    List<LtftSubmissionHistory> migratedSnapshots = findSnapshots(migratedRef);
    List<LtftSubmissionHistory> nativeSnapshots = findSnapshots(nativeRef);
    assertThat("Unexpected snapshot count.", migratedSnapshots.size(),
        is(nativeSnapshots.size()));

    for (int i = 0; i < nativeSnapshots.size(); i++) {
      LtftSubmissionHistory migratedSnapshot = migratedSnapshots.get(i);
      LtftSubmissionHistory nativeSnapshot = nativeSnapshots.get(i);
      assertThat("Unexpected snapshot current role.",
          migratedSnapshot.getStatus().current().responsibleRole(),
          is(nativeSnapshot.getStatus().current().responsibleRole()));
      assertThat("Unexpected snapshot history roles.", roles(migratedSnapshot),
          is(roles(nativeSnapshot)));
    }
  }

  /**
   * Find the form with the given form reference.
   *
   * @param formRef The form reference.
   * @return The found form.
   */
  private LtftForm findForm(String formRef) {
    return mongoTemplate.findOne(Query.query(Criteria.where("formRef").is(formRef)),
        LtftForm.class);
  }

  /**
   * Find the submission snapshots with the given form reference, ordered by revision.
   *
   * @param formRef The form reference.
   * @return The found snapshots.
   */
  private List<LtftSubmissionHistory> findSnapshots(String formRef) {
    Query query = Query.query(Criteria.where("formRef").is(formRef))
        .with(Sort.by("revision"));
    return mongoTemplate.find(query, LtftSubmissionHistory.class);
  }

  /**
   * Insert an LTFT entity as it would exist prior to the responsible role being introduced.
   *
   * @param entityType The type of LTFT entity to insert.
   * @param states     The states to include in the status history, the last will be current.
   * @return The inserted entity.
   */
  private AbstractAuditedForm<?> insertLegacyLtft(
      Class<? extends AbstractAuditedForm<?>> entityType, LifecycleState... states) {
    List<StatusInfo> history = Arrays.stream(states)
        .map(s -> statusInfo(s, null))
        .toList();
    return insertLtft(entityType, history);
  }

  /**
   * Insert an LTFT entity with the given status history. A unique form reference is assigned, as
   * submission history requires the combination of form reference and revision to be unique.
   *
   * @param entityType The type of LTFT entity to insert.
   * @param history    The status history, the last entry will be current.
   * @return The inserted entity.
   */
  private AbstractAuditedForm<?> insertLtft(Class<? extends AbstractAuditedForm<?>> entityType,
      List<StatusInfo> history) {
    StatusInfo current = history.get(history.size() - 1);
    boolean draftOnly = history.stream().allMatch(s -> s.state() == DRAFT);

    AbstractAuditedForm<?> form = newInstance(entityType);
    form.setFormRef(UUID.randomUUID().toString());
    form.setStatus(Status.builder()
        .current(current)
        .submitted(draftOnly ? null : Instant.now())
        .history(history)
        .build());
    return mongoTemplate.insert(form);
  }

  /**
   * Create a new instance of the given entity type.
   *
   * @param entityType The type of entity to create.
   * @return The new entity instance.
   */
  private static AbstractAuditedForm<?> newInstance(
      Class<? extends AbstractAuditedForm<?>> entityType) {
    try {
      return entityType.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to create " + entityType.getSimpleName(), e);
    }
  }

  /**
   * Build a status info entry without a responsible role.
   *
   * @param state         The lifecycle state.
   * @param reviewer      The assigned reviewer, may be null.
   * @param reviewOutcome The review outcome, may be null.
   * @return The built status info.
   */
  private StatusInfo statusInfo(LifecycleState state, Person reviewer,
      ReviewOutcome reviewOutcome) {
    return StatusInfo.builder()
        .state(state)
        .assignedReviewer(reviewer)
        .reviewOutcome(reviewOutcome)
        .timestamp(Instant.now())
        .build();
  }

  /**
   * Build a status info entry.
   *
   * @param state The lifecycle state.
   * @param role  The responsible role, may be null.
   * @return The built status info.
   */
  private StatusInfo statusInfo(LifecycleState state, ResponsibleRole role) {
    return StatusInfo.builder()
        .state(state)
        .timestamp(Instant.now())
        .responsibleRole(role)
        .build();
  }

  /**
   * Get the responsible role of each status history entry.
   *
   * @param form The form to get the roles from.
   * @return The ordered responsible roles.
   */
  private List<ResponsibleRole> roles(AbstractAuditedForm<?> form) {
    return form.getStatus().history().stream()
        .map(StatusInfo::responsibleRole)
        .toList();
  }
}