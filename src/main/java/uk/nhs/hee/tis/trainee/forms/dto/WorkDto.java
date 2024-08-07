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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;
import uk.nhs.hee.tis.trainee.forms.annotations.MaxDateValidation;
import uk.nhs.hee.tis.trainee.forms.annotations.MinDateValidation;
import uk.nhs.hee.tis.trainee.forms.annotations.NotBeforeAnotherDateValidation;

/**
 * A DTO for work information.
 */
@Data
@NotBeforeAnotherDateValidation(
    fieldName = "startDate",
    dependFieldName = "endDate",
    message = "End date must not be before start date"
)
public class WorkDto {

  @NotNull
  @Size(min = 1, max = 100)
  private String typeOfWork;

  @NotNull
  @MaxDateValidation(maxYearsInFuture = 25)
  @MinDateValidation(maxYearsAgo = 25)
  private LocalDate startDate;

  @NotNull
  @MaxDateValidation(maxYearsInFuture = 25)
  @MinDateValidation(maxYearsAgo = 25)
  private LocalDate endDate;

  @NotNull
  @Size(min = 1, max = 100)
  private String trainingPost;

  @NotNull
  @Size(min = 1, max = 100)
  private String site;

  @NotNull
  @Size(min = 1, max = 100)
  private String siteLocation;

  @Size(max = 100)
  private String siteKnownAs;
}
