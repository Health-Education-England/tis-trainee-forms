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

package uk.nhs.hee.tis.trainee.forms.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartAValidator;
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartBValidator;
import uk.nhs.hee.tis.trainee.forms.config.InterceptorConfiguration;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.FormRType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

@Import({FormRPartAResource.class, FormRPartBResource.class})
@ContextConfiguration(classes = InterceptorConfiguration.class)
@WebMvcTest({FormRPartAResource.class, FormRPartBResource.class})
public class FormRResourceTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final LifecycleState DEFAULT_LIFECYCLESTATE = LifecycleState.DRAFT;
  private static final LocalDate DEFAULT_DOB = LocalDate.now().minusYears(25);
  private static final LocalDateTime DEFAULT_SUBMISSION_DATE = LocalDateTime.now();
  private static final LocalDateTime DEFAULT_LAST_MODIFIED_DATE = LocalDateTime.now();
  private static final LocalDate DEFAULT_ATTAINED_DATE = LocalDate.now().minusYears(5);
  private static final LocalDate DEFAULT_COMPLETION_DATE = LocalDate.now().plusYears(1);
  private static final LocalDate DEFAULT_START_DATE = LocalDate.now().minusYears(10);

  private static final LocalDate DEFAULT_PREV_REVAL_DATE = LocalDate.now().minusMonths(6);
  private static final LocalDate DEFAULT_CUR_REVAL_DATE = LocalDate.now().plusMonths(6);
  private static final LocalDate DEFAULT_WORK_START_DATE = LocalDate.now().minusYears(1);
  private static final LocalDate DEFAULT_WORK_END_DATE = LocalDate.now().minusMonths(3);
  private static final LocalDate DEFAULT_DECLARE_DATE = LocalDate.now().minusYears(2);

  private static final String AUTH_TOKEN =
      TestJwtUtil.generateTokenForTisId(DEFAULT_TRAINEE_TIS_ID);

  @Autowired private MockMvc mockMvc;

  @MockBean private FormRPartAService serviceA;
  @MockBean private FormRPartBService serviceB;

  @MockBean private FormRPartAValidator validatorA;
  @MockBean private FormRPartBValidator validatorB;

  @MockBean TraineeIdentity traineeIdentity;

  @MockBean PdfService pdfService;

  private FormRPartADto dtoA;
  private FormRPartBDto dtoB;

  /** init test data. */
  @BeforeEach
  void initData() {
    dtoA = new FormRPartADto();
    dtoA.setId(DEFAULT_ID);
    dtoA.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dtoA.setForename(DEFAULT_FORENAME);
    dtoA.setSurname(DEFAULT_SURNAME);
    dtoA.setLifecycleState(DEFAULT_LIFECYCLESTATE);

    dtoB = new FormRPartBDto();
    dtoB.setId(DEFAULT_ID);
    dtoB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dtoB.setForename(DEFAULT_FORENAME);
    dtoB.setSurname(DEFAULT_SURNAME);
    dtoB.setLifecycleState(DEFAULT_LIFECYCLESTATE);
  }

  @ParameterizedTest
  @EnumSource(value = FormRType.class)
  void putPdfShouldNotCreatePdfWhenNoToken(FormRType formType) throws Exception {
    String formJson = getDefaultFormJson(formType);

    mockMvc
        .perform(
            put("/api/formr-" + formType.lowerName() + "-pdf")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(formJson))
        .andExpect(status().isForbidden());

    verifyNoInteractions(pdfService);
  }

  @ParameterizedTest
  @EnumSource(value = FormRType.class)
  void putPdfShouldNotCreatePdfWhenTokenIsInvalid(FormRType formType) throws Exception {
    String formJson = getDefaultFormJson(formType);

    mockMvc
        .perform(
            put("/api/formr-" + formType.lowerName() + "-pdf")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(formJson)
                .header(HttpHeaders.AUTHORIZATION, "aa.bb.cc"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(pdfService);
  }

  @ParameterizedTest
  @EnumSource(value = FormRType.class)
  void putPdfShouldNotCreatePdfWhenTraineeIdNotInToken(FormRType formType) throws Exception {
    String formJson = getDefaultFormJson(formType);

    mockMvc
        .perform(
            put("/api/formr-" + formType.lowerName() + "-pdf")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(formJson)
                .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("{}")))
        .andExpect(status().isForbidden());

    verifyNoInteractions(pdfService);
  }

  @Test
  void putPdfShouldNotCreatePartAPdfWhenDtoValidationFails() throws Exception {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dtoA, "formDto");
    bindingResult.addError(new FieldError("formDto", "formField", "Form field not valid."));

    Method method = validatorA.getClass().getMethod("validate", FormRPartADto.class);
    Exception exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
        bindingResult);
    doThrow(exception).when(validatorA).validate(dtoA);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(put("/api/formr-parta-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dtoA))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(pdfService);
  }

  @Test
  void putPdfShouldNotCreatePartBPdfWhenDtoValidationFails() throws Exception {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dtoB, "formDto");
    bindingResult.addError(new FieldError("formDto", "formField", "Form field not valid."));

    Method method = validatorB.getClass().getMethod("validate", FormRPartBDto.class);
    Exception exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
        bindingResult);
    doThrow(exception).when(validatorB).validate(dtoB);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(put("/api/formr-partb-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dtoB))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(pdfService);
  }

  @ParameterizedTest
  @EnumSource(value = FormRType.class)
  void putPdfShouldReturnUnprocessableWhenPdfNull(FormRType formType) throws Exception {
    String formJson = getDefaultFormJson(formType);

    when(pdfService.getUploadedPdf("1/forms/formr_" + formType.lowerName()
        + "/" + DEFAULT_ID + ".pdf"))
        .thenReturn(Optional.empty());
    when(pdfService.generateFormRPartA(any(), anyBoolean())).thenReturn(null);
    when(pdfService.generateFormRPartB(any(), anyBoolean())).thenReturn(null);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc
        .perform(
            put("/api/formr-" + formType.lowerName() + "-pdf")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(formJson)
                .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isUnprocessableEntity());
  }

  @ParameterizedTest
  @EnumSource(value = FormRType.class)
  void putPdfShouldReturnUnprocessableWhenIoException(FormRType formType) throws Exception {
    String formJson = getDefaultFormJson(formType);

    when(pdfService.getUploadedPdf("1/forms/formr_" + formType.lowerName()
        + "/" + DEFAULT_ID + ".pdf"))
        .thenReturn(Optional.empty());
    doThrow(new IOException("Test exception"))
        .when(pdfService)
        .generateFormRPartA(any(), anyBoolean());
    doThrow(new IOException("Test exception"))
        .when(pdfService)
        .generateFormRPartB(any(), anyBoolean());
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc
        .perform(
            put("/api/formr-" + formType.lowerName() + "-pdf")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(formJson)
                .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isUnprocessableEntity());
  }

  @ParameterizedTest
  @EnumSource(value = FormRType.class)
  void putPdfShouldReturnPreviousPdfWhenUploadedPdfExists(FormRType formType) throws Exception {
    String formJson = getDefaultFormJson(formType);

    byte[] response = "response content".getBytes();
    Resource resource = mock(Resource.class);
    when(resource.getContentAsByteArray()).thenReturn(response);
    when(pdfService.getUploadedPdf("1/forms/formr_" + formType.lowerName()
        + "/" + DEFAULT_ID + ".pdf"))
        .thenReturn(Optional.of(resource));
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc
        .perform(
            put("/api/formr-" + formType.lowerName() + "-pdf")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(formJson)
                .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(response));

    verify(pdfService, never()).generateFormRPartA(any(), anyBoolean());
  }

  @ParameterizedTest
  @EnumSource(value = FormRType.class)
  void putPdfShouldReturnGeneratePdfWhenUploadedPdfNotExists(FormRType formType) throws Exception {
    String formJson = getDefaultFormJson(formType);

    when(pdfService.getUploadedPdf("1/forms/formr_" + formType.lowerName()
        + "/" + DEFAULT_ID + ".pdf"))
        .thenReturn(Optional.empty());

    byte[] response = "response content".getBytes();
    Resource resource = mock(Resource.class);
    when(resource.getContentAsByteArray()).thenReturn(response);
    when(pdfService.generateFormRPartA(any(), anyBoolean())).thenReturn(resource);
    when(pdfService.generateFormRPartB(any(), anyBoolean())).thenReturn(resource);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc
        .perform(
            put("/api/formr-" + formType.lowerName() + "-pdf")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(formJson)
                .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(response));
  }

  /**
   * Generate a default form JSON.
   *
   * @return the form JSON.
   */
  private String getDefaultFormJson(FormRType formType) {
    if (formType == FormRType.PARTA) {
      return """
        {
          "id": "%s",
          "traineeTisId": "%s",
          "programmeMembershipId": "%s",
          "isArcp": true,
          "forename": "%s",
          "surname": "%s",
          "gmcNumber": "1234567",
          "localOfficeName": "Test Office",
          "dateOfBirth": "%s",
          "gender": "x",
          "immigrationStatus": "Test Status",
          "qualification": "Test Qualification",
          "dateAttained": "%s",
          "medicalSchool": "Test Medical School",
          "address1": "Address Line 1",
          "address2": "Address Line 2",
          "address3": "Address Line 3",
          "address4": "Address Line 4",
          "postCode": "TE57 1NG",
          "telephoneNumber": "+0123456789",
          "mobileNumber": "+1234567890",
          "email": "test@test.com",
          "declarationType": "TYPE1",
          "isLeadingToCct": true,
          "programmeSpecialty": "Test Specialty",
          "cctSpecialty1": "CCT Specialty 1",
          "cctSpecialty2": "CCT Specialty 2",
          "college": "Test College",
          "completionDate": "%s",
          "trainingGrade": "Test Grade",
          "startDate": "%s",
          "programmeMembershipType": "Substantive",
          "wholeTimeEquivalent": "1.0",
          "submissionDate": "%s",
          "lastModifiedDate": "%s",
          "otherImmigrationStatus": "other status",
          "lifecycleState": "%s"
        }
        """
          .formatted(
              DEFAULT_ID,
              DEFAULT_TRAINEE_TIS_ID,
              UUID.randomUUID(),
              DEFAULT_FORENAME,
              DEFAULT_SURNAME,
              DEFAULT_DOB,
              DEFAULT_ATTAINED_DATE,
              DEFAULT_COMPLETION_DATE,
              DEFAULT_START_DATE,
              DEFAULT_SUBMISSION_DATE,
              DEFAULT_LAST_MODIFIED_DATE,
              DEFAULT_LIFECYCLESTATE);
    } else if (formType == FormRType.PARTB) {
      return """
      {
        "id": "%s",
        "traineeTisId": "%s",
        "programmeMembershipId": "%s",
        "isArcp": true,
        "forename": "%s",
        "surname": "%s",
        "gmcNumber": "11111111",
        "email": "test@test.com",
        "localOfficeName": "Test Office",
        "prevRevalBody": "Previous Body",
        "prevRevalBodyOther": "Other Body",
        "currRevalDate": "%s",
        "prevRevalDate": "%s",
        "programmeSpecialty": "General Practice",
        "dualSpecialty": "Other Specialty",
        "work": [
          {
            "typeOfWork": "In Post",
            "startDate": "%s",
            "endDate": "%s",
            "trainingPost": "Test Training Post",
            "site": "Test Site",
            "siteLocation": "Test Location",
            "siteKnownAs": "Test Known As"
          }
        ],
        "sicknessAbsence": 10,
        "parentalLeave": 0,
        "careerBreaks": 0,
        "paidLeave": 5,
        "unauthorisedLeave": 0,
        "otherLeave": 0,
        "totalLeave": 15,
        "isHonest": true,
        "isHealthy": true,
        "isWarned": false,
        "isComplying": true,
        "healthStatement": "No health concerns",
        "havePreviousDeclarations": true,
        "previousDeclarations": [
          {
            "declarationType": "Significant Event",
            "dateOfEntry": "%s",
            "title": "Test Previous Declaration",
            "locationOfEntry": "Test Location"
          }
        ],
        "havePreviousUnresolvedDeclarations": false,
        "previousDeclarationSummary": null,
        "haveCurrentDeclarations": false,
        "currentDeclarations": [],
        "haveCurrentUnresolvedDeclarations": false,
        "currentDeclarationSummary": null,
        "compliments": "Test compliments",
        "submissionDate": "%s",
        "lastModifiedDate": "%s",
        "lifecycleState": "%s",
        "haveCovidDeclarations": false,
        "covidDeclarationDto": null
      }
      """
          .formatted(
              DEFAULT_ID,
              DEFAULT_TRAINEE_TIS_ID,
              UUID.randomUUID(),
              DEFAULT_FORENAME,
              DEFAULT_SURNAME,
              DEFAULT_CUR_REVAL_DATE,
              DEFAULT_PREV_REVAL_DATE,
              DEFAULT_WORK_START_DATE,
              DEFAULT_WORK_END_DATE,
              DEFAULT_DECLARE_DATE,
              DEFAULT_SUBMISSION_DATE,
              DEFAULT_LAST_MODIFIED_DATE,
              DEFAULT_LIFECYCLESTATE);
    } else {
      throw new IllegalArgumentException("Unsupported form type: " + formType);
    }
  }
}
