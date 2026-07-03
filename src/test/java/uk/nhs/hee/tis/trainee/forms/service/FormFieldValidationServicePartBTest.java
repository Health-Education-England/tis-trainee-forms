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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;
import uk.nhs.hee.tis.trainee.forms.dto.CovidDeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.DeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.content.FormrPartbContentDto;
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

  @MockitoBean
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
    input.getContent().setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenSurnameIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setSurname(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenGmcNumberIsNullThenThrowsNoException() {
    FormRPartBDto input = validForm();
    input.getContent().setGmcNumber(null);

    service.validateFormRPartB(input);

    // then no exception
  }

  @ParameterizedTest
  @ValueSource(strings = {STRING_21_CHARS})
  void whenGmcNumberIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setGmcNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {STRING_21_CHARS})
  void whenGdcNumberIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setGdcNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {STRING_21_CHARS})
  void whenPublicHealthNumberIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setPublicHealthNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_256_CHARS})
  void whenEmailIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent()
        .setEmail(str); //email validation is left quite loose, there is no attempt to test RFC5322

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenLocalOfficeNameIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCurrRevalDateIsNullThenDoesNotThrowException() {
    FormRPartBDto input = validForm();
    input.getContent().setCurrRevalDate(null);

    service.validateFormRPartB(input);

    // then no exception
  }

  @Test
  void whenCurrRevalDateIsPastThenDoesNotThrowException() {
    FormRPartBDto input = validForm();
    input.getContent().setCurrRevalDate(LocalDate.MIN);

    service.validateFormRPartB(input);

    // then no exception
  }

  @Test
  void whenCurrRevalDateIsTooBigThenDoesNotThrowException() {
    FormRPartBDto input = validForm();
    input.getContent().setCurrRevalDate(LocalDate.MAX);

    service.validateFormRPartB(input);

    // then no exception
  }

  @Test
  void whenPrevRevalDateIsFutureThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setPrevRevalDate(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenProgrammeSpecialtyIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenNoWorkItemsThenThrowsException() {
    FormRPartBDto input = validForm();

    input.getContent().setWork(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    input.getContent().setWork(new ArrayList<>());
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenSicknessAbsenceIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.getContent().setSicknessAbsence(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenParentalLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.getContent().setParentalLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenCareerBreaksIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.getContent().setCareerBreaks(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenPaidLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.getContent().setPaidLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenUnauthorisedLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.getContent().setUnauthorisedLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenOtherLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.getContent().setOtherLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {INT_NEGATIVE, INT_5_DIGITS})
  void whenTotalLeaveIsInvalidThenThrowsException(Integer val) {
    FormRPartBDto input = validForm();
    input.getContent().setTotalLeave(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false})
  void whenIsHonestIsInvalidThenThrowsException(Boolean val) {
    FormRPartBDto input = validForm();
    input.getContent().setIsHonest(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false})
  void whenIsHealthyIsInvalidThenThrowsException(Boolean val) {
    FormRPartBDto input = validForm();
    input.getContent().setIsHealthy(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenIsWarnedIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setIsWarned(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false})
  void whenIsComplyingIsInvalidThenThrowsException(Boolean val) {
    FormRPartBDto input = validForm();
    input.getContent().setIsWarned(true);
    input.getContent().setIsComplying(val);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenIsComplyingIsNotRequiredThenDoesNotThrowException() {
    FormRPartBDto input = validForm();
    input.getContent().setIsWarned(false);
    input.getContent().setIsComplying(null);

    service.validateFormRPartB(input);

    // then no exception
  }

  @Test
  void whenHavePreviousDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHavePreviousDeclarations(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenHavePreviousUnresolvedDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHavePreviousUnresolvedDeclarations(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenHaveCurrentDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCurrentDeclarations(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenHaveCurrentUnresolvedDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCurrentUnresolvedDeclarations(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenPreviousDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHavePreviousDeclarations(true);

    input.getContent().setPreviousDeclarations(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    input.getContent().setPreviousDeclarations(new ArrayList<>());
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCurrentDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCurrentDeclarations(true);

    input.getContent().setCurrentDeclarations(null);
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));

    input.getContent().setCurrentDeclarations(new ArrayList<>());
    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenPreviousDeclarationSummaryIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setHavePreviousUnresolvedDeclarations(true);
    input.getContent().setPreviousDeclarationSummary(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCurrentDeclarationSummaryIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCurrentUnresolvedDeclarations(true);
    input.getContent().setCurrentDeclarationSummary(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCovidDeclarationsIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCovidDeclarations(true);
    input.getContent().setCovidDeclarationDto(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  //Tests for WorkDto

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkTypeOfWorkIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getContent().getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setTypeOfWork(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkTrainingPostIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getContent().getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setTrainingPost(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkSiteIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getContent().getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setSite(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkSiteLocationIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getContent().getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setSiteLocation(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {STRING_128_CHARS})
  void whenWorkSiteKnownAsIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getContent().getWork();
    WorkDto workDto = dtos.get(0);
    workDto.setSiteKnownAs(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenWorkStartDateIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();

    List<WorkDto> dtos = input.getContent().getWork();
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

    List<WorkDto> dtos = input.getContent().getWork();
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

    List<WorkDto> dtos = input.getContent().getWork();
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
    input.getContent().setHaveCurrentUnresolvedDeclarations(true);

    DeclarationDto declarationDto = validDeclaration();
    declarationDto.setDeclarationType(str);
    input.getContent().setCurrentDeclarations(List.of(declarationDto));

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenDeclarationTitleIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCurrentUnresolvedDeclarations(true);

    DeclarationDto declarationDto = validDeclaration();
    declarationDto.setTitle(str);
    input.getContent().setCurrentDeclarations(List.of(declarationDto));

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_128_CHARS})
  void whenDeclarationLocationIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCurrentUnresolvedDeclarations(true);

    DeclarationDto declarationDto = validDeclaration();
    declarationDto.setLocationOfEntry(str);
    input.getContent().setCurrentDeclarations(List.of(declarationDto));

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenDeclarationDateOfEntryIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCurrentUnresolvedDeclarations(true);

    DeclarationDto declarationDto = validDeclaration();
    input.getContent().setCurrentDeclarations(List.of(declarationDto));

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
    input.getContent().setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setSelfRateForCovid(str);
    input.getContent().setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCovidDeclarationOtherInformationForPanelIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setOtherInformationForPanel("x".repeat(1001));
    input.getContent().setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCovidDeclarationEducationSupervisorEmailIsInvalidThenThrowsException() {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setEducationSupervisorEmail(STRING_256_CHARS);
    input.getContent().setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCovidDeclarationChangeCircumstancesIsMissingThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setHaveChangesToPlacement(true);
    covidDeclarationDto.setHowPlacementAdjusted("some valid string");
    covidDeclarationDto.setChangeCircumstances(str);
    input.getContent().setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCovidDeclarationChangeCircumstancesOtherIsMissingThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setHaveChangesToPlacement(true);
    covidDeclarationDto.setHowPlacementAdjusted("some valid string");
    covidDeclarationDto.setChangeCircumstances("Other");
    covidDeclarationDto.setChangeCircumstanceOther(str);
    input.getContent().setCovidDeclarationDto(covidDeclarationDto);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCovidDeclarationHowPlacementAdjustedIsMissingThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.getContent().setHaveCovidDeclarations(true);
    CovidDeclarationDto covidDeclarationDto = validCovidDeclaration();
    covidDeclarationDto.setHaveChangesToPlacement(true);
    covidDeclarationDto.setChangeCircumstances("some valid string");
    covidDeclarationDto.setHowPlacementAdjusted(str);
    input.getContent().setCovidDeclarationDto(covidDeclarationDto);

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
    input.setSubmissionDate(LocalDateTime.now());
    input.setLastModifiedDate(LocalDateTime.now());
    input.setLifecycleState(LifecycleState.SUBMITTED);

    FormrPartbContentDto content = new FormrPartbContentDto();
    content.setForename("David");
    content.setSurname("Short");
    content.setGmcNumber("8999999");
    content.setEmail("potato@potato.com");
    content.setLocalOfficeName("South London");
    content.setCurrRevalDate(LocalDate.now().plusYears(1L));
    content.setProgrammeSpecialty("Geriatric Medicine");

    content.setWork(List.of(validWork()));

    content.setSicknessAbsence(1);
    content.setParentalLeave(0);
    content.setCareerBreaks(0);
    content.setPaidLeave(0);
    content.setUnauthorisedLeave(0);
    content.setOtherLeave(0);
    content.setTotalLeave(1);

    content.setIsHonest(true);
    content.setIsHealthy(true);
    content.setIsWarned(false);
    content.setIsComplying(true);

    content.setHavePreviousDeclarations(false);
    content.setHaveCurrentDeclarations(false);
    content.setHaveCovidDeclarations(false);
    content.setHaveCurrentUnresolvedDeclarations(false);
    content.setHavePreviousUnresolvedDeclarations(false);
    input.setContent(content);

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
