/*
 * The MIT License (MIT)
 * Copyright 2024 Crown Copyright (Health Education England)
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.validation.ValidationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import uk.nhs.hee.tis.trainee.forms.dto.CovidDeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.DeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@ContextConfiguration(classes = ValidationAutoConfiguration.class)
@SpringBootTest(classes = FormFieldValidationService.class)
class FormFieldValidationServicePartBTest {

  private static final String STRING_21_CHARS = "0123456789abcdefghij0";
  private static final String STRING_128_CHARS = "0123456789abcdefghij0123456789abcdefghij"
      + "0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij01234567";
  private static final String STRING_256_CHARS = STRING_128_CHARS + STRING_128_CHARS;
  private static final int INT_NEGATIVE = -1;
  private static final int INT_5_DIGITS = 10000;

  @MockBean
  S3Client amazonS3;

  @Autowired
  private FormFieldValidationService service;

  @Test
  void whenInputIsValidThenThrowsNoException() {
    FormRPartBDto input = validForm();

    service.validateFormRPartB(input);

    // then no exception
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenForenameIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenSurnameIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setSurname(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_21_CHARS})
  void whenGmcNumberIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setGmcNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_256_CHARS})
  void whenEmailIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setEmail(str); //email validation is left quite loose, there is no attempt to test RFC5322

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenLocalOfficeNameIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCurrRevalDateIsNullThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setCurrRevalDate(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCurrRevalDateIsPastThenDoesNotThrowException() {
    FormRPartBDto input = validForm();
    input.setCurrRevalDate(LocalDate.MIN);

    service.validateFormRPartB(input);

    // then no exception
  }

  @Test
  void whenCurrRevalDateIsTooBigThenDoesNotThrowException() {
    FormRPartBDto input = validForm();
    input.setCurrRevalDate(LocalDate.MAX);

    service.validateFormRPartB(input);

    // then no exception
  }

  @Test
  void whenPrevRevalDateIsFutureThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setPrevRevalDate(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenProgrammeSpecialtyIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenNoWorkItemsThenThrowsException() {
    FormRPartBDto input = validForm();

    input.setWork(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    input.setWork(new ArrayList<>());
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenSicknessAbsenceIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.setSicknessAbsence(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenParentalLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.setParentalLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenCareerBreaksIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.setCareerBreaks(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenPaidLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.setPaidLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenUnauthorisedLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.setUnauthorisedLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenOtherLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.setOtherLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenTotalLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.setTotalLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false})
  void whenIsHonestIsInvalidThenThrowsException(Boolean val) {
    FormRPartBDto input = validForm();
    input.setIsHonest(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false})
  void whenIsHealthyIsInvalidThenThrowsException(Boolean val) {
    FormRPartBDto input = validForm();
    input.setIsHealthy(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenIsWarnedIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setIsWarned(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false})
  void whenIsComplyingIsInvalidThenThrowsException(Boolean val) {
    FormRPartBDto input = validForm();
    input.setIsWarned(true);
    input.setIsComplying(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenIsComplyingIsNotRequiredThenDoesNotThrowException() {
    FormRPartBDto input = validForm();
    input.setIsWarned(false);
    input.setIsComplying(null);

    service.validateFormRPartB(input);

    // then no exception
  }

  @Test
  void whenHavePreviousDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHavePreviousDeclarations(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenHavePreviousUnresolvedDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHavePreviousUnresolvedDeclarations(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenHaveCurrentDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHaveCurrentDeclarations(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenHaveCurrentUnresolvedDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHaveCurrentUnresolvedDeclarations(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenPreviousDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHavePreviousDeclarations(true);

    input.setPreviousDeclarations(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    input.setPreviousDeclarations(new ArrayList<>());
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCurrentDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHaveCurrentDeclarations(true);

    input.setCurrentDeclarations(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    input.setCurrentDeclarations(new ArrayList<>());
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenPreviousDeclarationSummaryIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHavePreviousUnresolvedDeclarations(true);
    input.setPreviousDeclarationSummary(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCurrentDeclarationSummaryIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHaveCurrentUnresolvedDeclarations(true);
    input.setCurrentDeclarationSummary(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCovidDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHaveCovidDeclarations(true);
    input.setCovidDeclarationDto(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  //Tests for WorkDto

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkTypeOfWorkIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setTypeOfWork(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkTrainingPostIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setTrainingPost(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkSiteIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setSite(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkSiteLocationIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setSiteLocation(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkSiteKnownAsIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setSiteKnownAs(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenWorkStartDateIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getWork();
    WorkDto workDto = dtos.get(0);

    workDto.setStartDate(LocalDate.MIN);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    workDto.setStartDate(LocalDate.MAX);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    workDto.setStartDate(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenWorkEndDateIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setStartDate(LocalDate.MIN);

    workDto.setEndDate(LocalDate.MIN.plusYears(1));
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    workDto.setEndDate(LocalDate.MAX);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    workDto.setEndDate(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenWorkEndDateIsBeforeStartDateOrNullThenThrowsException() {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setStartDate(LocalDate.now());
    workDto.setEndDate(LocalDate.now().minusYears(1));

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  //Tests for DeclarationDto

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenDeclarationTypeIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHaveCurrentUnresolvedDeclarations(true);

    DeclarationDto declarationDto = validDeclaration();
    declarationDto.setDeclarationType(str);
    input.setCurrentDeclarations(List.of(declarationDto));

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenDeclarationTitleIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHaveCurrentUnresolvedDeclarations(true);

    DeclarationDto declarationDto = validDeclaration();
    declarationDto.setTitle(str);
    input.setCurrentDeclarations(List.of(declarationDto));

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenDeclarationLocationIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHaveCurrentUnresolvedDeclarations(true);

    DeclarationDto declarationDto = validDeclaration();
    declarationDto.setLocationOfEntry(str);
    input.setCurrentDeclarations(List.of(declarationDto));

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenDeclarationDateOfEntryIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHaveCurrentUnresolvedDeclarations(true);

    DeclarationDto declarationDto = validDeclaration();
    input.setCurrentDeclarations(List.of(declarationDto));

    declarationDto.setDateOfEntry(LocalDate.MAX);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    declarationDto.setDateOfEntry(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  //Tests for CovidDeclarationDto

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenCovidDeclarationReasonOfSelfRateIsMissingThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setSelfRateForCovid(str);
    input.setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCovidDeclarationOtherInformationForPanelIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setOtherInformationForPanel("x".repeat(1001));
    input.setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCovidDeclarationEducationSupervisorEmailIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setEducationSupervisorEmail(STRING_256_CHARS);
    input.setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCovidDeclarationChangeCircumstancesIsMissingThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setHaveChangesToPlacement(true);
    covidDeclarationDto.setHowPlacementAdjusted("some valid string");
    covidDeclarationDto.setChangeCircumstances(str);
    input.setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCovidDeclarationChangeCircumstancesOtherIsMissingThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setHaveChangesToPlacement(true);
    covidDeclarationDto.setHowPlacementAdjusted("some valid string");
    covidDeclarationDto.setChangeCircumstances("Other");
    covidDeclarationDto.setChangeCircumstanceOther(str);
    input.setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCovidDeclarationHowPlacementAdjustedIsMissingThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setHaveChangesToPlacement(true);
    covidDeclarationDto.setChangeCircumstances("some valid string");
    covidDeclarationDto.setHowPlacementAdjusted(str);
    input.setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  /**
   * Helper function to return a validly completed FormR PartB.
   *
   * @return the FormR PartB DTO.
   */
  FormRPartBDto validForm() {
    FormRPartBDto input = new FormRPartBDto();

    input.setId("a3586ddb-adea-4709-9806-18e5bd200a69");
    input.setForename("David");
    input.setSurname("Short");
    input.setGmcNumber("8999999");
    input.setEmail("potato@potato.com");
    input.setLocalOfficeName("Health Education England South London");
    input.setCurrRevalDate(LocalDate.now().plusYears(1L));
    input.setProgrammeSpecialty("Geriatric Medicine");

    input.setWork(List.of(validWork()));

    input.setSicknessAbsence(1);
    input.setParentalLeave(0);
    input.setCareerBreaks(0);
    input.setPaidLeave(0);
    input.setUnauthorisedLeave(0);
    input.setOtherLeave(0);
    input.setTotalLeave(1);

    input.setIsHonest(true);
    input.setIsHealthy(true);
    input.setIsWarned(false);
    input.setIsComplying(true);

    input.setHavePreviousDeclarations(false);
    input.setHaveCurrentDeclarations(false);
    input.setHaveCovidDeclarations(false);
    input.setHaveCurrentUnresolvedDeclarations(false);
    input.setHavePreviousUnresolvedDeclarations(false);

    input.setSubmissionDate(LocalDateTime.now());
    input.setLastModifiedDate(LocalDateTime.now());

    input.setLifecycleState(LifecycleState.SUBMITTED);

    return input;
  }

  WorkDto validWork() {
    WorkDto workDto = new WorkDto();
    workDto.setTypeOfWork("some type of work");
    workDto.setTrainingPost("a training post");
    workDto.setSite("the site");
    workDto.setSiteLocation("site location");
    workDto.setStartDate(LocalDate.now());
    workDto.setEndDate(LocalDate.now().plusYears(1L));
    return workDto;
  }

  DeclarationDto validDeclaration() {
    DeclarationDto declarationDto = new DeclarationDto();
    declarationDto.setDeclarationType("a declaration type");
    declarationDto.setDateOfEntry(LocalDate.now().minusMonths(1));
    declarationDto.setTitle("some declaration title");
    declarationDto.setLocationOfEntry("declaration location");
    return declarationDto;
  }

  CovidDeclarationDto validCovidDeclaration() {
    CovidDeclarationDto covidDeclarationDto = new CovidDeclarationDto();
    covidDeclarationDto.setSelfRateForCovid("Satisfactory progress for stage of training and "
        + "required competencies met");
    covidDeclarationDto.setHaveChangesToPlacement(false);
    return covidDeclarationDto;
  }
}
