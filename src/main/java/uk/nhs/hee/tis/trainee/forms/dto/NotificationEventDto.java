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

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;
import lombok.Builder;

/**
 * A notification event DTO.

 * @param tisReference     The TIS record type and id that prompted the notification.
 * @param notificationType The type of notification, e.g. "LTFT_APPROVED", "FORM_UPDATED".
 * @param sentAt           The timestamp when the notification was sent.
 * @param status           The status of the notification, e.g. "SENT", "FAILED".
 * @param statusDetail     Any additional detail about failed statuses, e.g. "On suppression list".
 * @param lastRetry        The timestamp of the last retry attempt for the notification, if
 *                         applicable.
 */

@Builder
public record NotificationEventDto(
    TisReferenceInfo tisReference,
    @JsonAlias("type")
    String notificationType,
    Instant sentAt,
    String status,
    String statusDetail,
    Instant lastRetry
) {

  /**
   * A representation of the TIS record that prompted the notification.
   *
   * @param type The TIS reference type for the entity that prompted the notification.
   * @param id   The TIS ID of the entity that prompted the notification.
   */
  public record TisReferenceInfo(String type, String id) {

  }
}
