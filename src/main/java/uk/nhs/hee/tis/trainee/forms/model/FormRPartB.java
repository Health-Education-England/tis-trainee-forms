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

package uk.nhs.hee.tis.trainee.forms.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "FormRPartB")
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FormRPartB extends AbstractFormR {

  private UUID programmeMembershipId;
  private Boolean isArcp;

  private String forename;
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
  private List<Work> work = new ArrayList<>();
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
  private List<Declaration> previousDeclarations = new ArrayList<>();
  private String previousDeclarationSummary;
  private Boolean haveCurrentDeclarations;
  private List<Declaration> currentDeclarations = new ArrayList<>();
  private String currentDeclarationSummary;
  private String compliments;
  private Boolean haveCovidDeclarations;
  private CovidDeclaration covidDeclaration;
  private Boolean haveCurrentUnresolvedDeclarations;
  private Boolean havePreviousUnresolvedDeclarations;

  @Override
  public String getFormType() {
    return "formr-b";
  }
}
