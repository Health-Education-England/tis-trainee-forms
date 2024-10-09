/*
 * The MIT License (MIT).
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.hee.tis.trainee.forms.dto.DeleteEventDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.DeleteType;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;
import uk.nhs.hee.tis.trainee.forms.service.PdfPublisherService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

/**
 * Listener for receiving form events from SQS queue.
 */
@Slf4j
@Component
public class FormEventListener {

  private final FormRPartAService formRPartAService;
  private final FormRPartBService formRPartBService;
  private final PdfPublisherService pdfPublisherService;
  private final ObjectMapper objectMapper;

  FormEventListener(FormRPartAService formRPartAService,
      FormRPartBService formRPartBService, PdfPublisherService pdfPublisherService,
      ObjectMapper objectMapper) {
    this.formRPartAService = formRPartAService;
    this.formRPartBService = formRPartBService;
    this.pdfPublisherService = pdfPublisherService;
    this.objectMapper = objectMapper;
  }

  /**
   * Listener for handling signed Conditions of Joining being received.
   *
   * @param event The signing event for the Conditions of Joining form.
   * @throws IOException If the Conditions of Joining could not be published.
   */
  @SqsListener("${application.aws.sqs.coj-received}")
  public void handleCojReceivedEvent(ConditionsOfJoiningSignedEvent event) throws IOException {
    log.info("Signed Conditions of Joining received: {}", event);
    pdfPublisherService.publishConditionsOfJoining(event);
  }

  /**
   * Listener for handling form delete form event.
   */
  @SqsListener("${application.aws.sqs.delete-event}")
  public void handleFormDeleteEvent(String message) {
    try {
      log.info("Form delete event received: {}", message);
      DeleteEventDto deleteEvent = objectMapper.readValue(message, DeleteEventDto.class);

      if (deleteEvent.getDeleteType() == DeleteType.PARTIAL) {
        final String[] eventDetails =
            deleteEvent.getKey().split("/");
        final String formId = eventDetails[3].split(".json")[0];
        final String traineeTisId = eventDetails[0];
        final String[] fixFields = deleteEvent.getFixedFields();

        switch (eventDetails[2]) {
          case "formr-a":
            formRPartAService.partialDeleteFormRPartAById(formId, traineeTisId, Set.of(fixFields));
            break;
          case "formr-b":
            formRPartBService.partialDeleteFormRPartBById(formId, traineeTisId, Set.of(fixFields));
            break;
          default:
            log.error("Unknown form type: {}", eventDetails[2]);
        }
      } else {
        log.error("Unexpected deleteType of form: {}" + deleteEvent.getDeleteType());
        throw new ApplicationException("Unexpected deleteType of form: "
            + deleteEvent.getDeleteType());
      }
    } catch (Exception e) {
      log.error("Fail to delete form: {}", e);
      throw new ApplicationException("Fail to delete form:", e);
    }
  }
}
