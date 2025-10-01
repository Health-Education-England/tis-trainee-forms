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
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import software.amazon.awssdk.services.s3.S3Client;
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
  private static final String TARGET_TRAINEE_TIS_ID = "TARGET_TRAINEE";
  private static final String DEFAULT_TRAINEE_TIS_ID = "DEFAULT_TRAINEE";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final LocalDateTime DEFAULT_SUBMISSION_DATE = LocalDateTime.now();


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
  private S3Client amazonS3Mock;

  @Captor
  private ArgumentCaptor<FormRPartA> formRPartACaptor;
  @Captor
  private ArgumentCaptor<FormRPartB> formRPartBCaptor;

  ObjectMapper mapper = new ObjectMapper();

  private FormRPartA formRPartA;
  private FormRPartB formRPartB;

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
    formRPartA.setLifecycleState(DRAFT);

    formRPartB = new FormRPartB();
    formRPartB.setId(FORM_ID);
    formRPartB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB.setForename(DEFAULT_FORENAME);
    formRPartB.setSurname(DEFAULT_SURNAME);
    formRPartB.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    formRPartB.setLifecycleState(LifecycleState.SUBMITTED);
  }

  @Test
  void shouldMoveDraftFormRPartAInDb() {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartA));

    service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID);

    verify(formRPartARepositoryMock).save(formRPartACaptor.capture());
    verifyNoInteractions(formRPartBRepositoryMock);
    verifyNoInteractions(amazonS3Mock);

    FormRPartA formRPartA = formRPartACaptor.getValue();
    assertThat("Unexpected form ID.", formRPartA.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartA.getTraineeTisId(), is(TARGET_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", formRPartA.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", formRPartA.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", formRPartA.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", formRPartA.getLifecycleState(),
        is(DRAFT));
  }

  @Test
  void shouldThrowExceptionWhenGetMoveFormInfoInDbFailed() {
    when(formRPartARepositoryMock.findById(FORM_ID))
        .thenThrow(new ApplicationException("Expected Exception"));
    when(formRPartBRepositoryMock.findById(FORM_ID))
        .thenThrow(new ApplicationException("Expected Exception"));

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));
    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock, never()).save(any());
    verifyNoInteractions(amazonS3Mock);
  }

  @Test
  void shouldTryPartBRepositoryWhenGetMoveFormInfoInDbFailedForPartARepository() {
    when(formRPartARepositoryMock.findById(FORM_ID))
        .thenThrow(new ApplicationException("Expected Exception"));
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));
    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock).findById(FORM_ID);
    verify(formRPartBRepositoryMock, never()).save(any());
    verifyNoInteractions(amazonS3Mock);
  }

  @Test
  void shouldNotUpdateDbAndS3WhenGetMoveFormInfoInDbIsNull() {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));

    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock, never()).save(any());
    verifyNoInteractions(amazonS3Mock);
  }

  @Test
  void shouldNotUpdateDbAndS3WhenSourceTraineeIsSameAsTargetTrainee() {
    formRPartA.setTraineeTisId(TARGET_TRAINEE_TIS_ID);
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartA));

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));

    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock, never()).save(any());
    verifyNoInteractions(amazonS3Mock);
  }

  @Test
  void shouldRollBackAndThrowExceptionWhenMoveDraftFormInDbFail() {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartA));
    when(formRPartARepositoryMock.save(formRPartA))
        .thenThrow(new ApplicationException("Expected Exception"));

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));

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
        is(DRAFT));
  }

  @Test
  void shouldRollBackAndThrowExceptionWhenMoveSubmittedFormInDbFail() {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartB));
    when(formRPartBRepositoryMock.save(formRPartB))
        .thenThrow(new ApplicationException("Expected Exception"));

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));

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
  void shouldMoveUnsubmittedFormInDbAndS3() {
    formRPartA.setLifecycleState(LifecycleState.UNSUBMITTED);
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartA));
    when(abstractCloudRepositoryAMock
        .findByIdAndTraineeTisId(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(formRPartA));

    service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID);

    // should update DB
    verify(formRPartARepositoryMock).save(formRPartACaptor.capture());
    verifyNoInteractions(formRPartBRepositoryMock);

    FormRPartA formRPartA = formRPartACaptor.getValue();
    assertThat("Unexpected form ID.", formRPartA.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartA.getTraineeTisId(), is(TARGET_TRAINEE_TIS_ID));
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
        is(TARGET_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", s3FormRPartA.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", s3FormRPartA.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", s3FormRPartA.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", s3FormRPartA.getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));
    verify(abstractCloudRepositoryAMock).delete(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID);
  }

  @Test
  void shouldMoveSubmittedFormInDbAndS3() {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartB));
    when(abstractCloudRepositoryBMock
        .findByIdAndTraineeTisId(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(formRPartB));

    service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID);

    // should update DB
    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock).save(formRPartBCaptor.capture());

    FormRPartB formRPartB = formRPartBCaptor.getValue();
    assertThat("Unexpected form ID.", formRPartB.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartB.getTraineeTisId(), is(TARGET_TRAINEE_TIS_ID));
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
        is(TARGET_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", s3FormRPartB.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", s3FormRPartB.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", s3FormRPartB.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", s3FormRPartB.getLifecycleState(),
        is(LifecycleState.SUBMITTED));
    verify(abstractCloudRepositoryBMock).delete(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID);
  }

  @Test
  void shouldRollBackAndThrowExceptionWhenFormAInS3NotFound() {
    formRPartA.setLifecycleState(LifecycleState.UNSUBMITTED);
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartA));
    when(abstractCloudRepositoryAMock
        .findByIdAndTraineeTisId(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));

    // should roll back DB
    verify(formRPartARepositoryMock, times(2)).save(formRPartACaptor.capture());
    verify(formRPartBRepositoryMock, never()).save(any());

    List<FormRPartA> formRPartAs = formRPartACaptor.getAllValues();
    assertThat("Unexpected form ID.", formRPartAs.get(1).getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", formRPartAs.get(1).getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", formRPartAs.get(1).getForename(),
        is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", formRPartAs.get(1).getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", formRPartAs.get(1).getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", formRPartAs.get(1).getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));

    // should try roll back S3 but not saving as form not found
    verify(abstractCloudRepositoryAMock, never()).save(any());
    verify(abstractCloudRepositoryBMock, never()).save(any());
  }

  @Test
  void shouldRollBackAndThrowExceptionWhenFormBInS3NotFound() {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartB));
    when(abstractCloudRepositoryBMock
        .findByIdAndTraineeTisId(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.empty());

    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));

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

    // should try roll back S3 but not saving as form not found
    verify(abstractCloudRepositoryAMock, never()).save(any());
    verify(abstractCloudRepositoryBMock, never()).save(any());
  }

  @Test
  void shouldRollBackAndThrowExceptionWhenMoveSubmittedFormInS3Fail() {
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.empty());
    when(formRPartBRepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formRPartB));
    when(abstractCloudRepositoryBMock
        .findByIdAndTraineeTisId(FORM_ID.toString(), DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(formRPartB));

    when(abstractCloudRepositoryBMock.save(formRPartB))
        .thenThrow(new ApplicationException("Expected Exception"));
    assertThrows(ApplicationException.class, () ->
        service.relocateForm(FORM_ID_STRING, TARGET_TRAINEE_TIS_ID));

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
    verify(abstractCloudRepositoryBMock).save(formRPartBCaptor.capture());
    FormRPartB s3FormRPartB = formRPartBCaptor.getValue();
    assertThat("Unexpected form ID.", s3FormRPartB.getId(), is(FORM_ID));
    assertThat("Unexpected trainee ID.", s3FormRPartB.getTraineeTisId(),
        is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", s3FormRPartB.getForename(),
        is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", s3FormRPartB.getSurname(), is(DEFAULT_SURNAME));
    assertThat("Unexpected submissionDate.", s3FormRPartB.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycleState.", s3FormRPartB.getLifecycleState(),
        is(LifecycleState.SUBMITTED));
  }

  @Test
  void shouldMoveAllForms() {
    FormRPartA formA1 = new FormRPartA();
    formA1.setId(FORM_ID);
    formA1.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formA1.setLifecycleState(DRAFT);
    FormRPartA formA2 = new FormRPartA();
    formA2.setId(UUID.randomUUID());
    formA2.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formA2.setLifecycleState(DRAFT);

    FormRPartB formB1 = new FormRPartB();
    formB1.setId(UUID.randomUUID());
    formB1.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formB1.setLifecycleState(DRAFT);
    FormRPartB formB2 = new FormRPartB();
    formB2.setId(UUID.randomUUID());
    formB2.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formB2.setLifecycleState(DRAFT);

    when(formRPartARepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(List.of(formA1, formA2));
    when(formRPartBRepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(List.of(formB1, formB2));

    when(formRPartARepositoryMock.findById(formA1.getId())).thenReturn(Optional.of(formA1));
    when(formRPartARepositoryMock.findById(formA2.getId())).thenReturn(Optional.of(formA2));
    when(formRPartBRepositoryMock.findById(formB1.getId())).thenReturn(Optional.of(formB1));
    when(formRPartBRepositoryMock.findById(formB2.getId())).thenReturn(Optional.of(formB2));

    service.moveAllForms(DEFAULT_TRAINEE_TIS_ID, TARGET_TRAINEE_TIS_ID);

    verify(formRPartARepositoryMock, times(2)).save(formRPartACaptor.capture());
    verify(formRPartBRepositoryMock, times(2)).save(formRPartBCaptor.capture());
    List<FormRPartA> savedFormRPartAs = formRPartACaptor.getAllValues();
    for (FormRPartA formRPartA : savedFormRPartAs) {
      assertThat("Unexpected trainee ID.", formRPartA.getTraineeTisId(), is(TARGET_TRAINEE_TIS_ID));
    }
    List<FormRPartB> savedFormRPartBs = formRPartBCaptor.getAllValues();
    for (FormRPartB formRPartB : savedFormRPartBs) {
      assertThat("Unexpected trainee ID.", formRPartB.getTraineeTisId(), is(TARGET_TRAINEE_TIS_ID));
    }
  }

  @Test
  void shouldContinueMovingFormsWhenOneFormFailsToMove() {
    FormRPartA formA1 = new FormRPartA();
    formA1.setId(FORM_ID);
    formA1.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    FormRPartB formB1 = new FormRPartB();
    UUID formBid = UUID.randomUUID();
    formB1.setId(formBid);
    formB1.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formB1.setLifecycleState(DRAFT);

    when(formRPartARepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(List.of(formA1));
    when(formRPartBRepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(List.of(formB1));

    // Make first form move fail
    when(formRPartARepositoryMock.findById(any()))
        .thenThrow(new ApplicationException("Expected Exception"));
    when(formRPartBRepositoryMock.findById(formBid)).thenReturn(Optional.of(formB1));

    service.moveAllForms(DEFAULT_TRAINEE_TIS_ID, TARGET_TRAINEE_TIS_ID);

    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock).save(any());
  }

  @Test
  void shouldMoveFormsWhenLaterFormFailsToMove() {
    FormRPartA formA1 = new FormRPartA();
    formA1.setId(FORM_ID);
    formA1.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formA1.setLifecycleState(DRAFT);
    FormRPartB formB1 = new FormRPartB();
    UUID formBid = UUID.randomUUID();
    formB1.setId(formBid);
    formB1.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formB1.setLifecycleState(DRAFT);

    when(formRPartARepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(List.of(formA1));
    when(formRPartBRepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(List.of(formB1));

    // Make second form move fail
    when(formRPartBRepositoryMock.findById(any()))
        .thenThrow(new ApplicationException("Expected Exception"));
    when(formRPartARepositoryMock.findById(FORM_ID)).thenReturn(Optional.of(formA1));

    service.moveAllForms(DEFAULT_TRAINEE_TIS_ID, TARGET_TRAINEE_TIS_ID);

    verify(formRPartBRepositoryMock, never()).save(any());
    verify(formRPartARepositoryMock).save(any());
  }

  @Test
  void shouldHandleEmptyFormLists() {
    when(formRPartARepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(List.of());
    when(formRPartBRepositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(List.of());

    service.moveAllForms(DEFAULT_TRAINEE_TIS_ID, TARGET_TRAINEE_TIS_ID);

    verify(formRPartARepositoryMock, never()).save(any());
    verify(formRPartBRepositoryMock, never()).save(any());
    verifyNoInteractions(abstractCloudRepositoryAMock);
    verifyNoInteractions(abstractCloudRepositoryBMock);
  }

}
