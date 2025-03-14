/*
 * The MIT License (MIT)
 *
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
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "FormRPartA")
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FormRPartA extends AbstractFormR {

  private UUID programmeMembershipId;
  private Boolean isArcp;

  private String forename;
  private String surname;
  private String gmcNumber;
  private String localOfficeName;
  private LocalDate dateOfBirth;
  private String gender;
  private String immigrationStatus;
  private String qualification;
  private LocalDate dateAttained;
  private String medicalSchool;
  private String address1;
  private String address2;
  private String address3;
  private String address4;
  private String postCode;
  private String telephoneNumber;
  private String mobileNumber;
  private String email;
  private String declarationType;
  private Boolean isLeadingToCct;
  private String programmeSpecialty;
  private String cctSpecialty1;
  private String cctSpecialty2;
  private String college;
  private LocalDate completionDate;
  private String trainingGrade;
  private LocalDate startDate;
  private String programmeMembershipType;
  private String wholeTimeEquivalent;
  private String otherImmigrationStatus;

  @Override
  public String getFormType() {
    return "formr-a";
  }
}
