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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNDER_REVIEW;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.ADMIN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.REVIEWER;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.TRAINEE;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

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
    mongoTemplate.remove(new Query(), FormRPartA.class);
  }

  @Test
  void shouldPopulateEveryHistoryEntryFromItsOwnState() {
    LtftForm form = insertLegacyLtft(DRAFT, SUBMITTED, UNDER_REVIEW, UNSUBMITTED, SUBMITTED);

    migration.migrate();

    LtftForm migrated = mongoTemplate.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), is(ADMIN));
    assertThat("Unexpected history responsible roles.", roles(migrated),
        contains(null, ADMIN, ADMIN, TRAINEE, ADMIN));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"APPROVED", "REJECTED", "WITHDRAWN"})
  void shouldLeaveFinalStatesWithoutResponsibleRole(LifecycleState finalState) {
    LtftForm form = insertLegacyLtft(SUBMITTED, UNDER_REVIEW, finalState);

    migration.migrate();

    LtftForm migrated = mongoTemplate.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), nullValue());
    assertThat("Unexpected history responsible roles.", roles(migrated),
        contains(ADMIN, ADMIN, null));
  }

  @Test
  void shouldNotPopulateDraftOnlyForms() {
    LtftForm form = insertLegacyLtft(DRAFT);

    migration.migrate();

    LtftForm migrated = mongoTemplate.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), nullValue());
    assertThat("Unexpected history responsible roles.", roles(migrated), contains(nullValue()));
  }

  @Test
  void shouldNotOverwriteExistingResponsibleRoles() {
    // Simulates entries recorded by the application, e.g. with a content-dependent role.
    StatusInfo legacy = statusInfo(SUBMITTED, null);
    StatusInfo recorded = statusInfo(UNDER_REVIEW, REVIEWER);

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .current(recorded)
        .submitted(Instant.now())
        .history(List.of(legacy, recorded))
        .build());
    form = mongoTemplate.insert(form);

    migration.migrate();

    LtftForm migrated = mongoTemplate.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), is(REVIEWER));
    assertThat("Unexpected history responsible roles.", roles(migrated),
        contains(ADMIN, REVIEWER));
  }

  @Test
  void shouldNotFailForFormsWithoutStatus() {
    LtftForm form = mongoTemplate.insert(new LtftForm());

    migration.migrate();

    LtftForm migrated = mongoTemplate.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected status.", migrated.getStatus(), nullValue());
  }

  @Test
  void shouldProduceSameResultWhenRunTwice() {
    LtftForm form = insertLegacyLtft(SUBMITTED, UNSUBMITTED);

    migration.migrate();
    migration.migrate();

    LtftForm migrated = mongoTemplate.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), is(TRAINEE));
    assertThat("Unexpected history responsible roles.", roles(migrated),
        contains(ADMIN, TRAINEE));
  }

  @Test
  void shouldNotModifyLastModifiedWhenPopulatingResponsibleRole() {
    LtftForm form = insertLegacyLtft(SUBMITTED, UNDER_REVIEW);
    Instant lastModified = mongoTemplate.findById(form.getId(), LtftForm.class).getLastModified();

    migration.migrate();

    LtftForm migrated = mongoTemplate.findById(form.getId(), LtftForm.class);
    assertThat("Unexpected last modified.", migrated.getLastModified(), is(lastModified));
  }

  @Test
  void shouldNotModifyOtherFormTypes() {
    FormRPartA formr = new FormRPartA();
    formr.setTraineeTisId("40");
    formr.setStatus(Status.builder()
        .current(statusInfo(SUBMITTED, null))
        .submitted(Instant.now())
        .history(List.of(statusInfo(SUBMITTED, null)))
        .build());
    formr = mongoTemplate.insert(formr);

    migration.migrate();

    FormRPartA migrated = mongoTemplate.findById(formr.getId(), FormRPartA.class);
    assertThat("Unexpected current responsible role.",
        migrated.getStatus().current().responsibleRole(), nullValue());
    assertThat("Unexpected history responsible role.",
        migrated.getStatus().history().get(0).responsibleRole(), nullValue());
  }

  /**
   * Insert an LTFT form as it would exist prior to the responsible role being introduced.
   *
   * @param states The states to include in the status history, the last will be current.
   * @return The inserted form.
   */
  private LtftForm insertLegacyLtft(LifecycleState... states) {
    List<StatusInfo> history = Arrays.stream(states)
        .map(s -> statusInfo(s, null))
        .toList();

    LtftForm form = new LtftForm();
    form.setStatus(Status.builder()
        .current(history.get(history.size() - 1))
        .submitted(states.length == 1 && states[0] == DRAFT ? null : Instant.now())
        .history(history)
        .build());
    return mongoTemplate.insert(form);
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
  private List<ResponsibleRole> roles(LtftForm form) {
    return form.getStatus().history().stream()
        .map(StatusInfo::responsibleRole)
        .toList();
  }
}

