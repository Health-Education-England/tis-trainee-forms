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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import uk.nhs.hee.tis.trainee.forms.annotations.NotEmptyIfAnotherFieldHasValueValidation;

@Data
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "selfRateForCovid",
    fieldValue = "Satisfactory progress for stage of training and required competencies met",
    isNotCondition = true,
    dependFieldName = "reasonOfSelfRate",
    message = "Reason for self-rate is required"
)
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "haveChangesToPlacement",
    fieldValue = "true",
    dependFieldName = "changeCircumstances",
    message = "Circumstance of change is required"
)
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "haveChangesToPlacement",
    fieldValue = "true",
    dependFieldName = "howPlacementAdjusted",
    message = "How your placement was adjusted is required"
)
@NotEmptyIfAnotherFieldHasValueValidation(
    fieldName = "changeCircumstances",
    fieldValue = "Other",
    dependFieldName = "changeCircumstanceOther",
    message = "Other circumstance is required"
)
public class CovidDeclarationDto {

  @NotNull
  @Size(min = 1, max = 300)
  private String selfRateForCovid;

  //required when selfRateForCovid != satisfactory progress
  private String reasonOfSelfRate;

  @Size(max = 1000)
  private String otherInformationForPanel;

  private Boolean discussWithSupervisorChecked;
  private Boolean discussWithSomeoneChecked;

  @NotNull
  private Boolean haveChangesToPlacement;

  //required when haveChangesToPlacement = true
  private String changeCircumstances;

  //required when changeCircumstances = other
  private String changeCircumstanceOther;

  //required when haveChangesToPlacement = true
  private String howPlacementAdjusted;

  private String educationSupervisorName;

  @Email
  @Size(max = 255)
  private String educationSupervisorEmail;
}
