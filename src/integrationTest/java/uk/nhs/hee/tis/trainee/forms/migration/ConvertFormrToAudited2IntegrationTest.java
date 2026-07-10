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
 *
 */

package uk.nhs.hee.tis.trainee.forms.migration;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.AbstractFormR;
import uk.nhs.hee.tis.trainee.forms.model.CovidDeclaration;
import uk.nhs.hee.tis.trainee.forms.model.Declaration;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.model.content.FormContent;
import uk.nhs.hee.tis.trainee.forms.model.content.FormrPartbContent;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormrPartbSubmissionHistoryRepository;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ConvertFormrToAudited2IntegrationTest {

  private static final String FIELD_FORM_ID = "_id";
  private static final String FIELD_TRAINEE_ID = "traineeTisId";
  private static final String FIELD_LIFECYCLE_STATE = "lifecycleState";
  private static final String FIELD_SUBMISSION_DATE = "submissionDate";
  private static final String FIELD_LAST_MODIFIED_DATE = "lastModifiedDate";
  private static final String FIELD_FORM_CLASS = "_class";

  private static TimeZone originalTimeZone;

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @BeforeAll
  static void setUpBeforeAll() {
    originalTimeZone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @AfterAll
  static void tearDownAfterAll() {
    TimeZone.setDefault(originalTimeZone);
  }

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private FormRPartBRepository partbRepository;

  @Autowired
  private FormrPartbSubmissionHistoryRepository partbSubmissionHistoryRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private ConvertFormrToAudited2 migration;

  @BeforeEach
  void setUp() {
    migration = new ConvertFormrToAudited2(mongoTemplate);
  }

  @AfterEach
  void tearDown() {
    mongoTemplate.remove(new Query(), FormRPartB.class);
    mongoTemplate.remove(new Query(), FormrPartbSubmissionHistory.class);
  }

  @Test
  void shouldMigrateFormMetadata() {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(FormRPartB.class);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateFormrPartb();

    Optional<? extends AbstractFormR<?>> foundForm = partbRepository.findById(formId);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(FormRPartB.class));

    assertThat("Unexpected form ID.", migratedForm.getId(), is(formId));
    assertThat("Unexpected trainee ID.", migratedForm.getTraineeTisId(), is(traineeId));

    String expectedFormRef = "formr_partb_%s_001".formatted(traineeId);
    assertThat("Unexpected form reference.", migratedForm.getFormRef(), is(expectedFormRef));
    assertThat("Unexpected revision.", migratedForm.getRevision(), is(0));

    assertThat("Unexpected lifecycle state.", migratedForm.getLifecycleState(), is(SUBMITTED));
    assertThat("Unexpected created.", migratedForm.getCreated(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));
    assertThat("Unexpected last modified.", migratedForm.getLastModified(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));
  }

  @Test
  void shouldMigrateFormStatus() {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(FormRPartB.class);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateFormrPartb();

    Optional<? extends AbstractFormR<?>> foundForm = partbRepository.findById(formId);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(FormRPartB.class));

    Status status = migratedForm.getStatus();
    assertThat("Unexpected submitted timestamp.", status.submitted(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));

    StatusInfo current = status.current();
    assertThat("Unexpected current state.", current.state(), is(SUBMITTED));
    assertThat("Unexpected current revision.", current.revision(), is(0));
    assertThat("Unexpected current modified by.", current.modifiedBy(), is(Person.builder()
        .name("forename_1 surname_1")
        .email("email_1")
        .role("TRAINEE")
        .build()
    ));
    assertThat("Unexpected current assigned admin.", current.assignedAdmin(), nullValue());
    assertThat("Unexpected current detail.", current.detail(), nullValue());

    // The status timestamp is based on S3 version history, we cannot assert exact values because it
    // is tied to the insertion into the localstack S3 instance and NOT any data we easily control.
    assertThat("Unexpected current timestamp.", current.timestamp(), notNullValue());

    List<StatusInfo> statusHistory = status.history();
    assertThat("Unexpected status history count.", statusHistory, hasSize(2));
    assertThat("Unexpected lastest status history.", statusHistory.get(1), is(current));

    StatusInfo status1 = statusHistory.get(0);
    assertThat("Unexpected history state.", status1.state(), is(DRAFT));
    assertThat("Unexpected history revision.", status1.revision(), is(0));
    assertThat("Unexpected history modified by.", status1.modifiedBy(), is(Person.builder()
        .name("forename_1 surname_1")
        .email("email_1")
        .role("TRAINEE")
        .build()
    ));
    assertThat("Unexpected history assigned admin.", status1.assignedAdmin(), nullValue());
    assertThat("Unexpected history detail.", status1.detail(), nullValue());
    assertThat("Unexpected history timestamp.", status1.timestamp(), notNullValue());
  }

  @Test
  void shouldMigrateFormContent() {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(FormRPartB.class);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateFormrPartb();

    Optional<? extends AbstractFormR<?>> foundForm = partbRepository.findById(formId);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(FormRPartB.class));

    FormContent content = migratedForm.getContent();
    assertPartbContent((FormrPartbContent) content, originalFields);
  }

  @Test
  void shouldMigrateSubmittedHistory() {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().plusDays(5);
    LocalDateTime lastModifiedDate = LocalDateTime.now().plusDays(10);
    Map<String, Object> originalFields = createFormFields(formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(FormRPartB.class);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateFormrPartb();

    Optional<? extends AbstractFormR<?>> foundForm = partbRepository.findById(formId);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(FormRPartB.class));

    Status status = migratedForm.getStatus();
    List<StatusInfo> statusHistory = status.history();
    assertThat("Unexpected status history count.", statusHistory, hasSize(2));
    assertThat("Unexpected lastest status history.", statusHistory.get(1), is(status.current()));

    StatusInfo status1 = statusHistory.get(0);
    assertThat("Unexpected history state.", status1.state(), is(DRAFT));
    assertThat("Unexpected history revision.", status1.revision(), is(0));

    StatusInfo status2 = statusHistory.get(1);
    assertThat("Unexpected history state.", status2.state(), is(SUBMITTED));
    assertThat("Unexpected history revision.", status2.revision(), is(0));
  }

  @Test
  void shouldSnapshotSubmittedFormHistoryMetadata() {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(FormRPartB.class);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateFormrPartb();

    List<? extends AbstractAuditedForm<?>> historyList = partbSubmissionHistoryRepository
        .findByTraineeTisId(traineeId);

    assertThat("Expected form history count.", historyList, hasSize(1));

    AbstractAuditedForm<?> history = historyList.get(0);

    assertThat("Unexpected form ID.", history.getId(), not(formId));
    assertThat("Unexpected trainee ID.", history.getTraineeTisId(), is(traineeId));

    String expectedFormRef = "formr_partb_%s_001".formatted(traineeId);
    assertThat("Unexpected form reference.", history.getFormRef(), is(expectedFormRef));
    assertThat("Unexpected revision.", history.getRevision(), is(0));

    assertThat("Unexpected lifecycle state.", history.getLifecycleState(), is(SUBMITTED));
    assertThat("Unexpected created.", history.getCreated(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));
    assertThat("Unexpected last modified.", history.getLastModified(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));
  }

  @Test
  void shouldSnapshotSubmittedFormHistoryStatus() {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(FormRPartB.class);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateFormrPartb();

    List<? extends AbstractAuditedForm<?>> historyList = partbSubmissionHistoryRepository
        .findByTraineeTisId(traineeId);

    assertThat("Expected form history count.", historyList, hasSize(1));

    Status status = historyList.get(0).getStatus();
    assertThat("Unexpected submitted timestamp.", status.submitted(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));

    StatusInfo current = status.current();
    assertThat("Unexpected current state.", current.state(), is(SUBMITTED));
    assertThat("Unexpected current revision.", current.revision(), is(0));
    assertThat("Unexpected current modified by.", current.modifiedBy(), is(Person.builder()
        .name("forename_1 surname_1")
        .email("email_1")
        .role("TRAINEE")
        .build()
    ));
    assertThat("Unexpected current assigned admin.", current.assignedAdmin(), nullValue());
    assertThat("Unexpected current detail.", current.detail(), nullValue());

    // The status timestamp is based on S3 version history, we cannot assert exact values because it
    // is tied to the insertion into the localstack S3 instance and NOT any data we easily control.
    assertThat("Unexpected current timestamp.", current.timestamp(), notNullValue());

    List<StatusInfo> statusHistory = status.history();
    assertThat("Unexpected status history count.", statusHistory, hasSize(2));
    assertThat("Unexpected lastest status history.", statusHistory.get(1), is(current));

    StatusInfo status1 = statusHistory.get(0);
    assertThat("Unexpected history state.", status1.state(), is(DRAFT));
    assertThat("Unexpected history revision.", status1.revision(), is(0));
    assertThat("Unexpected history modified by.", status1.modifiedBy(), is(Person.builder()
        .name("forename_1 surname_1")
        .email("email_1")
        .role("TRAINEE")
        .build()
    ));
    assertThat("Unexpected history assigned admin.", status1.assignedAdmin(), nullValue());
    assertThat("Unexpected history detail.", status1.detail(), nullValue());
    assertThat("Unexpected history timestamp.", status1.timestamp(), notNullValue());
  }

  @Test
  void shouldSnapshotSubmittedFormHistoryContent() {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(FormRPartB.class);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateFormrPartb();

    List<? extends AbstractAuditedForm<?>> historyList = partbSubmissionHistoryRepository
        .findByTraineeTisId(traineeId);

    assertThat("Expected form history count.", historyList, hasSize(1));

    FormContent content = historyList.get(0).getContent();
    assertPartbContent((FormrPartbContent) content, originalFields);
  }

  /**
   * Create a content map for the given form class.
   *
   * @param formId           The ID of the form.
   * @param traineeId        The trainee ID.
   * @param lifecycleState   The lifecycle state of the form.
   * @param submissionDate   The date the form was submitted, may be null.
   * @param lastModifiedDate The date of the last modification to the form.
   * @param contentSuffix    A suffix to apply to content values.
   * @return The created form.
   */
  private Map<String, Object> createFormFields(UUID formId, String traineeId,
      LifecycleState lifecycleState, LocalDateTime submissionDate, LocalDateTime lastModifiedDate,
      String contentSuffix) {
    Map<String, Object> formFields = new HashMap<>();
    formFields.put(FIELD_FORM_ID, formId);
    formFields.put(FIELD_TRAINEE_ID, traineeId);
    formFields.put(FIELD_LIFECYCLE_STATE, lifecycleState.toString());
    formFields.put(FIELD_LAST_MODIFIED_DATE, lastModifiedDate);
    formFields.put(FIELD_FORM_CLASS, FormRPartB.class.getName());

    if (submissionDate != null) {
      formFields.put(FIELD_SUBMISSION_DATE, submissionDate);
    }

    Map<String, Object> contentFields = createPartbContent(contentSuffix);
    formFields.putAll(contentFields);

    return formFields;
  }

  /**
   * Create a content map for Form-R Part B.
   *
   * @param suffix The suffix to apply to string fields.
   * @return The created content map.
   */
  private Map<String, Object> createPartbContent(String suffix) {
    return Map.ofEntries(
        Map.entry("isArcp", true),
        Map.entry("programmeMembershipId", UUID.randomUUID()),
        Map.entry("forename", "forename_" + suffix),
        Map.entry("surname", "surname_" + suffix),
        Map.entry("gmcNumber", "gmcNumber_" + suffix),
        Map.entry("gdcNumber", "gdcNumber_" + suffix),
        Map.entry("publicHealthNumber", "publicHealthNumber_" + suffix),
        Map.entry("email", "email_" + suffix),
        Map.entry("localOfficeName", "localOfficeName_" + suffix),
        Map.entry("prevRevalBody", "prevRevalBody_" + suffix),
        Map.entry("prevRevalBodyOther", "prevRevalBodyOther_" + suffix),
        Map.entry("currRevalDate", LocalDate.now().minusDays(5)),
        Map.entry("prevRevalDate", LocalDate.now().minusDays(10)),
        Map.entry("programmeSpecialty", "programmeSpecialty_" + suffix),
        Map.entry("dualSpecialty", "dualSpecialty_" + suffix),
        Map.entry("work", List.of(
            Work.builder()
                .typeOfWork("typeOfWork_" + suffix)
                .startDate(LocalDate.now().minusDays(15))
                .endDate(LocalDate.now().minusDays(20))
                .trainingPost("trainingPost_" + suffix)
                .site("site_" + suffix)
                .siteLocation("siteLocation_" + suffix)
                .siteKnownAs("siteKnownAs_" + suffix)
                .build()
        )),
        Map.entry("sicknessAbsence", 1),
        Map.entry("parentalLeave", 2),
        Map.entry("careerBreaks", 3),
        Map.entry("paidLeave", 4),
        Map.entry("unauthorisedLeave", 5),
        Map.entry("otherLeave", 6),
        Map.entry("totalLeave", 21),
        Map.entry("isHonest", true),
        Map.entry("isHealthy", true),
        Map.entry("isWarned", true),
        Map.entry("isComplying", true),
        Map.entry("healthStatement", "healthStatement_" + suffix),
        Map.entry("havePreviousDeclarations", true),
        Map.entry("previousDeclarations", List.of(
            Declaration.builder()
                .declarationType("declarationType_" + suffix)
                .dateOfEntry(LocalDate.now().minusDays(25))
                .title("title_" + suffix)
                .locationOfEntry("locationOfEntry_" + suffix)
                .build()
        )),
        Map.entry("previousDeclarationSummary", "previousDeclarationSummary_" + suffix),
        Map.entry("haveCurrentDeclarations", true),
        Map.entry("currentDeclarations", List.of(
            Declaration.builder()
                .declarationType("declarationType_" + suffix)
                .dateOfEntry(LocalDate.now().minusDays(30))
                .title("title_" + suffix)
                .locationOfEntry("locationOfEntry_" + suffix)
                .build()
        )),
        Map.entry("currentDeclarationSummary", "currentDeclarationSummary_" + suffix),
        Map.entry("compliments", "compliments_" + suffix),
        Map.entry("haveCovidDeclarations", true),
        Map.entry("covidDeclaration", CovidDeclaration.builder()
            .selfRateForCovid("selfRateForCovid_" + suffix)
            .reasonOfSelfRate("reasonOfSelfRate_" + suffix)
            .otherInformationForPanel("otherInformationForPanel_" + suffix)
            .discussWithSupervisorChecked(true)
            .discussWithSomeoneChecked(true)
            .haveChangesToPlacement(true)
            .changeCircumstances("changeCircumstances_" + suffix)
            .changeCircumstanceOther("changeCircumstanceOther_" + suffix)
            .howPlacementAdjusted("howPlacementAdjusted_" + suffix)
            .educationSupervisorName("educationSupervisorName_" + suffix)
            .educationSupervisorEmail("educationSupervisorEmail_" + suffix)
            .build()
        ),
        Map.entry("haveCurrentUnresolvedDeclarations", true),
        Map.entry("havePreviousUnresolvedDeclarations", true)
    );
  }

  /**
   * Assert that all migrated Part B content fields match the original document content.
   *
   * @param content        The migrated content.
   * @param originalFields The original stored document fields.
   */
  private void assertPartbContent(FormrPartbContent content, Map<String, Object> originalFields) {
    Map<String, Object> contentFields = Map.ofEntries(
        Map.entry("programmeMembershipId", content.getProgrammeMembershipId()),
        Map.entry("isArcp", content.getIsArcp()),
        Map.entry("forename", content.getForename()),
        Map.entry("surname", content.getSurname()),
        Map.entry("gmcNumber", content.getGmcNumber()),
        Map.entry("gdcNumber", content.getGdcNumber()),
        Map.entry("publicHealthNumber", content.getPublicHealthNumber()),
        Map.entry("email", content.getEmail()),
        Map.entry("localOfficeName", content.getLocalOfficeName()),
        Map.entry("prevRevalBody", content.getPrevRevalBody()),
        Map.entry("prevRevalBodyOther", content.getPrevRevalBodyOther()),
        Map.entry("currRevalDate", content.getCurrRevalDate()),
        Map.entry("prevRevalDate", content.getPrevRevalDate()),
        Map.entry("programmeSpecialty", content.getProgrammeSpecialty()),
        Map.entry("dualSpecialty", content.getDualSpecialty()),
        Map.entry("work", content.getWork()),
        Map.entry("sicknessAbsence", content.getSicknessAbsence()),
        Map.entry("parentalLeave", content.getParentalLeave()),
        Map.entry("careerBreaks", content.getCareerBreaks()),
        Map.entry("paidLeave", content.getPaidLeave()),
        Map.entry("unauthorisedLeave", content.getUnauthorisedLeave()),
        Map.entry("otherLeave", content.getOtherLeave()),
        Map.entry("totalLeave", content.getTotalLeave()),
        Map.entry("isHonest", content.getIsHonest()),
        Map.entry("isHealthy", content.getIsHealthy()),
        Map.entry("isWarned", content.getIsWarned()),
        Map.entry("isComplying", content.getIsComplying()),
        Map.entry("healthStatement", content.getHealthStatement()),
        Map.entry("havePreviousDeclarations", content.getHavePreviousDeclarations()),
        Map.entry("previousDeclarations", content.getPreviousDeclarations()),
        Map.entry("previousDeclarationSummary", content.getPreviousDeclarationSummary()),
        Map.entry("haveCurrentDeclarations", content.getHaveCurrentDeclarations()),
        Map.entry("currentDeclarations", content.getCurrentDeclarations()),
        Map.entry("currentDeclarationSummary", content.getCurrentDeclarationSummary()),
        Map.entry("compliments", content.getCompliments()),
        Map.entry("haveCovidDeclarations", content.getHaveCovidDeclarations()),
        Map.entry("covidDeclaration", content.getCovidDeclaration()),
        Map.entry("haveCurrentUnresolvedDeclarations",
            content.getHaveCurrentUnresolvedDeclarations()),
        Map.entry("havePreviousUnresolvedDeclarations",
            content.getHavePreviousUnresolvedDeclarations())
    );

    assertThat("Unexpected content field count.", contentFields.keySet(), hasSize(39));
    contentFields.forEach((contentField, contentValue) ->
        assertThat("Unexpected %s.".formatted(contentField), contentValue,
            is(originalFields.get(contentField)))
    );
  }
}
