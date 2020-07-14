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
import java.util.List;
import lombok.Data;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * A DTO for FormRPartB entity Holds the fields for the trainee's form R partB.
 */
@Data
public class FormRPartBDto {

  private String id;
  private String traineeTisId;
  private String forename;
  private String surname;
  private String gmcNumber;
  private String email;
  private String localOfficeName;
  private String prevRevalBody;
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
  private Boolean havePreviousDeclarations;
  private List<DeclarationDto> previousDeclarations;
  private String previousDeclarationSummary;
  private Boolean haveCurrentDeclarations;
  private List<DeclarationDto> currentDeclarations;
  private String currentDeclarationSummary;
  private String compliments;
  private LocalDate submissionDate;
  private LocalDate lastModifiedDate;
  private LifecycleState lifecycleState;
  private boolean covidFormFilled;
}
