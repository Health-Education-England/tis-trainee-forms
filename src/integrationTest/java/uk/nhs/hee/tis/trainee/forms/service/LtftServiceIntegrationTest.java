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

import static java.util.Collections.sort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.awspring.cloud.sns.core.SnsTemplate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LtftServiceIntegrationTest {

  private static final String TRAINEE_ID = "47165";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private LtftService service;

  @Autowired
  private TraineeIdentity traineeIdentity;

  @Autowired
  private MongoTemplate template;

  @MockBean
  private SnsTemplate snsTemplate;

  @BeforeEach
  void setUp() {
    traineeIdentity.setTraineeId(TRAINEE_ID);
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
        .build();

    LtftFormDto saved = service.createLtftForm(dto).orElseThrow();
    assertThat("Unexpected form ref.", saved.formRef(), nullValue());
  }

  @Test
  void shouldNotCountDraftsWhenGeneratingFormRefSuffix() {
    LtftFormDto dto = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .name("my test form")
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
    List<LtftSubmissionHistory> savedSubmissionHistories = template.find(query, LtftSubmissionHistory.class);

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
}
