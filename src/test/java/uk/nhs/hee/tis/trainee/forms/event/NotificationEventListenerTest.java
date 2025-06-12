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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.hee.tis.trainee.forms.dto.NotificationEventDto;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

/**
 * Unit tests for {@link NotificationEventListener}.
 */
class NotificationEventListenerTest {

  private NotificationEventListener listener;
  private LtftService ltftService;

  @BeforeEach
  void setUp() {
    ltftService = mock(LtftService.class);
    listener = new NotificationEventListener(ltftService);
  }

  @Test
  void shouldUpdateTpdNotificationStatusWhenValidLtftEvent() throws IOException {
    UUID formId = UUID.randomUUID();
    NotificationEventDto event = new NotificationEventDto(
        new NotificationEventDto.TisReferenceInfo("LTFT", formId.toString()),
        "LTFT_SUBMITTED_TPD",
        "PENDING"
    );

    listener.handleTpdNotificationEvent(event);

    verify(ltftService).updateTpdNotificationStatus(formId, "PENDING");
  }

  @Test
  void shouldNotUpdateStatusWhenNotLtftTpdNotification() throws IOException {
    UUID formId = UUID.randomUUID();
    NotificationEventDto event = new NotificationEventDto(
        new NotificationEventDto.TisReferenceInfo("LTFT", formId.toString()),
        "some other type",
        "PENDING"
    );

    listener.handleTpdNotificationEvent(event);

    verifyNoInteractions(ltftService);
  }

  @Test
  void shouldNotUpdateStatusWhenNotLtftReference() throws IOException {
    UUID formId = UUID.randomUUID();
    NotificationEventDto event = new NotificationEventDto(
        new NotificationEventDto.TisReferenceInfo("OTHER", formId.toString()),
        "LTFT_SUBMITTED_TPD",
        "PENDING"
    );

    listener.handleTpdNotificationEvent(event);

    verifyNoInteractions(ltftService);
  }

  @Test
  void shouldNotUpdateStatusWhenReferenceIsNull() throws IOException {
    NotificationEventDto event = new NotificationEventDto(
        null,
        "LTFT_SUBMITTED_TPD",
        "PENDING"
    );

    listener.handleTpdNotificationEvent(event);

    verifyNoInteractions(ltftService);
  }
}
