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

package uk.nhs.hee.tis.trainee.forms.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.hee.tis.trainee.forms.dto.NotificationEventDto;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

/**
 * Listener for receiving notification events from SQS queue.
 */
@Slf4j
@Component
public class NotificationEventListener {

  LtftService service;

  /**
   * Constructor for NotificationEventListener.
   *
   * @param service The LTFT service to handle the notification events.
   */
  public NotificationEventListener(LtftService service) {
    this.service = service;
  }

  /**
   * Listener for handling emailed notification updates. Only LTFT TPD notifications are handled.
   *
   * @param event The notification event.
   * @throws IOException If the event is badly formed.
   */
  @SqsListener("${application.aws.sqs.notification-event}")
  public void handleTpdNotificationEvent(NotificationEventDto event) throws IOException {
    log.info("Notification event received: {}", event);
    if (isLtftTpdNotification(event.notificationType())
        && event.tisReference() != null && event.tisReference().type().equals("LTFT")) {
      UUID formId = UUID.fromString(event.tisReference().id());
      log.info("Updating LTFT form TPD notification status: form {}, status {}",
          formId, event.status());
      //assuming events are received in the correct order, since there is no tracking of lastRetry
      //to ensure no anachronistic updates are made.
      service.updateTpdNotificationStatus(formId, event.status());
    } else {
      log.info("Ignoring non LTFT TPD notification event.");
    }
  }

  /**
   * Check if the notification type is for LTFT TPD. At present, only the submitted event is
   * handled, not the approved or other events.
   *
   * @param notificationType The type of the notification to check.
   * @return True if the notification type is for LTFT TPD, false otherwise.
   */
  private boolean isLtftTpdNotification(String notificationType) {
    return notificationType.equalsIgnoreCase("LTFT_SUBMITTED_TPD");
  }
}
