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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;
import uk.nhs.hee.tis.trainee.forms.annotations.NotEmptyIfAnotherFieldHasValueValidation;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * A DTO for FormRPartB entity Holds the fields for the trainee's form R partB.
 */
@Data
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "haveCurrentDeclarations",
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
public class FormRPartBDto {

  private String id;
  private String traineeTisId;

  @NotNull
  @Size(min = 1, max = 100)
  private String forename;

  @NotNull
  @Size(min = 1, max = 100)
  private String surname;

  private String gmcNumber;
  private String email;
  private String localOfficeName;
  private String prevRevalBody;
  private String prevRevalBodyOther;
  private LocalDate currRevalDate;
  private LocalDate prevRevalDate;
  private String programmeSpecialty;
  private String dualSpecialty;
  private List<WorkDto> work;
  private Integer sicknessAbsence;
  private Integer parentalLeave;
  private Integer careerBreaks;
  private Integer paidLeave;
  private Integer unauthorisedLeave;
  private Integer otherLeave;
  private Integer totalLeave;
  private Boolean isHonest;
  private Boolean isHealthy;
  private Boolean isWarned;
  private Boolean isComplying;
  private String healthStatement;

  @NotNull
  private Boolean havePreviousDeclarations;

  @Valid
  private List<DeclarationDto> previousDeclarations;

  //when havePreviousDeclarations = true then required
  private String previousDeclarationSummary;

  @NotNull
  private Boolean haveCurrentDeclarations;

  @Valid
  private List<DeclarationDto> currentDeclarations;

  //when haveCurrentDeclarations = true then required
  private String currentDeclarationSummary;

  private String compliments;
  private LocalDateTime submissionDate;
  private LocalDateTime lastModifiedDate;
  private LifecycleState lifecycleState;
  private Boolean haveCovidDeclarations;
  private CovidDeclarationDto covidDeclarationDto;
  private Boolean haveCurrentUnresolvedDeclarations;
  private Boolean havePreviousUnresolvedDeclarations;
}
