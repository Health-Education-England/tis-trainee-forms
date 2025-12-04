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

package uk.nhs.hee.tis.trainee.forms.event;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProfileMoveListenerIntegrationTest {
  private static final String FROM_TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TO_TRAINEE_ID = UUID.randomUUID().toString();

  private static final String S3_BUCKET = "test-filestore-bucket";
  private static final String PROFILE_MOVE_QUEUE = UUID.randomUUID().toString();

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Container
  private static final LocalStackContainer localstack = new LocalStackContainer(
      DockerImageNames.LOCALSTACK)
      .withServices(SQS, S3)
      .withExposedPorts(4566);

  @DynamicPropertySource
  private static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);

    registry.add("application.aws.sqs.profile-move", () -> PROFILE_MOVE_QUEUE);
    registry.add("spring.cloud.aws.sqs.endpoint",
        () -> localstack.getEndpointOverride(SQS).toString());
    registry.add("spring.cloud.aws.sqs.enabled", () -> true);

    registry.add("application.filestore.bucket", () -> S3_BUCKET);
    registry.add("spring.cloud.aws.s3.endpoint",
        () -> localstack.getEndpointOverride(S3).toString());
    registry.add("spring.cloud.aws.s3.path-style-access-enabled", () -> true);
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    localstack.execInContainer("awslocal", "sqs", "create-queue", "--queue-name",
        PROFILE_MOVE_QUEUE);

    // Create S3 bucket
    String[] createBucketCmd = {
        "awslocal", "s3api", "create-bucket",
        "--bucket", S3_BUCKET,
        "--region", localstack.getRegion()
    };
    var bucketResult = localstack.execInContainer(createBucketCmd);
    assertThat("S3 bucket creation failed", bucketResult.getExitCode(), is(0));
  }

  @Autowired
  private SqsTemplate sqsTemplate;

  @Autowired
  private MongoTemplate template;

  @MockBean
  private JwtDecoder jwtDecoder;

  @AfterEach
  void cleanUp() {
    template.findAllAndRemove(new Query(), FormRPartA.class);
    template.findAllAndRemove(new Query(), FormRPartB.class);
    template.findAllAndRemove(new Query(), LtftForm.class);
    template.findAllAndRemove(new Query(), LtftSubmissionHistory.class);
  }

  @Test
  void shouldMoveAllRelevantFormRPartAsWhenProfileMove() throws JsonProcessingException {
    //forms to move
    UUID id1 = UUID.randomUUID();
    FormRPartA formPartA = new FormRPartA();
    formPartA.setId(id1);
    formPartA.setCollege("another college");
    formPartA.setLifecycleState(DRAFT);
    formPartA.setTraineeTisId(FROM_TRAINEE_ID);
    template.insert(formPartA);

    UUID id2 = UUID.randomUUID();
    FormRPartA formPartA2 = new FormRPartA();
    formPartA2.setId(id2);
    formPartA2.setCollege("some college");
    formPartA2.setLifecycleState(DRAFT);
    formPartA2.setTraineeTisId(FROM_TRAINEE_ID);
    template.insert(formPartA2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeTisId").is(TO_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<FormRPartA> movedFormRpartAs = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<FormRPartA> foundA = template.find(query, FormRPartA.class);
          assertThat("Unexpected moved FormR PartA count.", foundA.size(),
              is(2));
          movedFormRpartAs.addAll(foundA);
        });

    //check that nothing apart from trainee id has changed
    FormRPartA movedForm1 = movedFormRpartAs.stream()
        .filter(a -> a.getId().equals(id1))
        .findFirst()
        .orElseThrow();
    movedForm1.setTraineeTisId(FROM_TRAINEE_ID);
    assertThat("Unexpected moved FormR PartA data.", movedForm1, is(formPartA));

    FormRPartA movedForm2 = movedFormRpartAs.stream()
        .filter(a -> a.getId().equals(id2))
        .findFirst()
        .orElseThrow();
    movedForm2.setTraineeTisId(FROM_TRAINEE_ID);
    assertThat("Unexpected moved FormR PartA data.", movedForm2, is(formPartA2));
  }

  @Test
  void shouldMoveAllRelevantFormRPartBsWhenProfileMove() throws JsonProcessingException {
    //forms to move
    UUID id1 = UUID.randomUUID();
    FormRPartB formRPartB = new FormRPartB();
    formRPartB.setId(id1);
    formRPartB.setCompliments("some compliments");
    formRPartB.setLifecycleState(DRAFT);
    formRPartB.setTraineeTisId(FROM_TRAINEE_ID);
    template.insert(formRPartB);

    UUID id2 = UUID.randomUUID();
    FormRPartB formRPartB2 = new FormRPartB();
    formRPartB2.setId(id2);
    formRPartB2.setCompliments("other compliments");
    formRPartB2.setLifecycleState(DRAFT);
    formRPartB2.setTraineeTisId(FROM_TRAINEE_ID);
    template.insert(formRPartB2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeTisId").is(TO_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<FormRPartB> movedFormRpartBs = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<FormRPartB> foundB = template.find(query, FormRPartB.class);
          assertThat("Unexpected moved FormR PartB count.", foundB.size(),
              is(2));
          movedFormRpartBs.addAll(foundB);
        });

    //check that nothing apart from trainee id has changed
    FormRPartB movedForm1 = movedFormRpartBs.stream()
        .filter(a -> a.getId().equals(id1))
        .findFirst()
        .orElseThrow();
    movedForm1.setTraineeTisId(FROM_TRAINEE_ID);
    assertThat("Unexpected moved FormR PartB data.", movedForm1, is(formRPartB));

    FormRPartB movedForm2 = movedFormRpartBs.stream()
        .filter(a -> a.getId().equals(id2))
        .findFirst()
        .orElseThrow();
    movedForm2.setTraineeTisId(FROM_TRAINEE_ID);
    assertThat("Unexpected moved FormR PartB data.", movedForm2, is(formRPartB2));
  }

  @Test
  void shouldNotMoveUnexpectedFormsWhenProfileMove() throws JsonProcessingException {
    UUID id1 = UUID.randomUUID();
    FormRPartA formPartA = new FormRPartA();
    formPartA.setId(id1);
    formPartA.setCollege("another college");
    formPartA.setLifecycleState(DRAFT);
    formPartA.setTraineeTisId(TO_TRAINEE_ID);
    template.insert(formPartA);

    UUID id2 = UUID.randomUUID();
    FormRPartA formPartA2 = new FormRPartA();
    formPartA2.setId(id2);
    formPartA2.setCollege("some college");
    formPartA2.setLifecycleState(DRAFT);
    formPartA2.setTraineeTisId("another trainee");
    template.insert(formPartA2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeTisId").ne(FROM_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<FormRPartA> movedFormRpartAs = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<FormRPartA> foundA = template.find(query, FormRPartA.class);
          assertThat("Unexpected unchanged FormR PartA count.", foundA.size(),
              is(2));
          movedFormRpartAs.addAll(foundA);
        });

    FormRPartA movedForm1 = movedFormRpartAs.stream()
        .filter(a -> a.getId().equals(id1))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected changed FormR PartA data.", movedForm1, is(formPartA));

    FormRPartA movedForm2 = movedFormRpartAs.stream()
        .filter(a -> a.getId().equals(id2))
        .findFirst()
        .orElseThrow();
    assertThat("Unexpected changed FormR PartA data.", movedForm2, is(formPartA2));
  }

  @Test
  void shouldMoveFormRandS3FileWhenProfileMove() throws IOException, InterruptedException {
    //forms to move
    UUID id1 = UUID.randomUUID();
    FormRPartA formPartA = new FormRPartA();
    formPartA.setId(id1);
    formPartA.setCollege("another college");
    formPartA.setLifecycleState(SUBMITTED);
    formPartA.setSubmissionDate(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
    formPartA.setTraineeTisId(FROM_TRAINEE_ID);
    template.insert(formPartA);

    // First verify S3 bucket exists
    String[] checkBucketCmd = {
        "awslocal",
        "s3",
        "ls",
        "s3://" + S3_BUCKET
    };
    var bucketResult = localstack.execInContainer(checkBucketCmd);
    assertThat("Unexpected missing S3 bucket.", bucketResult.getExitCode(), is(0));

    String formJson = """
      {
        "id": "%s",
        "college": "another college",
        "lifecycleState": "SUBMITTED",
        "submissionDate": "2024-01-01T00:00:00",
        "traineeTisId": "%s"
      }
      """.formatted(id1, FROM_TRAINEE_ID);
    String sourceKey = String.format("%s/forms/formr-a/%s.json", FROM_TRAINEE_ID, id1);
    String[] createFileCmd = {
        "/bin/sh",
        "-c",
        String.format("printf '%s' > /tmp/form.json " +
                "&& awslocal s3 cp /tmp/form.json s3://%s/%s " +
                "&& rm /tmp/form.json", formJson, S3_BUCKET, sourceKey)
    };
    var createResult = localstack.execInContainer(createFileCmd);
    assertThat("Unexpected failed S3 file creation.", createResult.getExitCode(), is(0));

    // Verify the file exists
    String[] checkSourceCmd = {
        "awslocal",
        "s3api",
        "head-object",
        "--bucket", S3_BUCKET,
        "--key", sourceKey
    };
    var verifyResult = localstack.execInContainer(checkSourceCmd);
    assertThat("Unexpected missing source S3 file.", verifyResult.getExitCode(), is(0));

    // trigger the move
    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);

    JsonNode eventJson = JsonMapper.builder()
        .build()
        .readTree(eventString);

    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeTisId").is(TO_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<FormRPartA> movedFormRpartAs = new ArrayList<>();

    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<FormRPartA> foundA = template.find(query, FormRPartA.class);
          assertThat("Unexpected moved FormR PartA count.", foundA.size(),
              is(1));
          movedFormRpartAs.addAll(foundA);
        });

    // Verify form data is unchanged except for traineeId
    FormRPartA movedForm = movedFormRpartAs.get(0);
    movedForm.setTraineeTisId(FROM_TRAINEE_ID);
    assertThat("Unexpected moved FormR PartA data.", movedForm, is(formPartA));

    String targetKey = String.format("%s/forms/formr-a/%s.json", TO_TRAINEE_ID, id1);

    // Verify target S3 file creation and source file deletion
    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          // Check target file exists
          String[] checkTargetCmd = {
              "awslocal", "s3api", "head-object",
              "--bucket", S3_BUCKET,
              "--key", targetKey
          };
          var targetResult = localstack.execInContainer(checkTargetCmd);
          assertThat("Target S3 file should exist", targetResult.getExitCode(), is(0));

          // Check source file does not exist
          var sourceResult = localstack.execInContainer(checkSourceCmd);
          assertThat("Source S3 file should be deleted", sourceResult.getExitCode(), is(255));
        });
  }

  @Test
  void shouldMoveAllRelevantLtftFormsWhenProfileMove() throws Exception {
    // LTFT forms to move
    UUID id1 = UUID.randomUUID();
    LtftForm ltftForm1 = new LtftForm();
    ltftForm1.setId(id1);
    ltftForm1.setTraineeTisId(FROM_TRAINEE_ID);
    ltftForm1.setFormRef("one");
    template.insert(ltftForm1);

    UUID id2 = UUID.randomUUID();
    LtftForm ltftForm2 = new LtftForm();
    ltftForm2.setId(id2);
    ltftForm2.setTraineeTisId(FROM_TRAINEE_ID);
    ltftForm2.setFormRef("two");
    template.insert(ltftForm2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);
    JsonNode eventJson = JsonMapper.builder().build().readTree(eventString);
    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeTisId").is(TO_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<LtftForm> movedLtftForms = new ArrayList<>();
    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<LtftForm> found = template.find(query, LtftForm.class);
          assertThat("Unexpected moved LtftForm count.", found.size(), is(2));
          movedLtftForms.addAll(found);
        });
    // Ignore timestamps
    LtftForm moved1 = movedLtftForms.stream().filter(f -> f.getId().equals(id1)).findFirst().orElseThrow();
    assertThat(moved1.getId(), is(ltftForm1.getId()));
    assertThat(moved1.getTraineeTisId(), is(TO_TRAINEE_ID));
    assertThat(moved1.getFormRef(), is(ltftForm1.getFormRef()));
    LtftForm moved2 = movedLtftForms.stream().filter(f -> f.getId().equals(id2)).findFirst().orElseThrow();
    assertThat(moved2.getId(), is(ltftForm2.getId()));
    assertThat(moved2.getTraineeTisId(), is(TO_TRAINEE_ID));
    assertThat(moved2.getFormRef(), is(ltftForm2.getFormRef()));
    assertThat(moved2.getContent(), is(ltftForm2.getContent()));
  }

  @Test
  void shouldMoveAllRelevantLtftSubmissionHistoriesWhenProfileMove() throws Exception {
    // LTFT submission histories to move
    UUID id1 = UUID.randomUUID();
    LtftSubmissionHistory sub1 = new LtftSubmissionHistory();
    sub1.setId(id1);
    sub1.setTraineeTisId(FROM_TRAINEE_ID);
    sub1.setFormRef("one");
    sub1.setRevision(0);
    template.insert(sub1);

    UUID id2 = UUID.randomUUID();
    LtftSubmissionHistory sub2 = new LtftSubmissionHistory();
    sub2.setId(id2);
    sub2.setTraineeTisId(FROM_TRAINEE_ID);
    sub2.setFormRef("two");
    sub2.setRevision(1);
    template.insert(sub2);

    String eventString = """
        {
          "fromTraineeId": "%s",
          "toTraineeId": "%s"
        }""".formatted(FROM_TRAINEE_ID, TO_TRAINEE_ID);
    JsonNode eventJson = JsonMapper.builder().build().readTree(eventString);
    sqsTemplate.send(PROFILE_MOVE_QUEUE, eventJson);

    Criteria criteria = Criteria.where("traineeTisId").is(TO_TRAINEE_ID);
    Query query = Query.query(criteria);
    List<LtftSubmissionHistory> movedSubs = new ArrayList<>();
    await()
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .untilAsserted(() -> {
          List<LtftSubmissionHistory> found = template.find(query, LtftSubmissionHistory.class);
          assertThat("Unexpected moved LtftSubmissionHistory count.", found.size(), is(2));
          movedSubs.addAll(found);
        });
    // Ignore timestamps
    LtftSubmissionHistory moved1 = movedSubs.stream().filter(f -> f.getId().equals(id1)).findFirst().orElseThrow();
    assertThat(moved1.getId(), is(sub1.getId()));
    assertThat(moved1.getTraineeTisId(), is(TO_TRAINEE_ID));
    assertThat(moved1.getFormRef(), is(sub1.getFormRef()));
    assertThat(moved1.getRevision(), is(sub1.getRevision()));
    LtftSubmissionHistory moved2 = movedSubs.stream().filter(f -> f.getId().equals(id2)).findFirst().orElseThrow();
    assertThat(moved2.getId(), is(sub2.getId()));
    assertThat(moved2.getTraineeTisId(), is(TO_TRAINEE_ID));
    assertThat(moved2.getFormRef(), is(sub2.getFormRef()));
    assertThat(moved2.getRevision(), is(sub2.getRevision()));
  }
}
