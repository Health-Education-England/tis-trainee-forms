/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
 *
 */

package uk.nhs.hee.tis.trainee.forms.dto;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.nhs.hee.tis.trainee.forms.dto.validation.ValidationTestUtils.hasViolationForField;
import static uk.nhs.hee.tis.trainee.forms.dto.validation.ValidationTestUtils.hasViolationForFieldWithMessage;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class WorkDtoTest {

  @Nested
  class Validation {

    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
      validatorFactory = jakarta.validation.Validation.buildDefaultValidatorFactory();
      validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
      validatorFactory.close();
    }

    @Nested
    class TrainingPost {

      @Test
      void shouldNotAllowNull() {
        WorkDto workDto = new WorkDto();
        workDto.setTrainingPost(null);

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Expected violation not found.", hasViolationForFieldWithMessage(violations,
                "trainingPost",
                "must not be null"),
            is(true));
      }

      @ParameterizedTest
      @ValueSource(ints = {0, 101})
      void shouldNotAllowExceedingMinOrMaxLength(int length) {
        WorkDto workDto = new WorkDto();
        workDto.setTrainingPost("a".repeat(length));

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Expected violation not found.", hasViolationForFieldWithMessage(violations,
                "trainingPost",
                "size must be between 1 and 100"),
            is(true));
      }

      @ParameterizedTest
      @ValueSource(ints = {1, 50, 100})
      void shouldAllowValuesBetweenMinAndMax(int length) {
        WorkDto workDto = new WorkDto();
        workDto.setTrainingPost("a".repeat(length));

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Unexpected violation found.", hasViolationForField(violations,
                "trainingPost"),
            is(false));
      }
    }

    @Nested
    class Site {

      @ParameterizedTest
      @NullAndEmptySource
      void shouldBeRequiredWhenTrainingPostNotNo(String site) {
        WorkDto workDto = new WorkDto();
        workDto.setTrainingPost("Yes");
        workDto.setSite(site);

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Expected violation not found.", hasViolationForFieldWithMessage(violations,
                "site",
                "Site must be provided if this is a training post"),
            is(true));
      }

      @Test
      void shouldNotBeRequiredWhenTrainingPostNo() {
        WorkDto workDto = new WorkDto();
        workDto.setTrainingPost("No");
        workDto.setSite(null);

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Unexpected violation found.", hasViolationForField(violations,
                "site"),
            is(false));
      }

      @ParameterizedTest
      @ValueSource(ints = {0, 101})
      void shouldNotAllowExceedingMinOrMaxLength(int length) {
        WorkDto workDto = new WorkDto();
        workDto.setSite("a".repeat(length));

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Expected violation not found.", hasViolationForFieldWithMessage(violations,
                "site",
                "size must be between 1 and 100"),
            is(true));
      }

      @ParameterizedTest
      @ValueSource(ints = {1, 50, 100})
      void shouldAllowValuesBetweenMinAndMax(int length) {
        WorkDto workDto = new WorkDto();
        workDto.setSite("a".repeat(length));

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Unexpected violation found.", hasViolationForField(violations,
                "site"),
            is(false));
      }
    }

    @Nested
    class SiteLocation {

      @ParameterizedTest
      @NullAndEmptySource
      void shouldBeRequiredWhenTrainingPostNotNo(String siteLocation) {
        WorkDto workDto = new WorkDto();
        workDto.setTrainingPost("Yes");
        workDto.setSiteLocation(siteLocation);

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Expected violation not found.", hasViolationForFieldWithMessage(violations,
                "siteLocation",
                "Site location must be provided if this is a training post"),
            is(true));
      }

      @Test
      void shouldNotBeRequiredWhenTrainingPostNo() {
        WorkDto workDto = new WorkDto();
        workDto.setTrainingPost("No");
        workDto.setSiteLocation(null);

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Unexpected violation found.", hasViolationForField(violations,
                "siteLocation"),
            is(false));
      }

      @ParameterizedTest
      @ValueSource(ints = {0, 101})
      void shouldNotAllowExceedingMinOrMaxLength(int length) {
        WorkDto workDto = new WorkDto();
        workDto.setSiteLocation("a".repeat(length));

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Expected violation not found.", hasViolationForFieldWithMessage(violations,
                "siteLocation",
                "size must be between 1 and 100"),
            is(true));
      }

      @ParameterizedTest
      @ValueSource(ints = {1, 50, 100})
      void shouldAllowValuesBetweenMinAndMax(int length) {
        WorkDto workDto = new WorkDto();
        workDto.setSiteLocation("a".repeat(length));

        Set<ConstraintViolation<WorkDto>> violations = validator.validate(workDto);

        assertThat("Unexpected violation found.", hasViolationForField(violations,
                "siteLocation"),
            is(false));
      }
    }
  }
}
