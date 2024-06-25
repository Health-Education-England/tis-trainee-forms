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

import com.amazonaws.services.s3.AmazonS3;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ConfigurationPropertiesScan
class FormFieldValidationServicePartATest {

  private static final String STRING_9_CHARS = "012345678";
  private static final String STRING_21_CHARS = "0123456789abcdefghij0";
  private static final String STRING_120_CHARS = "0123456789abcdefghij0123456789abcdefghij"
      + "0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij";
  private static final String STRING_240_CHARS = STRING_120_CHARS + STRING_120_CHARS;

  @MockBean
  AmazonS3 amazonS3;

  @Autowired
  private FormFieldValidationService service;

  @Test
  void whenInputIsValidThenThrowsNoException() {
    FormRPartADto input = validForm();

    service.validateFormRPartA(input);

    // then no exception
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenForenameIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenSurnameIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setSurname(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_21_CHARS})
  void whenGmcNumberIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setGmcNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenLocalOfficeNameIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenGenderIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setGender(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateOfBirthIsTooRecentThenThrowsException() {
    FormRPartADto input = validForm();
    input.setDateOfBirth(LocalDate.now());

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateOfBirthIsTooOldThenThrowsException() {
    FormRPartADto input = validForm();
    input.setDateOfBirth(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateOfBirthIsNullThenThrowsException() {
    FormRPartADto input = validForm();
    input.setDateOfBirth(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_240_CHARS})
  void whenImmigrationStatusIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setImmigrationStatus(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenQualificationIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setQualification(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateAttainedIsFutureThenThrowsException() {
    FormRPartADto input = validForm();
    input.setDateAttained(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateAttainedIsTooOldThenThrowsException() {
    FormRPartADto input = validForm();
    input.setDateAttained(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenMedicalSchoolIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setMedicalSchool(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenAddress1IsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setAddress1(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenAddress2IsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setAddress2(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_21_CHARS})
  void whenPostCodeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setPostCode(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_9_CHARS, STRING_21_CHARS})
  void whenTelephoneNumberIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setTelephoneNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_9_CHARS, STRING_21_CHARS})
  void whenMobileNumberIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setMobileNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_9_CHARS, STRING_21_CHARS})
  void whenEmailIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setEmail(str); //email validation is left quite loose, there is no attempt to test RFC5322

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullSource
  void whenDeclarationTypeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setDeclarationType(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenProgrammeSpecialtyIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setProgrammeSpecialty(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenCctSpecialty1IsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setDeclarationType("I have been appointed to a programme leading to award of CCT");
    input.setCctSpecialty1(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCctSpecialty1IsNotConstrainedThenDoesNotThrowException(String str) {
    FormRPartADto input = validForm();
    input.setDeclarationType("another declaration type");
    input.setCctSpecialty1(str);

    service.validateFormRPartA(input);

    // then no exception
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenCollegeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setCollege(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenCompletionDateIsPastThenThrowsException() {
    FormRPartADto input = validForm();
    input.setCompletionDate(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenCompletionDateIsTooBigThenThrowsException() {
    FormRPartADto input = validForm();
    input.setCompletionDate(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenCompletionDateIsNullThenDoesNotThrowException() {
    FormRPartADto input = validForm();
    input.setCompletionDate(null);

    service.validateFormRPartA(input);

    // then no exception
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenTrainingGradeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setTrainingGrade(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenStartDateIsTooOldThenThrowsException() {
    FormRPartADto input = validForm();
    input.setStartDate(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenStartDateIsTooBigThenThrowsException() {
    FormRPartADto input = validForm();
    input.setStartDate(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenProgrammeMembershipTypeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setProgrammeMembershipType(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"1.1", "-1", "0.005", "1e0", "a", "0.99999999999", "1.00000000001"})
  void whenWholeTimeEquivalentIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.setWholeTimeEquivalent(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  /**
   * Helper function to return a validly completed FormR PartA.
   *
   * @return the FormR PartA DTO.
   */
  FormRPartADto validForm() {
    FormRPartADto input = new FormRPartADto();

    input.setId("a3586ddb-adea-4709-9806-18e5bd200a69");
    input.setForename("David");
    input.setSurname("Short");
    input.setGmcNumber("8999999");
    input.setLocalOfficeName("Health Education England South London");
    input.setDateOfBirth(LocalDate.now().minusYears(20L));
    input.setGender("Male");
    input.setImmigrationStatus("British National Overseas");
    input.setQualification("Degree");
    input.setDateAttained(LocalDate.now().minusYears(5L));
    input.setMedicalSchool("Sheffield University");
    input.setAddress1("3rd Floor");
    input.setAddress2("3 Piccadilly Place");
    input.setAddress3("Manchester");
    input.setPostCode("M1 3BN");
    input.setTelephoneNumber("0161 625 7379");
    input.setMobileNumber("+445326346346");
    input.setEmail("potato@potato.com");
    input.setDeclarationType("I have been appointed to a programme leading to award of CCT");
    input.setIsLeadingToCct(null);
    input.setProgrammeSpecialty("Geriatric Medicine");
    input.setCctSpecialty1("GP Returner");
    input.setCctSpecialty2("GP Returner");
    input.setCollege("Faculty of Intensive Care Medicine");
    input.setCompletionDate(LocalDate.now().plusYears(5));
    input.setTrainingGrade("Core Training Year 3");
    input.setStartDate(LocalDate.now());
    input.setProgrammeMembershipType("Military");
    input.setWholeTimeEquivalent("1");
    input.setSubmissionDate(LocalDateTime.now());
    input.setLastModifiedDate(LocalDateTime.now());
    input.setOtherImmigrationStatus("");
    input.setLifecycleState(LifecycleState.SUBMITTED);

    return input;
  }
}
