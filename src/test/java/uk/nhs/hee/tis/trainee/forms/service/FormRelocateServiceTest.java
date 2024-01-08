/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartARepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartBRepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@ExtendWith(MockitoExtension.class)
class FormRelocateServiceTest {

  private static final UUID FORM_ID = UUID.randomUUID();
  private static final String FORM_ID_STRING = FORM_ID.toString();
  private static final String TARGET_TRAINEE = "TARGET_TRAINEE";
  private static final String DEFAULT_TRAINEE_TIS_ID = "DEFAULT_TRAINEE";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final LocalDateTime DEFAULT_SUBMISSION_DATE = LocalDateTime.now();
  private static final String BUCKET_NAME = "BUCKET_NAME";
  private static final String SOURCE_KEY =
      DEFAULT_TRAINEE_TIS_ID + "/forms/formr-b/" + FORM_ID_STRING + ".json";
  private static final String TARGET_KEY =
      TARGET_TRAINEE + "/forms/formr-b/" + FORM_ID_STRING + ".json";


  private FormRelocateService service;

  @Mock
  private FormRPartARepository formRPartARepositoryMock;
  @Mock
  private FormRPartBRepository formRPartBRepositoryMock;
  @Mock
  private S3FormRPartARepositoryImpl abstractCloudRepositoryAMock;
  @Mock
  private S3FormRPartBRepositoryImpl abstractCloudRepositoryBMock;
  @Mock
  private AmazonS3 amazonS3Mock;

  @Captor
  private ArgumentCaptor<FormRPartA> formRPartACaptor;
  @Captor
  private ArgumentCaptor<FormRPartB> formRPartBCaptor;
  @Captor
  private ArgumentCaptor<PutObjectRequest> putRequestCaptor;

  ObjectMapper mapper = new ObjectMapper();

  private FormRPartA formRPartA;
  private FormRPartB formRPartB;
  private S3Object object1;
  private S3Object movedObject;
  private InputStream objectContent1;
  private InputStream movedObjectContent;
  private ObjectMetadata metadata1;

  @BeforeEach
  void setUp() {
    service = new FormRelocateService(
        formRPartARepositoryMock,
        formRPartBRepositoryMock,
        abstractCloudRepositoryAMock,
        abstractCloudRepositoryBMock
        );

    formRPartA = new FormRPartA();
    formRPartA.setId(FORM_ID);
    formRPartA.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA.setForename(DEFAULT_FORENAME);
    formRPartA.setSurname(DEFAULT_SURNAME);
    formRPartA.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    formRPartA.setLifecycleState(LifecycleState.DRAFT);

    formRPartB = new FormRPartB();
    formRPartB.setId(FORM_ID);
    formRPartB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB.setForename(DEFAULT_FORENAME);
    formRPartB.setSurname(DEFAULT_SURNAME);
    formRPartB.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    formRPartB.setLifecycleState(LifecycleState.SUBMITTED);

    objectContent1 = new ByteArrayInputStream("{\"traineeTisId\":\"DEFAULT_TRAINEE\"}".getBytes());
    movedObjectContent =
        new ByteArrayInputStream("{\"traineeTisId\":\"DEFAULT_TRAINEE\"}".getBytes());

    metadata1 = new ObjectMetadata();
    metadata1.addUserMetadata("traineeid", DEFAULT_TRAINEE_TIS_ID);
    metadata1.addUserMetadata("submissiondate", DEFAULT_SUBMISSION_DATE.toString());

    object1 = new S3Object();
    object1.setBucketName(BUCKET_NAME);
    object1.setKey(DEFAULT_TRAINEE_TIS_ID);
    object1.setObjectContent(objectContent1);
    object1.setObjectMetadata(metadata1);

    movedObject = new S3Object();
    movedObject.setBucketName(BUCKET_NAME);
    movedObject.setKey(TARGET_TRAINEE);
    movedObject.setObjectContent(movedObjectContent);
    movedObject.setObjectMetadata(metadata1);
  }

