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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.content.FormrPartaContentDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@ContextConfiguration(classes = ValidationAutoConfiguration.class)
@SpringBootTest(classes = FormFieldValidationService.class)
class FormFieldValidationServicePartATest {

  private static final String STRING_9_CHARS = "012345678";
  private static final String STRING_21_CHARS = "0123456789abcdefghij0";
  private static final String STRING_120_CHARS = "0123456789abcdefghij0123456789abcdefghij"
      + "0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij";
  private static final String STRING_240_CHARS = STRING_120_CHARS + STRING_120_CHARS;

  @MockitoBean
  S3Client amazonS3;

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
    input.getContent().setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenSurnameIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setSurname(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenGmcNumberIsNullThenThrowsNoException() {
    FormRPartADto input = validForm();
    input.getContent().setGmcNumber(null);

    service.validateFormRPartA(input);

    // then no exception
  }

  @ParameterizedTest
  @ValueSource(strings = {STRING_21_CHARS})
  void whenGmcNumberIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setGmcNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {STRING_21_CHARS})
  void whenGdcNumberIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setGdcNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {STRING_21_CHARS})
  void whenPublicHealthNumberIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();

    input.getContent().setPublicHealthNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenLocalOfficeNameIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenGenderIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setGender(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateOfBirthIsTooRecentThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setDateOfBirth(LocalDate.now());

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateOfBirthIsTooOldThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setDateOfBirth(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateOfBirthIsNullThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setDateOfBirth(null);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_240_CHARS})
  void whenImmigrationStatusIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setImmigrationStatus(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenQualificationIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setQualification(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateAttainedIsFutureThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setDateAttained(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenDateAttainedIsTooOldThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setDateAttained(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenMedicalSchoolIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setMedicalSchool(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenAddress1IsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setAddress1(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenAddress2IsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setAddress2(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_21_CHARS})
  void whenPostCodeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setPostCode(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_9_CHARS, STRING_21_CHARS})
  void whenTelephoneNumberIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setTelephoneNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_9_CHARS, STRING_21_CHARS})
  void whenMobileNumberIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setMobileNumber(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_9_CHARS, STRING_21_CHARS})
  void whenEmailIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent()
        .setEmail(str); //email validation is left quite loose, there is no attempt to test RFC5322

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullSource
  void whenDeclarationTypeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setDeclarationType(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenProgrammeSpecialtyIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setProgrammeSpecialty(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenCctSpecialty1IsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent()
        .setDeclarationType("I have been appointed to a programme leading to award of CCT");
    input.getContent().setCctSpecialty1(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void whenCctSpecialty1IsNotConstrainedThenDoesNotThrowException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setDeclarationType("another declaration type");
    input.getContent().setCctSpecialty1(str);

    service.validateFormRPartA(input);

    // then no exception
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenCollegeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setCollege(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenCompletionDateIsPastThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setCompletionDate(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenCompletionDateIsTooBigThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setCompletionDate(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenCompletionDateIsNullThenDoesNotThrowException() {
    FormRPartADto input = validForm();
    input.getContent().setCompletionDate(null);

    service.validateFormRPartA(input);

    // then no exception
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenTrainingGradeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setTrainingGrade(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenStartDateIsTooOldThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setStartDate(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @Test
  void whenStartDateIsTooBigThenThrowsException() {
    FormRPartADto input = validForm();
    input.getContent().setStartDate(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenProgrammeMembershipTypeIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setProgrammeMembershipType(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"1.1", "-1", "0.005", "1e0", "a", "0.99999999999", "1.00000000001"})
  void whenWholeTimeEquivalentIsInvalidThenThrowsException(String str) {
    FormRPartADto input = validForm();
    input.getContent().setWholeTimeEquivalent(str);

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
    input.setSubmissionDate(LocalDateTime.now());
    input.setLastModifiedDate(LocalDateTime.now());
    input.setLifecycleState(LifecycleState.SUBMITTED);

    FormrPartaContentDto content = new FormrPartaContentDto();
    content.setForename("David");
    content.setSurname("Short");
    content.setGmcNumber("8999999");
    content.setLocalOfficeName("South London");
    content.setDateOfBirth(LocalDate.now().minusYears(20L));
    content.setGender("Male");
    content.setImmigrationStatus("British National Overseas");
    content.setQualification("Degree");
    content.setDateAttained(LocalDate.now().minusYears(5L));
    content.setMedicalSchool("Sheffield University");
    content.setAddress1("3rd Floor");
    content.setAddress2("3 Piccadilly Place");
    content.setAddress3("Manchester");
    content.setPostCode("M1 3BN");
    content.setTelephoneNumber("0161 625 7379");
    content.setMobileNumber("+445326346346");
    content.setEmail("potato@potato.com");
    content.setDeclarationType("I have been appointed to a programme leading to award of CCT");
    content.setIsLeadingToCct(null);
    content.setProgrammeSpecialty("Geriatric Medicine");
    content.setCctSpecialty1("GP Returner");
    content.setCctSpecialty2("GP Returner");
    content.setCollege("Faculty of Intensive Care Medicine");
    content.setCompletionDate(LocalDate.now().plusYears(5));
    content.setTrainingGrade("Core Training Year 3");
    content.setStartDate(LocalDate.now());
    content.setProgrammeMembershipType("Military");
    content.setWholeTimeEquivalent("1");
    content.setOtherImmigrationStatus("");
    input.setContent(content);

    return input;
  }
}
