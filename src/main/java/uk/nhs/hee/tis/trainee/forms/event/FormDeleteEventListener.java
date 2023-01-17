/*
 * The MIT License (MIT)
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
import io.awspring.cloud.messaging.listener.annotation.SqsListener;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.hee.tis.trainee.forms.dto.DeleteEventDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.DeleteType;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@Slf4j
@Component
public class FormDeleteEventListener {

  private final FormRPartAService formRPartAService;
  private final FormRPartBService formRPartBService;

  FormDeleteEventListener(FormRPartAService formRPartAService,
      FormRPartBService formRPartBService) {
    this.formRPartAService = formRPartAService;
    this.formRPartBService = formRPartBService;
  }

  /**
   * Listener for receiving form delete event from SQS queue.
   */
  @SqsListener("${application.aws.sqs.delete-event}")
  public void getFormDeleteEvent(String message) throws IOException {
    try {
      log.info("Form delete event received: {}", message);
      ObjectMapper objectMapper = new ObjectMapper();
      DeleteEventDto deleteEvent = objectMapper.readValue(message, DeleteEventDto.class);


      if (deleteEvent.getDeleteType() == DeleteType.PARTIAL) {
        final var eventDetails =
            deleteEvent.getKey().split("/");
        final var formId = eventDetails[3].split(".json")[0];
        final var traineeTisId = eventDetails[0];
        final var fixFields = deleteEvent.getFixedFields();

        if (eventDetails[2].equals("formr-a")) {
          formRPartAService.partialDeleteFormRPartAById(formId, traineeTisId, fixFields);
          log.info("Partial delete successfully for trainee {} with form Id {} (FormRPartA)",
              traineeTisId, formId);
        } else if (eventDetails[2].equals("formr-b")) {
          log.info("trainee: {}, Form B", eventDetails[0]);
          formRPartBService.partialDeleteFormRPartBById(formId, traineeTisId, fixFields);
          log.info("Partial delete successfully for trainee {} with form Id {} (FormRPartB)",
              traineeTisId, formId);
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
