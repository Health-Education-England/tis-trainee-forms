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

package uk.nhs.hee.tis.trainee.forms.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

class LtftResourceTest {

  private static final UUID ID = UUID.randomUUID();

  private LtftResource controller;
  private LtftService service;
  private PdfService pdfService;

  @BeforeEach
  void setUp() {
    service = mock(LtftService.class);
    pdfService = mock(PdfService.class);
    controller = new LtftResource(service, pdfService);
  }

  @Test
  void shouldNotGetLtftSummariesWhenLtftFormsNotExist() {
    when(service.getLtftSummaries()).thenReturn(List.of());

    ResponseEntity<List<LtftSummaryDto>> response = controller.getLtftSummaries();

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is(List.of()));
  }

  @Test
  void shouldGetLtftSummariesWhenLtftFormsExist() {
    UUID id1 = UUID.randomUUID();
    LtftSummaryDto dto1 = LtftSummaryDto.builder()
        .id(id1)
        .name("Test LTFT 1")
        .build();
    UUID id2 = UUID.randomUUID();
    LtftSummaryDto dto2 = LtftSummaryDto.builder()
        .id(id2)
        .name("Test LTFT 2")
        .build();
    when(service.getLtftSummaries()).thenReturn(List.of(dto1, dto2));

    ResponseEntity<List<LtftSummaryDto>> response = controller.getLtftSummaries();

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));

    List<LtftSummaryDto> responseDtos = response.getBody();
    assertThat("Unexpected response DTO count.", responseDtos, hasSize(2));

    LtftSummaryDto responseDto1 = responseDtos.get(0);
    assertThat("Unexpected ID.", responseDto1.id(), is(id1));
    assertThat("Unexpected name.", responseDto1.name(), is("Test LTFT 1"));

    LtftSummaryDto responseDto2 = responseDtos.get(1);
    assertThat("Unexpected ID.", responseDto2.id(), is(id2));
    assertThat("Unexpected name.", responseDto2.name(), is("Test LTFT 2"));
  }

  @Test
  void shouldReturnBadRequestWhenServiceWontSaveLtftForm() {
    when(service.createLtftForm(any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.createLtft(LtftFormDto.builder().build());

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void shouldReturnBadRequestWhenServiceWontUpdateLtftForm() {
    when(service.updateLtftForm(any(), any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.updateLtft(ID, LtftFormDto.builder().build());

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void shouldReturnNotFoundWhenServiceCantFindLtftForm() {
    when(service.getLtftForm(any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.getLtft(ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
  }

  @Test
  void shouldReturnSavedLtftFormWhenSaved() {
    LtftFormDto savedForm = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("some trainee")
        .build();
    when(service.createLtftForm(any())).thenReturn(Optional.of(savedForm));

    LtftFormDto newForm = LtftFormDto.builder()
        .traineeTisId("some trainee")
        .build();
    ResponseEntity<LtftFormDto> response = controller.createLtft(newForm);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    LtftFormDto responseDto = response.getBody();
    assert responseDto != null;
    assertThat("Unexpected response body.", responseDto, is(savedForm));
  }

  @Test
  void shouldReturnSavedLtftFormWhenUpdated() {
    LtftFormDto savedForm = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("some trainee")
        .build();
    when(service.updateLtftForm(any(), any())).thenReturn(Optional.of(savedForm));

    LtftFormDto existingForm = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("some trainee")
        .build();
    ResponseEntity<LtftFormDto> response = controller.updateLtft(ID, existingForm);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    LtftFormDto responseDto = response.getBody();
    assert responseDto != null;
    assertThat("Unexpected response body.", responseDto.equals(savedForm), is(true));
  }

  @Test
  void shouldReturnLtftFormWhenFound() {
    LtftFormDto existingForm = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("some trainee")
        .build();
    when(service.getLtftForm(ID)).thenReturn(Optional.of(existingForm));

    ResponseEntity<LtftFormDto> response = controller.getLtft(ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    LtftFormDto responseDto = response.getBody();
    assert responseDto != null;
    assertThat("Unexpected response body.", responseDto.equals(existingForm), is(true));
  }

  @Test
  void shouldReturnNotFoundWhenServiceCantFindLtftFormToDelete() {
    when(service.deleteLtftForm(any())).thenReturn(Optional.empty());

    ResponseEntity<Void> response = controller.deleteLtft(ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
  }

  @Test
  void shouldReturnBadRequestWhenServiceCantDeleteLtftForm() {
    when(service.deleteLtftForm(any())).thenReturn(Optional.of(false));

    ResponseEntity<Void> response = controller.deleteLtft(ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void shouldReturnOkWhenServiceDeletesLtftForm() {
    when(service.deleteLtftForm(any())).thenReturn(Optional.of(true));

    ResponseEntity<Void> response = controller.deleteLtft(ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
  }

  @Test
  void shouldReturnBadRequestWhenServiceWontSubmitLtftForm() {
    when(service.submitLtftForm(any(), any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.submitLtft(ID, null);

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void shouldReturnSubmittedLtftFormWhenSubmitted() {
    LtftFormDto form = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("some trainee")
        .build();
    when(service.submitLtftForm(any(), any())).thenReturn(Optional.of(form));

    ResponseEntity<LtftFormDto> response = controller.submitLtft(ID, null);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    LtftFormDto responseDto = response.getBody();
    assertThat("Unexpected response body.", responseDto, is(form));
  }

  @Test
  void shouldReturnBadRequestWhenServiceWontUnsubmitLtftForm() {
    when(service.unsubmitLtftForm(any(), any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.unsubmitLtft(ID, null);

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void shouldReturnUnsubmittedLtftFormWhenUnsubmitted() {
    LtftFormDto form = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("some trainee")
        .build();
    when(service.unsubmitLtftForm(any(), any())).thenReturn(Optional.of(form));

    ResponseEntity<LtftFormDto> response = controller.unsubmitLtft(ID, null);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    LtftFormDto responseDto = response.getBody();
    assertThat("Unexpected response body.", responseDto, is(form));
  }

  @Test
  void shouldReturnBadRequestWhenServiceWontWithdrawLtftForm() {
    when(service.withdrawLtftForm(any(), any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.withdrawLtft(ID, null);

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void shouldReturnWithdrawnLtftFormWhenWithdrawn() {
    LtftFormDto form = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("some trainee")
        .build();
    when(service.withdrawLtftForm(any(), any())).thenReturn(Optional.of(form));

    ResponseEntity<LtftFormDto> response = controller.withdrawLtft(ID, null);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    LtftFormDto responseDto = response.getBody();
    assertThat("Unexpected response body.", responseDto, is(form));
  }

  @Test
  void shouldNotGetDetailPdfWhenFormNotFound() {
    UUID id = UUID.randomUUID();
    when(service.getLtftForm(id)).thenReturn(Optional.empty());

    ResponseEntity<byte[]> response = controller.getLtftPdf(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());

    verifyNoInteractions(pdfService);
  }

  @Test
  void shouldNotGetDetailPdfWhenPdfGenerationFails() throws IOException {
    UUID id = UUID.randomUUID();
    LtftFormDto dto = LtftFormDto.builder().id(id).build();
    when(service.getLtftForm(id)).thenReturn(Optional.of(dto));

    when(pdfService.generatePdf(dto, "trainee")).thenThrow(IOException.class);

    ResponseEntity<byte[]> response = controller.getLtftPdf(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldGetDetailPdfWhenFormFound() throws IOException {
    UUID id = UUID.randomUUID();
    LtftFormDto dto = LtftFormDto.builder().id(id).build();
    when(service.getLtftForm(id)).thenReturn(Optional.of(dto));

    byte[] body = "body".getBytes();
    when(pdfService.generatePdf(dto, "trainee")).thenReturn(body);

    ResponseEntity<byte[]> response = controller.getLtftPdf(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(body));
  }
}
