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
import java.util.ArrayList;
import java.util.List;
import javax.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class FormFieldValidationServicePartBTest {

  private static final String STRING_9_CHARS = "012345678";
  private static final String STRING_21_CHARS = "0123456789abcdefghij0";
  private static final String STRING_128_CHARS = "0123456789abcdefghij0123456789abcdefghij"
      + "0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij01234567";
  private static final String STRING_256_CHARS = STRING_128_CHARS + STRING_128_CHARS;

  @MockBean
  AmazonS3 amazonS3;

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
  void whenCurrRevalDateIsPastThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setCurrRevalDate(LocalDate.MIN);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @Test
  void whenCurrRevalDateIsTooBigThenThrowsException() {
    FormRPartBDto input = validForm();
    input.setCurrRevalDate(LocalDate.MAX);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
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

    WorkDto workDto = new WorkDto();
    workDto.setTypeOfWork("some type of work");
    workDto.setTrainingPost("a training post");
    workDto.setSite("the site");
    workDto.setSiteLocation("site location");
    workDto.setStartDate(LocalDate.now());
    workDto.setEndDate(LocalDate.now().plusYears(1L));

    input.setWork(List.of(workDto));

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
}
