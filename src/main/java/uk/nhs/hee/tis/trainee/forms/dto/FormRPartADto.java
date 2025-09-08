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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import org.springframework.data.annotation.Transient;
import uk.nhs.hee.tis.trainee.forms.annotations.LegalAgeValidation;
import uk.nhs.hee.tis.trainee.forms.annotations.MaxDateValidation;
import uk.nhs.hee.tis.trainee.forms.annotations.MinDateValidation;
import uk.nhs.hee.tis.trainee.forms.annotations.NotEmptyIfAnotherFieldHasValueValidation;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * A DTO for FormRPartA entity Holds the fields for the trainee's form R partA.
 */
@Data
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "declarationType",
    fieldValue = "I have been appointed to a programme leading to award of CCT",
    dependFieldName = "cctSpecialty1",
    message = "This declaration type requires a CCT specialty to be selected"
)
public class FormRPartADto {

  private String id;
  private String traineeTisId;
  private UUID programmeMembershipId;
  private Boolean isArcp;

  @Transient
  private String programmeName;

  @NotNull
  @Size(min = 1, max = 100)
  private String forename;

  @NotNull
  @Size(min = 1, max = 100)
  private String surname;

  @NotNull
  @Size(min = 1, max = 20)
  private String gmcNumber;

  @NotNull
  @Size(min = 1, max = 100)
  private String localOfficeName;

  @NotNull
  @LegalAgeValidation
  @MinDateValidation
  private LocalDate dateOfBirth;

  @NotNull
  @Size(min = 1, max = 100)
  private String gender;

  @NotNull
  @Size(min = 1, max = 200)
  private String immigrationStatus;

  @NotNull
  @Size(min = 1, max = 100)
  private String qualification;

  @NotNull
  @PastOrPresent
  @MinDateValidation
  private LocalDate dateAttained;

  @NotNull
  @Size(min = 1, max = 100)
  private String medicalSchool;

  @NotNull
  @Size(min = 1, max = 100)
  private String address1;

  @NotNull
  @Size(min = 1, max = 100)
  private String address2;

  private String address3;

  private String address4;

  @NotNull
  @Size(min = 1, max = 20)
  private String postCode;

  @NotNull
  @Pattern(regexp = "^\\+?(?:\\d\\s?){10,15}$", message = "Invalid telephone number")
  private String telephoneNumber;

  @NotNull
  @Pattern(regexp = "^\\+?(?:\\d\\s?){10,15}$", message = "Invalid telephone number")
  private String mobileNumber;

  @NotBlank
  @Email
  private String email;

  @NotNull
  private String declarationType;

  private Boolean isLeadingToCct;

  @NotNull
  @Size(min = 1, max = 100)
  private String programmeSpecialty;

  @Size(min = 0, max = 100)
  private String cctSpecialty1;

  private String cctSpecialty2;

  @NotNull
  @Size(min = 1, max = 100)
  private String college;

  @FutureOrPresent
  @MaxDateValidation
  private LocalDate completionDate;

  @NotNull
  @Size(min = 1, max = 100)
  private String trainingGrade;

  @NotNull
  @MaxDateValidation(maxYearsInFuture = 25)
  @MinDateValidation(maxYearsAgo = 25)
  private LocalDate startDate;

  @NotNull
  @Size(min = 1, max = 100)
  private String programmeMembershipType;

  @NotBlank
  @Pattern(regexp = "^((0\\.[1-9])?|(0\\.(\\d[1-9]|[1-9]\\d))|1(\\.0{1,2})?)$",
      message = "Training hours (Full Time Equivalent) needs to be a number less than or equal to "
          + "1 and greater than zero (a maximum of 2 decimal places)")
  private String wholeTimeEquivalent;

  private LocalDateTime submissionDate;

  private LocalDateTime lastModifiedDate;

  private String otherImmigrationStatus;

  private LifecycleState lifecycleState;
}