  @Test
  void shouldMoveDraftFormRPartAInDb() throws IOException {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartA));

    service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE);

    verify(formRPartARepositoryMock).save(formRPartACaptor.capture());
    verifyNoInteractions(formRPartBRepositoryMock);
    verifyNoInteractions(amazonS3Mock);

    FormRPartA formRPartA = formRPartACaptor.getValue();
    assertThat("Unexpected form ID.", formRPartA.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartA.getTraineeTisId(), is(TARGET_TRAINEE));
    assertThat("Unexpected forename.", formRPartA.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", formRPartA.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", formRPartA.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", formRPartA.getLifecycleState(),
        is(LifecycleState.DRAFT));
  }

  @Test
  void shouldThrowExceptionWhenGetMoveFormInfoInDbFailed() throws IOException {
    when(formRPartARepositoryMock.findById(FORM_ID))
        .thenThrow(new ApplicationException("Expected Exception"));

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE));
    verify(formRPartARepositoryMock, never()).save(any());
    verifyNoInteractions(formRPartBRepositoryMock);
    verifyNoInteractions(amazonS3Mock);
  }

  @Test
  void shouldNotUpdateDbAndS3WhenGetMoveFormInfoInDbIsNull() throws IOException {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE));

    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock, never()).save(any());
    verifyNoInteractions(amazonS3Mock);
  }

  @Test
  void shouldRollBackAndThrowExceptionWhenMoveDraftFormInDbFail() throws IOException {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartA));
    when(formRPartARepositoryMock.save(formRPartA))
        .thenThrow(new ApplicationException("Expected Exception"));

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE));

    verify(formRPartARepositoryMock, times(2)).save(formRPartACaptor.capture());
    verifyNoInteractions(formRPartBRepositoryMock);
    verifyNoInteractions(amazonS3Mock);

    // should roll back DB
    FormRPartA formRPartA = formRPartACaptor.getValue();
    assertThat("Unexpected form ID.", formRPartA.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartA.getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", formRPartA.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", formRPartA.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", formRPartA.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", formRPartA.getLifecycleState(),
        is(LifecycleState.DRAFT));
  }

  @Test
  void shouldRollBackAndThrowExceptionWhenMoveSubmittedFormInDbFail() throws IOException {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartB));
    when(formRPartBRepositoryMock.save(formRPartB))
        .thenThrow(new ApplicationException("Expected Exception"));

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE));

    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock, times(2)).save(formRPartBCaptor.capture());
    verifyNoInteractions(amazonS3Mock);

    // should roll back DB
    FormRPartB formRPartB = formRPartBCaptor.getValue();
    assertThat("Unexpected form ID.", formRPartB.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartB.getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", formRPartB.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", formRPartB.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", formRPartB.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", formRPartB.getLifecycleState(),
        is(LifecycleState.SUBMITTED));
  }

  @Test
  void shouldMoveUnsubmittedFormInDbAndS3() throws IOException {
    formRPartA.setLifecycleState(LifecycleState.UNSUBMITTED);
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartA));

    service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE);

    // should update DB
    verify(formRPartARepositoryMock).save(formRPartACaptor.capture());
    verifyNoInteractions(formRPartBRepositoryMock);

    FormRPartA formRPartA = formRPartACaptor.getValue();
    assertThat("Unexpected form ID.", formRPartA.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartA.getTraineeTisId(), is(TARGET_TRAINEE));
    assertThat("Unexpected forename.", formRPartA.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", formRPartA.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", formRPartA.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", formRPartA.getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));

    // should update S3
    verify(abstractCloudRepositoryBMock, never()).save(any());
    verify(abstractCloudRepositoryAMock).save(formRPartACaptor.capture());
    FormRPartA s3FormRPartA = formRPartACaptor.getValue();
    assertThat("Unexpected form ID.", s3FormRPartA.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", s3FormRPartA.getTraineeTisId(),
        is(TARGET_TRAINEE));
    assertThat("Unexpected forename.", s3FormRPartA.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", s3FormRPartA.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", s3FormRPartA.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", s3FormRPartA.getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));
    verify(abstractCloudRepositoryAMock).delete(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID);
  }

  @Test
  void shouldMoveSubmittedFormInDbAndS3() throws IOException {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartB));

    service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE);

    // should update DB
    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock).save(formRPartBCaptor.capture());

    FormRPartB formRPartB = formRPartBCaptor.getValue();
    assertThat("Unexpected form ID.", formRPartB.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartB.getTraineeTisId(), is(TARGET_TRAINEE));
    assertThat("Unexpected forename.", formRPartB.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", formRPartB.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", formRPartB.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", formRPartB.getLifecycleState(),
        is(LifecycleState.SUBMITTED));

    // should update S3
    verify(abstractCloudRepositoryAMock, never()).save(any());
    verify(abstractCloudRepositoryBMock).save(formRPartBCaptor.capture());
    FormRPartB s3FormRPartB = formRPartBCaptor.getValue();
    assertThat("Unexpected form ID.", s3FormRPartB.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", s3FormRPartB.getTraineeTisId(),
        is(TARGET_TRAINEE));
    assertThat("Unexpected forename.", s3FormRPartB.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", s3FormRPartB.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", s3FormRPartB.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", s3FormRPartB.getLifecycleState(),
        is(LifecycleState.SUBMITTED));
    verify(abstractCloudRepositoryBMock).delete(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID);
  }

  @Test
  void shouldRollBackAndThrowExceptionWhenMoveSubmittedFormInS3Fail() throws IOException {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartB));

    when(abstractCloudRepositoryBMock.save(formRPartB))
        .thenThrow(new ApplicationException("Expected Exception"));
    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE));

    // should roll back DB
    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock, times(2)).save(formRPartBCaptor.capture());

    List<FormRPartB> formRPartBs = formRPartBCaptor.getAllValues();
    assertThat("Unexpected form ID.", formRPartBs.get(1).getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartBs.get(1).getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", formRPartBs.get(1).getForename(),
        is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", formRPartBs.get(1).getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", formRPartBs.get(1).getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", formRPartBs.get(1).getLifecycleState(),
        is(LifecycleState.SUBMITTED));

    // should roll back S3
    verify(abstractCloudRepositoryAMock, never()).save(any());
    verify(abstractCloudRepositoryBMock, times(2)).save(formRPartBCaptor.capture());
    List<FormRPartB> s3FormRPartBs = formRPartBCaptor.getAllValues();
    assertThat("Unexpected form ID.", s3FormRPartBs.get(1).getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", s3FormRPartBs.get(1).getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", s3FormRPartBs.get(1).getForename(),
        is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", s3FormRPartBs.get(1).getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", s3FormRPartBs.get(1).getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", s3FormRPartBs.get(1).getLifecycleState(),
        is(LifecycleState.SUBMITTED));
  }
}
