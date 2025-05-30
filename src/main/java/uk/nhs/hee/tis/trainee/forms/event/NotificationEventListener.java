package uk.nhs.hee.tis.trainee.forms.event;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.hee.tis.trainee.forms.dto.NotificationEventDto;

import java.io.IOException;

/**
 * Listener for receiving notification events from SQS queue.
 */
@Slf4j
@Component
public class NotificationEventListener {

  /**
   * Listener for handling LTFT notification updates.
   *
   * @param event The signing event for the Conditions of Joining form.
   * @throws IOException If the Conditions of Joining could not be published.
   */
  @SqsListener("${application.aws.sqs.notification-event}")
  public void handleNotificationEvent(NotificationEventDto event) throws IOException {
    log.info("Notification event received: {}", event);
    //if it is an LTFT notification, update the form
    service.updateForm()
  }
}
