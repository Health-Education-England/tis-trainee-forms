/*
 * The MIT License (MIT)
 * Copyright 2020 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import uk.nhs.hee.tis.trainee.forms.annotations.NotEmptyIfAnotherFieldHasValueValidation;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * A DTO for FormRPartB entity Holds the fields for the trainee's form R partB.
 */
@Data
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "haveCurrentUnresolvedDeclarations",
    fieldValue = "true",
    dependFieldName = "currentDeclarationSummary",
    message = "A summary of new unresolved declarations is required"
)
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "havePreviousUnresolvedDeclarations",
    fieldValue = "true",
    dependFieldName = "previousDeclarationSummary",
    message = "A summary of previous unresolved declarations is required"
)
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "haveCurrentDeclarations",
    fieldValue = "true",
    dependFieldName = "currentDeclarations",
    message = "At least one declaration is required"
)
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "havePreviousDeclarations",
    fieldValue = "true",
    dependFieldName = "previousDeclarations",
    message = "At least one declaration is required"
)
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "isWarned",
    fieldValue = "true",
    dependFieldName = "isComplying",
    message = "You must indicate compliance with these conditions/undertakings"
)
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "haveCovidDeclarations",
    fieldValue = "true",
    dependFieldName = "covidDeclarationDto",
    message = "Covid declaration details are required"
)
public class FormRPartBDto {

  private String id;
  private String traineeTisId;
  private UUID programmeMembershipId;
  private Boolean isArcp;

  @NotNull
  @Size(min = 1, max = 100)
  private String forename;

  @NotNull
  @Size(min = 1, max = 100)
  private String surname;

  @NotNull
  @Size(min = 1, max = 20)
  private String gmcNumber;

  @NotBlank
  @Email
  @Size(min = 1, max = 255)
  private String email;

  @NotNull
  @Size(min = 1, max = 100)
  private String localOfficeName;

  private String prevRevalBody;

  private String prevRevalBodyOther;

  @NotNull
  private LocalDate currRevalDate;

  @PastOrPresent
  private LocalDate prevRevalDate;

  @NotNull
  @Size(min = 1, max = 100)
  private String programmeSpecialty;

  private String dualSpecialty;

  @Valid
  @NotNull
  @Size(min = 1)
  private List<WorkDto> work;

  @NotNull
  @Min(0)
  @Max(9999)
  private Integer sicknessAbsence;

  @NotNull
  @Min(0)
  @Max(9999)
  private Integer parentalLeave;

  @NotNull
  @Min(0)
  @Max(9999)
  private Integer careerBreaks;

  @NotNull
  @Min(0)
  @Max(9999)
  private Integer paidLeave;

  @NotNull
  @Min(0)
  @Max(9999)
  private Integer unauthorisedLeave;

  @NotNull
  @Min(0)
  @Max(9999)
  private Integer otherLeave;

  @NotNull
  @Min(0)
  @Max(9999)
  private Integer totalLeave;

  @NotNull
  @AssertTrue
  private Boolean isHonest;

  @NotNull
  @AssertTrue
  private Boolean isHealthy;

  @NotNull
  private Boolean isWarned;

  //when isWarned is true then isComplying must be true
  @AssertTrue
  private Boolean isComplying;

  private String healthStatement;

  @NotNull
  private Boolean havePreviousDeclarations;

  @Valid
  //when havePreviousDeclarations = true then size must be >= 1
  private List<DeclarationDto> previousDeclarations;

  @NotNull
  private Boolean havePreviousUnresolvedDeclarations;

  //when havePreviousUnresolvedDeclarations = true then this is required
  private String previousDeclarationSummary;

  @NotNull
  private Boolean haveCurrentDeclarations;

  @Valid
  //when haveCurrentsDeclarations = true then size must be >= 1
  private List<DeclarationDto> currentDeclarations;

  @NotNull
  private Boolean haveCurrentUnresolvedDeclarations;

  //when haveCurrentUnresolvedDeclarations = true then this is required
  private String currentDeclarationSummary;

  private String compliments;

  private LocalDateTime submissionDate;

  private LocalDateTime lastModifiedDate;

  private LifecycleState lifecycleState;

  private Boolean haveCovidDeclarations;

  //when haveCovidDeclarations = true then this is required
  @Valid
  private CovidDeclarationDto covidDeclarationDto;
}
