/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.hee.tis.trainee.forms.dto;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static uk.nhs.hee.tis.trainee.forms.model.LtftApprovalType.COMPLIANT;
import static uk.nhs.hee.tis.trainee.forms.model.ReviewOutcome.Outcome.RECOMMEND_APPROVAL;
import static uk.nhs.hee.tis.trainee.forms.model.ReviewOutcome.Outcome.RECOMMEND_REJECTION;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.nhs.hee.tis.trainee.forms.model.LtftApprovalType;

class LtftReviewOutcomeDtoTest {

  private ValidatorFactory validatorFactory;
  private Validator validator;

  @BeforeEach
  void setUp() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterEach
  void tearDown() {
    validatorFactory.close();
  }

  @Test
  void shouldAllowApprovalWithDetailsAndNoComments() {
    LtftReviewOutcomeDto dto = LtftReviewOutcomeDto.builder()
        .outcome(RECOMMEND_APPROVAL)
        .outcomeDetails(
            LtftReviewOutcomeDetailsDto.builder()
                .approvalType(COMPLIANT)
                .approvedDate(LocalDate.now())
                .build())
        .build();

    Set<ConstraintViolation<LtftReviewOutcomeDto>> violations = validator.validate(dto);

    assertThat("Unexpected violations.", violations, is(empty()));
  }

  @Test
  void shouldAllowRejectionWithCommentsAndNoDetails() {
    LtftReviewOutcomeDto dto = LtftReviewOutcomeDto.builder()
        .outcome(RECOMMEND_REJECTION)
        .outcomeComments("Does not meet the criteria")
        .build();

    Set<ConstraintViolation<LtftReviewOutcomeDto>> violations = validator.validate(dto);

    assertThat("Unexpected violations.", violations, is(empty()));
  }

  @Test
  void shouldRequireDetailsForApproval() {
    LtftReviewOutcomeDto dto = LtftReviewOutcomeDto.builder()
        .outcome(RECOMMEND_APPROVAL)
        .build();

    Set<ConstraintViolation<LtftReviewOutcomeDto>> violations = validator.validate(dto);

    Set<String> fields = fieldsWithViolations(violations);
    assertThat("Unexpected violation fields.", fields, containsInAnyOrder("outcomeDetails"));
  }

  @Test
  void shouldRequireApprovalTypeForApproval() {
    LtftReviewOutcomeDto dto = LtftReviewOutcomeDto.builder()
        .outcome(RECOMMEND_APPROVAL)
        .outcomeDetails(LtftReviewOutcomeDetailsDto.builder()
            .approvedDate(LocalDate.now())
            .build())
        .build();

    Set<ConstraintViolation<LtftReviewOutcomeDto>> violations = validator.validate(dto);

    Set<String> fields = fieldsWithViolations(violations);
    assertThat("Unexpected violation fields.", fields,
        containsInAnyOrder("outcomeDetails.approvalType"));
  }

  @ParameterizedTest
  @EnumSource(LtftApprovalType.class)
  void shouldRequireApprovedDateForApproval(LtftApprovalType approvalType) {
    LtftReviewOutcomeDto dto = LtftReviewOutcomeDto.builder()
        .outcome(RECOMMEND_APPROVAL)
        .outcomeDetails(LtftReviewOutcomeDetailsDto.builder()
            .approvalType(approvalType)
            .build())
        .build();

    Set<ConstraintViolation<LtftReviewOutcomeDto>> violations = validator.validate(dto);

    Set<String> fields = fieldsWithViolations(violations);
    assertThat("Unexpected violation fields.", fields,
        containsInAnyOrder("outcomeDetails.approvedDate"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n"})
  void shouldRequireNonBlankCommentsForRejection(String comments) {
    LtftReviewOutcomeDto dto = LtftReviewOutcomeDto.builder()
        .outcome(RECOMMEND_REJECTION)
        .outcomeComments(comments)
        .build();

    Set<ConstraintViolation<LtftReviewOutcomeDto>> violations = validator.validate(dto);

    Set<String> fields = fieldsWithViolations(violations);
    assertThat("Unexpected violation fields.", fields, containsInAnyOrder("outcomeComments"));
  }

  @Test
  void shouldRequireCommentsAndDisallowDetailsForRejection() {
    LtftReviewOutcomeDto dto = LtftReviewOutcomeDto.builder()
        .outcome(RECOMMEND_REJECTION)
        .outcomeDetails(LtftReviewOutcomeDetailsDto.builder()
            .approvalType(COMPLIANT)
            .approvedDate(LocalDate.now())
            .build())
        .build();

    Set<ConstraintViolation<LtftReviewOutcomeDto>> violations = validator.validate(dto);

    Set<String> fields = fieldsWithViolations(violations);
    assertThat("Unexpected violation fields.", fields,
        containsInAnyOrder("outcomeDetails", "outcomeComments"));
  }

  /**
   * Get the set of field names that have validation violations.
   *
   * @param violations The set of constraint violations.
   * @return A set of field names with validation violations.
   */
  private Set<String> fieldsWithViolations(
      Set<ConstraintViolation<LtftReviewOutcomeDto>> violations) {
    return violations.stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}
