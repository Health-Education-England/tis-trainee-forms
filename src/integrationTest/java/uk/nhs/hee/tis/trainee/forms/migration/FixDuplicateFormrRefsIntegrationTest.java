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

import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractFormR;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartaSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.FormrPartbSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.content.FormrPartaContent;
import uk.nhs.hee.tis.trainee.forms.model.content.FormrPartbContent;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FixDuplicateFormrRefsIntegrationTest {

  private static final String UPDATED_TOPIC = "integration-formr-updated-topic";
  private static final String FILE_TOPIC = "integration-formr-file-topic";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @DynamicPropertySource
  private static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("application.aws.sns.formr-updated", () -> UPDATED_TOPIC);
    registry.add("application.aws.sns.formr-file-event", () -> FILE_TOPIC);
  }

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private FormRPartAService partaService;

  @Autowired
  private FormRPartBService partbService;

  @Value("${application.aws.sns.formr-updated}")
  private String formrUpdatedTopic;

  @Value("${application.aws.sns.formr-file-event}")
  private String formrFileTopic;

  @MockitoBean
  private SnsClient snsClient;

  private FixDuplicateFormrRefs migration;

  @BeforeEach
  void setUp() {
    migration = new FixDuplicateFormrRefs(mongoTemplate, partaService, partbService);
  }

  @AfterEach
  void tearDown() {
    mongoTemplate.remove(new Query(), FormRPartA.class);
    mongoTemplate.remove(new Query(), FormRPartB.class);
    mongoTemplate.remove(new Query(), FormrPartaSubmissionHistory.class);
    mongoTemplate.remove(new Query(), FormrPartbSubmissionHistory.class);
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldFixDuplicateRefsUsingCreatedDateOrdering(Class<? extends AbstractFormR<?>> formClass) {
    String traineeWithDuplicates = UUID.randomUUID().toString();
    String traineeWithoutDuplicates = UUID.randomUUID().toString();

    UUID latestId = UUID.randomUUID();
    UUID middleId = UUID.randomUUID();
    UUID oldestId = UUID.randomUUID();

    String collectionName = mongoTemplate.getCollectionName(formClass);
    String baseRef = getFormRefPrefix(formClass, traineeWithDuplicates);

    mongoTemplate.save(createForm(formClass, latestId, traineeWithDuplicates, baseRef + "001",
        Instant.now().minus(1, DAYS)), collectionName);
    mongoTemplate.save(createForm(formClass, middleId, traineeWithDuplicates, baseRef + "001",
        Instant.now().minus(2, DAYS)), collectionName);
    mongoTemplate.save(createForm(formClass, oldestId, traineeWithDuplicates, baseRef + "001",
        Instant.now().minus(3, DAYS)), collectionName);

    // A non-duplicated trainee should not be changed by the migration.
    String uniqueBaseRef = getFormRefPrefix(formClass, traineeWithoutDuplicates);
    UUID uniqueId1 = UUID.randomUUID();
    UUID uniqueId2 = UUID.randomUUID();
    mongoTemplate.save(
        createForm(formClass, uniqueId1, traineeWithoutDuplicates, uniqueBaseRef + "002",
            Instant.now().minus(4, DAYS)), collectionName);
    mongoTemplate.save(
        createForm(formClass, uniqueId2, traineeWithoutDuplicates, uniqueBaseRef + "001",
            Instant.now().minus(5, DAYS)), collectionName);

    migration.migrateCollections();

    assertThat("Unexpected latest form ref.",
        findFormRef(collectionName, latestId), is(baseRef + "003"));
    assertThat("Unexpected middle form ref.",
        findFormRef(collectionName, middleId), is(baseRef + "002"));
    assertThat("Unexpected oldest form ref.",
        findFormRef(collectionName, oldestId), is(baseRef + "001"));

    assertThat("Unexpected non-duplicate form ref.",
        findFormRef(collectionName, uniqueId1), is(uniqueBaseRef + "002"));
    assertThat("Unexpected non-duplicate form ref.",
        findFormRef(collectionName, uniqueId2), is(uniqueBaseRef + "001"));

    String historyCollection = mongoTemplate.getCollectionName(getHistoryClass(formClass));
    List<Document> snapshots = mongoTemplate.find(
        Query.query(Criteria.where("traineeTisId").is(traineeWithDuplicates)),
        Document.class, historyCollection);
    assertThat("Unexpected history snapshot count.", snapshots, hasSize(2));
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldSkipUpdatesWhenGeneratedFormRefAlreadyExistsInHistory(
      Class<? extends AbstractFormR<?>> formClass) {
    String traineeId = UUID.randomUUID().toString();
    String collectionName = mongoTemplate.getCollectionName(formClass);
    String historyCollection = mongoTemplate.getCollectionName(getHistoryClass(formClass));

    UUID latestId = UUID.randomUUID();
    UUID olderId = UUID.randomUUID();
    String baseRef = getFormRefPrefix(formClass, traineeId);

    mongoTemplate.save(createForm(formClass, latestId, traineeId, baseRef + "001",
        Instant.now().minus(1, DAYS)), collectionName);
    mongoTemplate.save(createForm(formClass, olderId, traineeId, baseRef + "001",
        Instant.now().minus(2, DAYS)), collectionName);

    // For two forms the generated latest ref would be ..._002, which this history row blocks.
    mongoTemplate.save(new Document("formRef", baseRef + "002"), historyCollection);

    migration.migrateCollections();

    assertThat("Unexpected latest form ref.",
        findFormRef(collectionName, latestId), is(baseRef + "001"));
    assertThat("Unexpected older form ref.",
        findFormRef(collectionName, olderId), is(baseRef + "001"));

    verifyNoInteractions(snsClient);
  }

  @ParameterizedTest
  @ValueSource(classes = {FormRPartA.class, FormRPartB.class})
  void shouldPublishToUpdatedTopicWhenRefChanges(Class<? extends AbstractFormR<?>> formClass) {
    String traineeId = UUID.randomUUID().toString();
    String collectionName = mongoTemplate.getCollectionName(formClass);

    UUID latestId = UUID.randomUUID();
    String baseRef = getFormRefPrefix(formClass, traineeId);

    mongoTemplate.save(createForm(formClass, latestId, traineeId, baseRef + "001",
        Instant.now().minus(1, DAYS)), collectionName);
    mongoTemplate.save(createForm(formClass, UUID.randomUUID(), traineeId, baseRef + "001",
        Instant.now().minus(2, DAYS)), collectionName);

    migration.migrateCollections();

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient, times(2)).publish(requestCaptor.capture());

    List<PublishRequest> requests = requestCaptor.getAllValues();
    PublishRequest updatedRequest = requests.stream()
        .filter(request -> formrUpdatedTopic.equals(request.topicArn()))
        .findFirst()
        .orElse(null);
    assertThat("Expected updated topic publish request was not sent.", updatedRequest,
        notNullValue());

    String expectedFormType = formClass == FormRPartA.class ? "formr-a" : "formr-b";
    assertThat("Unexpected updated event formType attribute.",
        updatedRequest.messageAttributes().get("formType").stringValue(), is(expectedFormType));
    assertThat("Unexpected updated event message group ID.",
        updatedRequest.messageGroupId(), is(latestId.toString()));

    PublishRequest fileRequest = requests.stream()
        .filter(request -> formrFileTopic.equals(request.topicArn()))
        .findFirst()
        .orElse(null);
    assertThat("Expected file topic publish request was not sent.", fileRequest, notNullValue());
    assertThat("Unexpected file event_type attribute.",
        fileRequest.messageAttributes().get("event_type").stringValue(), is("FORM_R"));
  }

  /**
   * Find a form reference by document ID from a given collection.
   *
   * @param collectionName The Mongo collection to query.
   * @param id             The form document ID.
   * @return The form reference if found, otherwise {@code null}.
   */
  private String findFormRef(String collectionName, UUID id) {
    Document document = mongoTemplate.findById(id, Document.class, collectionName);
    return document == null ? null : document.getString("formRef");
  }

  /**
   * Resolve the submission history class for a Form-R type.
   *
   * @param formClass The Form-R class.
   * @return The matching submission history class.
   */
  private Class<? extends FormSubmissionHistory> getHistoryClass(
      Class<? extends AbstractFormR<?>> formClass) {
    return formClass == FormRPartA.class ? FormrPartaSubmissionHistory.class
        : FormrPartbSubmissionHistory.class;
  }

  /**
   * Build a form reference prefix for a trainee and Form-R type.
   *
   * @param formClass The Form-R class.
   * @param traineeId The trainee ID.
   * @return The form reference prefix.
   */
  private String getFormRefPrefix(Class<? extends AbstractFormR<?>> formClass, String traineeId) {
    String formType = formClass == FormRPartA.class ? "parta" : "partb";
    return "formr_" + formType + "_" + traineeId + "_";
  }

  /**
   * Create a minimal submitted Form-R instance for migration testing.
   *
   * @param formClass The Form-R class to instantiate.
   * @param id        The form ID.
   * @param traineeId The trainee ID.
   * @param formRef   The form reference.
   * @param created   The created/last-modified timestamp.
   * @return The populated Form-R instance.
   */
  private AbstractFormR<?> createForm(Class<? extends AbstractFormR<?>> formClass, UUID id,
      String traineeId, String formRef, Instant created) {
    AbstractFormR<?> form;

    if (formClass == FormRPartA.class) {
      FormRPartA parta = new FormRPartA();
      parta.setContent(FormrPartaContent.builder()
          .forename("Forename")
          .surname("Surname")
          .email("formr-a@example.com")
          .build());
      form = parta;
    } else {
      FormRPartB partb = new FormRPartB();
      partb.setContent(FormrPartbContent.builder()
          .forename("Forename")
          .surname("Surname")
          .email("formr-b@example.com")
          .build());
      form = partb;
    }

    form.setId(id);
    form.setTraineeTisId(traineeId);
    form.setFormRef(formRef);
    form.setRevision(0);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setCreated(created);
    form.setLastModified(created);
    return form;
  }
}
