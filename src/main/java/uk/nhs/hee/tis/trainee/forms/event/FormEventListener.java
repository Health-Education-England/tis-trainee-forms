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

import io.awspring.cloud.sqs.annotation.SqsListener;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoiningPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

/**
 * Listener for receiving form events from SQS queue.
 */
@Slf4j
@Component
public class FormEventListener {

  private final PdfService pdfService;

  FormEventListener(PdfService pdfService) {
    this.pdfService = pdfService;
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
    ConditionsOfJoiningPdfRequestDto request = new ConditionsOfJoiningPdfRequestDto(
        event.traineeId(), event.programmeMembershipId(), event.programmeName(),
        event.conditionsOfJoining());
    pdfService.generateConditionsOfJoining(request, true);
  }
}
