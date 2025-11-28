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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.GoldGuideVersion.GG9;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoining;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoiningPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

class FormEventListenerTest {

  private FormEventListener listener;
  private PdfService pdfService;

  @BeforeEach
  void setUp() {
    pdfService = mock(PdfService.class);
    listener = new FormEventListener(pdfService);
  }

  @Test
  void shouldPublishConditionsOfJoining() throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(GG9, Instant.now());
    UUID programmeMembershipId = UUID.randomUUID();
    ConditionsOfJoiningSignedEvent event = new ConditionsOfJoiningSignedEvent("40",
        programmeMembershipId, "progName", conditionsOfJoining);

    listener.handleCojReceivedEvent(event);

    ArgumentCaptor<ConditionsOfJoiningPdfRequestDto> requestCaptor = ArgumentCaptor.captor();
    verify(pdfService).generateConditionsOfJoining(requestCaptor.capture(), eq(true));

    ConditionsOfJoiningPdfRequestDto request = requestCaptor.getValue();
    assertThat("Unexpected trainee ID.", request.traineeId(), is("40"));
    assertThat("Unexpected programme membership ID.", request.programmeMembershipId(),
        is(programmeMembershipId));
    assertThat("Unexpected programme name.", request.programmeName(), is("progName"));
    assertThat("Unexpected conditions of joining.", request.conditionsOfJoining(),
        is(conditionsOfJoining));
  }

  @Test
  void shouldThrowExceptionPublishingConditionsOfJoiningThrowsException() throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(GG9, Instant.now());
    ConditionsOfJoiningSignedEvent event = new ConditionsOfJoiningSignedEvent("40",
        UUID.randomUUID(), "progName", conditionsOfJoining);

    doThrow(IOException.class).when(pdfService).generateConditionsOfJoining(any(), eq(true));

    assertThrows(IOException.class, () -> listener.handleCojReceivedEvent(event));
  }
}
