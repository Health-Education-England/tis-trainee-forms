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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.GoldGuideVersion.GG9;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoining;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;
import uk.nhs.hee.tis.trainee.forms.service.PdfPublisherService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

class FormEventListenerTest {

  private FormEventListener listener;
  private FormRPartAService formRPartAService;
  private FormRPartBService formRPartBService;
  private PdfPublisherService pdfPublisherService;

  @BeforeEach
  void setUp() {
    formRPartAService = mock(FormRPartAService.class);
    formRPartBService = mock(FormRPartBService.class);
    pdfPublisherService = mock(PdfPublisherService.class);
    listener = new FormEventListener(
        formRPartAService,
        formRPartBService,
        pdfPublisherService,
        new ObjectMapper()
    );
  }

  @Test
  void shouldPublishConditionsOfJoining() throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(GG9, Instant.now());
    ConditionsOfJoiningSignedEvent event = new ConditionsOfJoiningSignedEvent("40",
        UUID.randomUUID(), "progName", conditionsOfJoining);

    listener.handleCojReceivedEvent(event);

    verify(pdfPublisherService).publishConditionsOfJoining(event);
  }

  @Test
  void shouldThrowExceptionPublishingConditionsOfJoiningThrowsException() throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(GG9, Instant.now());
    ConditionsOfJoiningSignedEvent event = new ConditionsOfJoiningSignedEvent("40",
        UUID.randomUUID(), "progName", conditionsOfJoining);

    doThrow(IOException.class).when(pdfPublisherService).publishConditionsOfJoining(any());

    assertThrows(IOException.class, () -> listener.handleCojReceivedEvent(event));
  }

  @Test
  void shouldPartialDeleteFormA() {
    final String message = """
        {
        "deleteType": "PARTIAL",
        "bucket": "document-upload",
        "key": "1/forms/formr-a/1000a.json",
        "fixedFields": ["id", "traineeTisId"]
        }
        """;

    listener.handleFormDeleteEvent(message);

    verify(formRPartAService).partialDeleteFormRPartAById(
        "1000a", "1", Set.of("id", "traineeTisId"));
  }

  @Test
  void shouldPartialDeleteFormB() {
    final String message = """
        {
        "deleteType": "PARTIAL",
        "bucket": "document-upload",
        "key": "1/forms/formr-b/2000b.json",
        "fixedFields": ["id", "traineeTisId"]
        }
        """;

    listener.handleFormDeleteEvent(message);

    verify(formRPartBService).partialDeleteFormRPartBById(
        "2000b", "1", Set.of("id", "traineeTisId"));
  }

  @Test
  void shouldNotPartialDeleteFormsIfFormNameNotMatch() {
    final String message = """
        {
        "deleteType": "PARTIAL",
        "bucket": "document-upload",
        "key": "1/forms/formr-c/1000a.json",
        "fixedFields": ["id", "traineeTisId"]
        }
        """;

    listener.handleFormDeleteEvent(message);

    verifyNoInteractions(formRPartAService);
    verifyNoInteractions(formRPartBService);
  }

  @Test
  void shouldThrowExceptionWhenDeleteTypeNotPartial() {
    final String message = """
        {
        "deleteType": "HARD",
        "bucket": "document-upload",
        "key": "1/forms/formr-a/1000a.json",
        "fixedFields": ["id", "traineeTisId"]
        }
        """;

    verifyNoInteractions(formRPartAService);
    verifyNoInteractions(formRPartBService);
    assertThrows(ApplicationException.class, () -> listener.handleFormDeleteEvent(message));
  }

  @Test
  void shouldThrowExceptionWhenFailToPartialDeleteForm() {
    final String message = """
        {
        "deleteType": "HARD",
        "bucket": "document-upload",
        "key": "1/forms/formr-a/1000a.json",
        "fixedFields": ["id", "traineeTisId"]
        }
        """;

    doThrow(ApplicationException.class)
        .when(formRPartAService).partialDeleteFormRPartAById(any(), any(), any());
    assertThrows(ApplicationException.class, () -> listener.handleFormDeleteEvent(message));
  }
}
