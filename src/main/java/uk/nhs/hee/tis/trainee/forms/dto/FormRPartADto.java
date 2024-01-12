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

package uk.nhs.hee.tis.trainee.forms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.validation.constraints.Email;
import javax.validation.constraints.FutureOrPresent;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PastOrPresent;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Data;
import uk.nhs.hee.tis.trainee.forms.annotations.LegalAgeValidation;
import uk.nhs.hee.tis.trainee.forms.annotations.MaxDateValidation;
import uk.nhs.hee.tis.trainee.forms.annotations.MinDateValidation;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * A DTO for FormRPartA entity Holds the fields for the trainee's form R partA.
 */
@Data
public class FormRPartADto {

  private String id;
  private String traineeTisId;
  @Size(min = 1, max = 100)
  private String forename;
  @Size(min = 1, max = 100)
  private String surname;
  @Size(min = 1, max = 100)
  private String gmcNumber;
  @Size(min = 1, max = 100)
  private String localOfficeName;
  @LegalAgeValidation
  @MinDateValidation
  private LocalDate dateOfBirth;
  @Size(min = 1, max = 100)
  private String gender;
  @Size(min = 1, max = 200)
  private String immigrationStatus;
  @Size(min = 1, max = 100)
  private String qualification;
  @PastOrPresent
  @MinDateValidation
  private LocalDate dateAttained;
  @Size(min = 1, max = 100)
  private String medicalSchool;
  @Size(min = 1, max = 100)
  private String address1;
  @Size(min = 1, max = 100)
  private String address2;
  private String address3;
  private String address4;
  @Size(min = 1, max = 20)
  private String postCode;
  @Pattern(regexp = "^\\+?(?:\\d\\s?){10,15}$", message = "Invalid telephone number")
  private String telephoneNumber;
  @Pattern(regexp = "^\\+?(?:\\d\\s?){10,15}$", message = "Invalid telephone number")
  private String mobileNumber;
  @Email
  private String email;
  @NotNull
  private String declarationType;
  private Boolean isLeadingToCct;
  @Size(min = 1, max = 100)
  private String programmeSpecialty;
  @Size(min = 1, max = 100)
  private String cctSpecialty1;
  private String cctSpecialty2;
  @Size(min = 1, max = 100)
  private String college;
  @FutureOrPresent
  @MaxDateValidation
  private LocalDate completionDate;
  @Size(min = 1, max = 100)
  private String trainingGrade;
  @MaxDateValidation(maxYearsInFuture = 25)
  @MinDateValidation(maxYearsAgo = 25)
  private LocalDate startDate;
  @Size(min = 1, max = 100)
  private String programmeMembershipType;
  @Pattern(regexp = "^((0\\.[1-9])?|(0\\.(\\d[1-9]|[1-9]\\d))|1(\\.0{1,2})?)$",
      message = "Training hours (Full Time Equivalent) needs to be a number less than or equal to "
          + "1 and greater than zero (a maximum of 2 decimal places)")
  private String wholeTimeEquivalent;
  private LocalDateTime submissionDate;
  private LocalDateTime lastModifiedDate;
  private String otherImmigrationStatus;
  private LifecycleState lifecycleState;
}
