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
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.AbstractFormR;
import uk.nhs.hee.tis.trainee.forms.model.CovidDeclaration;
import uk.nhs.hee.tis.trainee.forms.model.Declaration;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.model.content.FormContent;
import uk.nhs.hee.tis.trainee.forms.model.content.FormrPartaContent;
import uk.nhs.hee.tis.trainee.forms.model.content.FormrPartbContent;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormrPartaSubmissionHistoryRepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormrPartbSubmissionHistoryRepository;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ConvertFormrToAuditedIntegrationTest {

  private static final String S3_BUCKET = UUID.randomUUID().toString();
  private static final String PART_A_BUCKET_KEY = "%s/forms/formr-a/%s.json";
  private static final String PART_B_BUCKET_KEY = "%s/forms/formr-b/%s.json";

  private static final String FIELD_FORM_ID = "_id";
  private static final String FIELD_TRAINEE_ID = "traineeTisId";
  private static final String FIELD_LIFECYCLE_STATE = "lifecycleState";
  private static final String FIELD_SUBMISSION_DATE = "submissionDate";
  private static final String FIELD_LAST_MODIFIED_DATE = "lastModifiedDate";
  private static final String FIELD_FORM_CLASS = "_class";

  private static final String METADATA_LIFECYCLE_STATE = "lifecyclestate";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Container
  private static final LocalStackContainer localstack = new LocalStackContainer(
      DockerImageNames.LOCALSTACK)
      .withServices(S3);

  @DynamicPropertySource
  private static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);

    registry.add("application.file-store.bucket", () -> S3_BUCKET);
    registry.add("spring.cloud.aws.s3.endpoint",
        () -> localstack.getEndpointOverride(S3).toString());
    registry.add("spring.cloud.aws.s3.path-style-access-enabled", () -> true);
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    // Create S3 bucket.
    String[] createBucketCmd = {
        "awslocal", "s3api", "create-bucket",
        "--bucket", S3_BUCKET,
        "--region", localstack.getRegion()
    };
    var bucketResult = localstack.execInContainer(createBucketCmd);
    assertThat("S3 bucket creation failed.", bucketResult.getExitCode(), is(0));

    // Enable versioning.
    String[] versionBucketCmd = {
        "awslocal", "--debug", "s3api", "put-bucket-versioning",
        "--bucket", S3_BUCKET,
        "--region", localstack.getRegion(),
        "--versioning-configuration", "Status=Enabled"
    };
    bucketResult = localstack.execInContainer(versionBucketCmd);
    assertThat("S3 bucket versioning failed.", bucketResult.getExitCode(), is(0));
  }

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private FormRPartARepository partaRepository;

  @Autowired
  private FormrPartaSubmissionHistoryRepository partaSubmissionHistoryRepository;

  @Autowired
  private FormRPartBRepository partbRepository;

  @Autowired
  private FormrPartbSubmissionHistoryRepository partbSubmissionHistoryRepository;

  @Autowired
  private S3Client s3Client;

  @Autowired
  private Environment env;

  @Autowired
  private ObjectMapper objectMapper;

  private ConvertFormrToAudited migration;

  @BeforeEach
  void setUp() {
    migration = new ConvertFormrToAudited(mongoTemplate, s3Client, env);
  }

  @AfterEach
  void tearDown() {
    mongoTemplate.remove(new Query(), FormRPartA.class);
    mongoTemplate.remove(new Query(), FormRPartB.class);
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateFormMetadata(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields), collectionName);

    uploadForm(originalFields, formClass);

    migration.migrateCollections();

    Optional<? extends AbstractFormR<?>> foundForm = getMigratedForm(formId, formClass);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(formClass));

    assertThat("Unexpected form ID.", migratedForm.getId(), is(formId));
    assertThat("Unexpected trainee ID.", migratedForm.getTraineeTisId(), is(traineeId));

    String expectedFormRef = "formr_part%s_%s_001".formatted(
        formClass == FormRPartA.class ? "a" : "b", traineeId);
    assertThat("Unexpected form reference.", migratedForm.getFormRef(), is(expectedFormRef));
    assertThat("Unexpected revision.", migratedForm.getRevision(), is(0));

    assertThat("Unexpected lifecycle state.", migratedForm.getLifecycleState(), is(SUBMITTED));
    assertThat("Unexpected created.", migratedForm.getCreated(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));
    assertThat("Unexpected last modified.", migratedForm.getLastModified(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateFormStatus(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields), collectionName);

    uploadForm(originalFields, formClass);

    migration.migrateCollections();

    Optional<? extends AbstractFormR<?>> foundForm = getMigratedForm(formId, formClass);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(formClass));

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

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateFormContent(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields), collectionName);

    uploadForm(originalFields, formClass);

    migration.migrateCollections();

    Optional<? extends AbstractFormR<?>> foundForm = getMigratedForm(formId, formClass);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(formClass));

    FormContent content = migratedForm.getContent();

    if (formClass == FormRPartA.class) {
      assertPartaContent((FormrPartaContent) content, originalFields);
    } else {
      assertPartbContent((FormrPartbContent) content, originalFields);
    }
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateDraftHistory(Class<? extends AbstractFormR<?>> formClass) {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formClass, formId, traineeId, DRAFT, null,
        lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateCollections();

    Optional<? extends AbstractFormR<?>> foundForm = getMigratedForm(formId, formClass);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(formClass));

    Status status = migratedForm.getStatus();
    List<StatusInfo> statusHistory = status.history();
    assertThat("Unexpected status history count.", statusHistory, hasSize(1));
    assertThat("Unexpected lastest status history.", statusHistory.get(0), is(status.current()));

    StatusInfo status1 = statusHistory.get(0);
    assertThat("Unexpected history state.", status1.state(), is(DRAFT));
    assertThat("Unexpected history revision.", status1.revision(), is(0));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateSubmittedHistory(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().plusDays(5);
    LocalDateTime lastModifiedDate = LocalDateTime.now().plusDays(10);
    Map<String, Object> originalFields = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    uploadForm(originalFields, formClass);

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields), collectionName);

    migration.migrateCollections();

    Optional<? extends AbstractFormR<?>> foundForm = getMigratedForm(formId, formClass);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(formClass));

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

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateUnsubmittedHistory(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();

    LocalDateTime submissionDate1 = LocalDateTime.now().plusDays(5);
    LocalDateTime lastModifiedDate1 = LocalDateTime.now().plusDays(10);
    Map<String, Object> originalFields1 = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate1, lastModifiedDate1, "1");

    uploadForm(originalFields1, formClass);

    LocalDateTime lastModifiedDate2 = LocalDateTime.now().plusDays(15);
    Map<String, Object> originalFields2 = createFormFields(formClass, formId, traineeId,
        UNSUBMITTED,
        submissionDate1, lastModifiedDate2, "2");

    uploadForm(originalFields2, formClass);

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields2), collectionName);

    migration.migrateCollections();

    Optional<? extends AbstractFormR<?>> foundForm = getMigratedForm(formId, formClass);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(formClass));

    Status status = migratedForm.getStatus();
    List<StatusInfo> statusHistory = status.history();
    assertThat("Unexpected status history count.", statusHistory, hasSize(3));
    assertThat("Unexpected lastest status history.", statusHistory.get(2), is(status.current()));

    StatusInfo status1 = statusHistory.get(0);
    assertThat("Unexpected history state.", status1.state(), is(DRAFT));
    assertThat("Unexpected history revision.", status1.revision(), is(0));

    StatusInfo status2 = statusHistory.get(1);
    assertThat("Unexpected history state.", status2.state(), is(SUBMITTED));
    assertThat("Unexpected history revision.", status2.revision(), is(0));

    StatusInfo status3 = statusHistory.get(2);
    assertThat("Unexpected history state.", status3.state(), is(UNSUBMITTED));
    assertThat("Unexpected history revision.", status3.revision(), is(1));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateResubmittedHistory(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();

    LocalDateTime submissionDate1 = LocalDateTime.now().plusDays(5);
    LocalDateTime lastModifiedDate1 = LocalDateTime.now().plusDays(10);
    Map<String, Object> originalFields1 = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate1, lastModifiedDate1, "1");

    uploadForm(originalFields1, formClass);

    LocalDateTime lastModifiedDate2 = LocalDateTime.now().plusDays(15);
    Map<String, Object> originalFields2 = createFormFields(formClass, formId, traineeId,
        UNSUBMITTED,
        submissionDate1, lastModifiedDate2, "2");

    uploadForm(originalFields2, formClass);

    LocalDateTime submissionDate3 = LocalDateTime.now().plusDays(20);
    LocalDateTime lastModifiedDate3 = LocalDateTime.now().plusDays(25);
    Map<String, Object> originalFields3 = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate3, lastModifiedDate3, "3");

    uploadForm(originalFields3, formClass);

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields3), collectionName);

    migration.migrateCollections();

    Optional<? extends AbstractFormR<?>> foundForm = getMigratedForm(formId, formClass);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(formClass));

    Status status = migratedForm.getStatus();
    List<StatusInfo> statusHistory = status.history();
    assertThat("Unexpected status history count.", statusHistory, hasSize(4));
    assertThat("Unexpected lastest status history.", statusHistory.get(3), is(status.current()));

    StatusInfo status1 = statusHistory.get(0);
    assertThat("Unexpected history state.", status1.state(), is(DRAFT));
    assertThat("Unexpected history revision.", status1.revision(), is(0));

    StatusInfo status2 = statusHistory.get(1);
    assertThat("Unexpected history state.", status2.state(), is(SUBMITTED));
    assertThat("Unexpected history revision.", status2.revision(), is(0));

    StatusInfo status3 = statusHistory.get(2);
    assertThat("Unexpected history state.", status3.state(), is(UNSUBMITTED));
    assertThat("Unexpected history revision.", status3.revision(), is(1));

    StatusInfo status4 = statusHistory.get(3);
    assertThat("Unexpected history state.", status4.state(), is(SUBMITTED));
    assertThat("Unexpected history revision.", status4.revision(), is(1));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldMigrateDeletedHistory(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();

    LocalDateTime submissionDate1 = LocalDateTime.now().plusDays(5);
    LocalDateTime lastModifiedDate1 = LocalDateTime.now().plusDays(10);
    Map<String, Object> originalFields1 = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate1, lastModifiedDate1, "1");

    uploadForm(originalFields1, formClass);

    LocalDateTime lastModifiedDate2 = LocalDateTime.now().plusDays(15);
    Map<String, Object> originalFields2 = createFormFields(formClass, formId, traineeId,
        UNSUBMITTED,
        submissionDate1, lastModifiedDate2, "2");

    uploadForm(originalFields2, formClass);

    LocalDateTime submissionDate3 = LocalDateTime.now().plusDays(20);
    LocalDateTime lastModifiedDate3 = LocalDateTime.now().plusDays(25);
    Map<String, Object> originalFields3 = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate3, lastModifiedDate3, "3");

    uploadForm(originalFields3, formClass);

    LocalDateTime lastModifiedDate4 = LocalDateTime.now().plusDays(30);
    Map<String, Object> originalFields4 = createFormFields(formClass, formId, traineeId, DELETED,
        submissionDate3, lastModifiedDate4, "4");

    uploadForm(originalFields4, formClass);

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields4), collectionName);

    migration.migrateCollections();

    Optional<? extends AbstractFormR<?>> foundForm = getMigratedForm(formId, formClass);
    assertThat("Expected form not found.", foundForm.isPresent(), is(true));

    AbstractFormR<?> migratedForm = foundForm.get();
    assertThat("Unexpected form type.", migratedForm, instanceOf(formClass));

    Status status = migratedForm.getStatus();
    List<StatusInfo> statusHistory = status.history();
    assertThat("Unexpected status history count.", statusHistory, hasSize(5));
    assertThat("Unexpected lastest status history.", statusHistory.get(4), is(status.current()));

    StatusInfo status1 = statusHistory.get(0);
    assertThat("Unexpected history state.", status1.state(), is(DRAFT));
    assertThat("Unexpected history revision.", status1.revision(), is(0));

    StatusInfo status2 = statusHistory.get(1);
    assertThat("Unexpected history state.", status2.state(), is(SUBMITTED));
    assertThat("Unexpected history revision.", status2.revision(), is(0));

    StatusInfo status3 = statusHistory.get(2);
    assertThat("Unexpected history state.", status3.state(), is(UNSUBMITTED));
    assertThat("Unexpected history revision.", status3.revision(), is(1));

    StatusInfo status4 = statusHistory.get(3);
    assertThat("Unexpected history state.", status4.state(), is(SUBMITTED));
    assertThat("Unexpected history revision.", status4.revision(), is(1));

    StatusInfo status5 = statusHistory.get(4);
    assertThat("Unexpected history state.", status5.state(), is(DELETED));
    assertThat("Unexpected history revision.", status5.revision(), is(1));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSnapshotSubmittedFormHistoryMetadata(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields), collectionName);

    uploadForm(originalFields, formClass);

    migration.migrateCollections();

    List<? extends AbstractAuditedForm<?>> historyList = getMigratedFormSubmissionHistory(
        traineeId, formClass);

    assertThat("Expected form history count.", historyList, hasSize(1));

    AbstractAuditedForm<?> history = historyList.get(0);

    assertThat("Unexpected form ID.", history.getId(), not(formId));
    assertThat("Unexpected trainee ID.", history.getTraineeTisId(), is(traineeId));

    String expectedFormRef = "formr_part%s_%s_001".formatted(
        formClass == FormRPartA.class ? "a" : "b", traineeId);
    assertThat("Unexpected form reference.", history.getFormRef(), is(expectedFormRef));
    assertThat("Unexpected revision.", history.getRevision(), is(0));

    assertThat("Unexpected lifecycle state.", history.getLifecycleState(), is(SUBMITTED));
    assertThat("Unexpected created.", history.getCreated(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));
    assertThat("Unexpected last modified.", history.getLastModified(),
        is(submissionDate.toInstant(UTC).truncatedTo(MILLIS)));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSnapshotSubmittedFormHistoryStatus(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields), collectionName);

    uploadForm(originalFields, formClass);

    migration.migrateCollections();

    List<? extends AbstractAuditedForm<?>> historyList = getMigratedFormSubmissionHistory(
        traineeId, formClass);

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

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSnapshotSubmittedFormHistoryContent(Class<? extends AbstractFormR<?>> formClass)
      throws JsonProcessingException {
    UUID formId = UUID.randomUUID();
    String traineeId = UUID.randomUUID().toString();
    LocalDateTime submissionDate = LocalDateTime.now().minusDays(10);
    LocalDateTime lastModifiedDate = LocalDateTime.now().minusDays(5);
    Map<String, Object> originalFields = createFormFields(formClass, formId, traineeId, SUBMITTED,
        submissionDate, lastModifiedDate, "1");

    String collectionName = mongoTemplate.getCollectionName(formClass);
    mongoTemplate.save(new Document(originalFields), collectionName);

    uploadForm(originalFields, formClass);

    migration.migrateCollections();

    List<? extends AbstractAuditedForm<?>> historyList = getMigratedFormSubmissionHistory(
        traineeId, formClass);

    assertThat("Expected form history count.", historyList, hasSize(1));

    FormContent content = historyList.get(0).getContent();

    if (formClass == FormRPartA.class) {
      assertPartaContent((FormrPartaContent) content, originalFields);
    } else {
      assertPartbContent((FormrPartbContent) content, originalFields);
    }
  }

  /**
   * Get the migrated form from the repository, if it exists.
   *
   * @param formId    The ID of the migrated form.
   * @param formClass The class of the migrated form.
   * @return The migrated form, if found.
   */
  private Optional<? extends AbstractFormR<?>> getMigratedForm(UUID formId,
      Class<? extends AbstractFormR<?>> formClass) {
    if (formClass == FormRPartA.class) {
      return partaRepository.findById(formId);
    } else {
      return partbRepository.findById(formId);
    }
  }

  /**
   * Get the migrated form submission history from the repository, if any exist.
   *
   * @param traineeId The ID of the migrated form.
   * @param formClass The class of the migrated form.
   * @return The migrated form submission history, or empty if not found.
   */
  private List<? extends AbstractAuditedForm<?>> getMigratedFormSubmissionHistory(String traineeId,
      Class<? extends AbstractFormR<?>> formClass) {
    if (formClass == FormRPartA.class) {
      return partaSubmissionHistoryRepository.findByTraineeTisId(traineeId);
    } else {
      return partbSubmissionHistoryRepository.findByTraineeTisId(traineeId);
    }
  }

  /**
   * Create a content map for the given form class.
   *
   * @param formClass        The class of the form to create content for.
   * @param formId           The ID of the form.
   * @param traineeId        The trainee ID.
   * @param lifecycleState   The lifecycle state of the form.
   * @param submissionDate   The date the form was submitted, may be null.
   * @param lastModifiedDate The date of the last modification to the form.
   * @param contentSuffix    A suffix to apply to content values.
   * @return The created form.
   */
  private Map<String, Object> createFormFields(Class<? extends AbstractFormR<?>> formClass,
      UUID formId, String traineeId, LifecycleState lifecycleState, LocalDateTime submissionDate,
      LocalDateTime lastModifiedDate, String contentSuffix) {
    Map<String, Object> formFields = new HashMap<>();
    formFields.put(FIELD_FORM_ID, formId);
    formFields.put(FIELD_TRAINEE_ID, traineeId);
    formFields.put(FIELD_LIFECYCLE_STATE, lifecycleState.toString());
    formFields.put(FIELD_LAST_MODIFIED_DATE, lastModifiedDate);
    formFields.put(FIELD_FORM_CLASS, formClass.getName());

    if (submissionDate != null) {
      formFields.put(FIELD_SUBMISSION_DATE, submissionDate);
    }

    Map<String, Object> contentFields =
        formClass == FormRPartA.class ? createPartaContent(contentSuffix)
            : createPartbContent(contentSuffix);
    formFields.putAll(contentFields);

    return formFields;
  }

  /**
   * Create a content map for Form-R Part A.
   *
   * @param suffix The suffix to apply to string fields.
   * @return The created content map.
   */
  private Map<String, Object> createPartaContent(String suffix) {
    return Map.ofEntries(
        Map.entry("isArcp", true),
        Map.entry("programmeMembershipId", UUID.randomUUID()),
        Map.entry("forename", "forename_" + suffix),
        Map.entry("surname", "surname_" + suffix),
        Map.entry("gmcNumber", "gmcNumber_" + suffix),
        Map.entry("gdcNumber", "gdcNumber_" + suffix),
        Map.entry("publicHealthNumber", "publicHealthNumber_" + suffix),
        Map.entry("localOfficeName", "localOfficeName_" + suffix),
        Map.entry("dateOfBirth", LocalDate.now().minusDays(30)),
        Map.entry("gender", "gender_" + suffix),
        Map.entry("immigrationStatus", "immigrationStatus_" + suffix),
        Map.entry("qualification", "qualification_" + suffix),
        Map.entry("dateAttained", LocalDate.now().minusDays(25)),
        Map.entry("medicalSchool", "medicalSchool_" + suffix),
        Map.entry("address1", "address1_" + suffix),
        Map.entry("address2", "address2_" + suffix),
        Map.entry("address3", "address3_" + suffix),
        Map.entry("address4", "address4_" + suffix),
        Map.entry("postCode", "postCode_" + suffix),
        Map.entry("telephoneNumber", "telephoneNumber_" + suffix),
        Map.entry("mobileNumber", "mobileNumber_" + suffix),
        Map.entry("email", "email_" + suffix),
        Map.entry("declarationType", "declarationType_" + suffix),
        Map.entry("isLeadingToCct", true),
        Map.entry("programmeSpecialty", "programmeSpecialty_" + suffix),
        Map.entry("cctSpecialty1", "cctSpecialty1_" + suffix),
        Map.entry("cctSpecialty2", "cctSpecialty2_" + suffix),
        Map.entry("college", "college_" + suffix),
        Map.entry("completionDate", LocalDate.now().minusDays(20)),
        Map.entry("trainingGrade", "trainingGrade_" + suffix),
        Map.entry("startDate", LocalDate.now().minusDays(15)),
        Map.entry("programmeMembershipType", "programmeMembershipType_" + suffix),
        Map.entry("wholeTimeEquivalent", "wholeTimeEquivalent_" + suffix),
        Map.entry("otherImmigrationStatus", "otherImmigrationStatus_" + suffix)
    );
  }

  /**
   * Assert that all migrated Part A content fields match the original document content.
   *
   * @param content        The migrated content.
   * @param originalFields The original stored document fields.
   */
  private void assertPartaContent(FormrPartaContent content, Map<String, Object> originalFields) {
    Map<String, Object> contentFields = Map.ofEntries(
        Map.entry("programmeMembershipId", content.getProgrammeMembershipId()),
        Map.entry("isArcp", content.getIsArcp()),
        Map.entry("forename", content.getForename()),
        Map.entry("surname", content.getSurname()),
        Map.entry("gmcNumber", content.getGmcNumber()),
        Map.entry("gdcNumber", content.getGdcNumber()),
        Map.entry("publicHealthNumber", content.getPublicHealthNumber()),
        Map.entry("localOfficeName", content.getLocalOfficeName()),
        Map.entry("dateOfBirth", content.getDateOfBirth()),
        Map.entry("gender", content.getGender()),
        Map.entry("immigrationStatus", content.getImmigrationStatus()),
        Map.entry("qualification", content.getQualification()),
        Map.entry("dateAttained", content.getDateAttained()),
        Map.entry("medicalSchool", content.getMedicalSchool()),
        Map.entry("address1", content.getAddress1()),
        Map.entry("address2", content.getAddress2()),
        Map.entry("address3", content.getAddress3()),
        Map.entry("address4", content.getAddress4()),
        Map.entry("postCode", content.getPostCode()),
        Map.entry("telephoneNumber", content.getTelephoneNumber()),
        Map.entry("mobileNumber", content.getMobileNumber()),
        Map.entry("email", content.getEmail()),
        Map.entry("declarationType", content.getDeclarationType()),
        Map.entry("isLeadingToCct", content.getIsLeadingToCct()),
        Map.entry("programmeSpecialty", content.getProgrammeSpecialty()),
        Map.entry("cctSpecialty1", content.getCctSpecialty1()),
        Map.entry("cctSpecialty2", content.getCctSpecialty2()),
        Map.entry("college", content.getCollege()),
        Map.entry("completionDate", content.getCompletionDate()),
        Map.entry("trainingGrade", content.getTrainingGrade()),
        Map.entry("startDate", content.getStartDate()),
        Map.entry("programmeMembershipType", content.getProgrammeMembershipType()),
        Map.entry("wholeTimeEquivalent", content.getWholeTimeEquivalent()),
        Map.entry("otherImmigrationStatus", content.getOtherImmigrationStatus())
    );

    assertThat("Unexpected content field count.", contentFields.keySet(), hasSize(34));
    contentFields.forEach((contentField, contentValue) ->
        assertThat("Unexpected %s.".formatted(contentField), contentValue,
            is(originalFields.get(contentField)))
    );
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

  /**
   * Upload the given form document to S3 with appropriate metadata for migration.
   *
   * @param formFields The form fields to upload.
   * @param formClass  The class of the form to upload.
   * @throws JsonProcessingException If the document was not valid JSON.
   */
  private void uploadForm(Map<String, Object> formFields,
      Class<? extends AbstractFormR<?>> formClass) throws JsonProcessingException {
    UUID formId = (UUID) formFields.get(FIELD_FORM_ID);
    String traineeId = (String) formFields.get(FIELD_TRAINEE_ID);
    String lifecycleState = (String) formFields.get(FIELD_LIFECYCLE_STATE);

    String keyTemplate = formClass == FormRPartA.class ? PART_A_BUCKET_KEY : PART_B_BUCKET_KEY;

    s3Client.putObject(b -> b.bucket(S3_BUCKET)
            .key(keyTemplate.formatted(traineeId, formId))
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .metadata(Map.of(
                METADATA_LIFECYCLE_STATE, lifecycleState
            ))
            .build(),
        RequestBody.fromBytes(objectMapper.writeValueAsBytes(formFields))
    );
  }

  /**
   * Assert that the given modifiedBy user matches the expected values.
   *
   * @param lifecycleState The lifecycle state of the form.
   * @param modifiedBy     The modifiedBy user to check.
   * @param suffix         The suffix used to generate the expected values.
   */
  private void assertModifiedBy(LifecycleState lifecycleState, Person modifiedBy, String suffix) {
    if (lifecycleState == DRAFT || lifecycleState == SUBMITTED) {
      assertThat("Unexpected modifiedBy name.", modifiedBy.name(),
          is("Forename_" + suffix + " Surname_" + suffix));
      assertThat("Unexpected modifiedBy email.", modifiedBy.email(), is("email_" + suffix));
      assertThat("Unexpected modifiedBy role.", modifiedBy.role(), is("TRAINEE"));
    } else {
      assertThat("Unexpected modifiedBy name.", modifiedBy.name(), is("Unknown Admin"));
      assertThat("Unexpected modifiedBy email.", modifiedBy.email(), is("no-reply@tis.nhs.uk"));
      assertThat("Unexpected modifiedBy role.", modifiedBy.role(), is("ADMIN"));
    }
  }
}
