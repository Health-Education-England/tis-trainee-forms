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
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProfileMoveListenerIntegrationTest {
  private static final String FROM_TRAINEE_ID = UUID.randomUUID().toString();
  private static final String TO_TRAINEE_ID = UUID.randomUUID().toString();

  private static final String PROFILE_MOVE_QUEUE = UUID.randomUUID().toString();

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Container
  private static final LocalStackContainer localstack = new LocalStackContainer(
      DockerImageNames.LOCALSTACK)
      .withServices(SQS);

  @DynamicPropertySource
  private static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("application.aws.sqs.profile-move", () -> PROFILE_MOVE_QUEUE);
    registry.add("application.filestore.bucket", () -> "test-filestore-bucket");

    registry.add("spring.cloud.aws.region.static", localstack::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    registry.add("spring.cloud.aws.sqs.endpoint",
        () -> localstack.getEndpointOverride(SQS).toString());
    registry.add("spring.cloud.aws.sqs.enabled", () -> true);
  }

  @BeforeAll
  static void setUpBeforeAll() throws IOException, InterruptedException {
    localstack.execInContainer("awslocal sqs create-queue --queue-name",
        PROFILE_MOVE_QUEUE);
  }

  @Autowired
  private SqsTemplate sqsTemplate;

  @Autowired
  private MongoTemplate template;

  @AfterEach
  void cleanUp() {
    template.findAllAndRemove(new Query(), FormRPartA.class);
    template.findAllAndRemove(new Query(), FormRPartB.class);
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
}
