/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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
import java.util.UUID;
import lombok.Builder;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * A DTO for an admin-focused LTFT summary record.
 *
 * @param id                The identifier of the LTFT application.
 * @param personalDetails   The personal details of the applicant.
 * @param programmeName     The programme name associated with the LTFT application.
 * @param proposedStartDate The proposed start date of the LTFT change.
 * @param submissionDate    The date the LTFT application was submitted.
 * @param reason            The reason given for applying for LTFT.
 * @param daysToStart       How many days until the start of the LTFT change.
 * @param shortNotice       Whether the LTFT application was submitted at short notice.
 * @param tpd               The details of the notification sent to the TPD.
 * @param status            The current status of the LTFT application.
 * @param assignedAdmin     The admin assigned to process the LTFT application.
 */
@Builder
public record LtftAdminSummaryDto(
    UUID id,
    LtftAdminPersonalDetailsDto personalDetails,
    String programmeName,
    LocalDate proposedStartDate,
    LocalDate submissionDate,
    String reason,
    Integer daysToStart,
    Boolean shortNotice,
    LtftAdminNotificationDto tpd,
    LifecycleState status,
    PersonDto assignedAdmin) {

  /**
   * Trainee personal details for the admin view.
   */
  @Builder
  public record LtftAdminPersonalDetailsDto(
      String id,
      String forenames,
      String surname,
      String gmcNumber,
      String gdcNumber) {

  }

  /**
   * The details of a notified person.
   *
   * @param email       The email the notification was sent to.
   * @param emailStatus The current status of the email, if known.
   */
  @Builder
  public record LtftAdminNotificationDto(String email, String emailStatus) {

  }
}
