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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

class AdminLtftResourceTest {

  private AdminLtftResource controller;
  private LtftService service;
  private PdfService pdfService;

  @BeforeEach
  void setUp() {
    service = mock(LtftService.class);
    pdfService = mock(PdfService.class);
    controller = new AdminLtftResource(service, pdfService);
  }

  @Test
  void shouldGetCountUsingStatusFilter() {
    String states = String.join(",", UNSUBMITTED.name(), SUBMITTED.name());
    Map<String, String> params = Map.of("status", states);

    controller.getAdminLtftCount(params);

    ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.captor();
    verify(service).getAdminLtftCount(paramsCaptor.capture());

    Map<String, String> capturedParams = paramsCaptor.getValue();
    assertThat("Unexpected parameter count.", capturedParams.size(), is(1));
    assertThat("Unexpected status filter.", capturedParams.get("status"), is(states));
  }

  @Test
  void shouldGetCountResponse() {
    when(service.getAdminLtftCount(any())).thenReturn(40L);

    ResponseEntity<String> response = controller.getAdminLtftCount(Map.of());

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is("40"));
    assertThat("Unexpected response type.", response.getHeaders().getContentType(), is(TEXT_PLAIN));
  }

  @Test
  void shouldGetSummariesUsingStatusFilter() {
    when(service.getAdminLtftSummaries(any(), any())).thenReturn(Page.empty());

    String states = String.join(",", UNSUBMITTED.name(), SUBMITTED.name());
    Map<String, String> params = Map.of("status", states);
    controller.getLtftAdminSummaries(params, null);

    ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.captor();
    verify(service).getAdminLtftSummaries(paramsCaptor.capture(), any());

    Map<String, String> capturedParams = paramsCaptor.getValue();
    assertThat("Unexpected parameter count.", capturedParams.size(), is(1));
    assertThat("Unexpected status filter.", capturedParams.get("status"), is(states));
  }

  @Test
  void shouldGetSummariesUsingPageable() {
    when(service.getAdminLtftSummaries(any(), any())).thenReturn(Page.empty());

    PageRequest pageable = PageRequest.of(1, 2);
    controller.getLtftAdminSummaries(Map.of(), pageable);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.captor();
    verify(service).getAdminLtftSummaries(any(), pageableCaptor.capture());

    Pageable capturedPageable = pageableCaptor.getValue();
    assertThat("Unexpected page number.", capturedPageable.getPageNumber(), is(1));
    assertThat("Unexpected page size.", capturedPageable.getPageSize(), is(2));
  }

  @Test
  void shouldGetSummariesResponse() {
    UUID id1 = UUID.randomUUID();
    LtftAdminSummaryDto dto1 = LtftAdminSummaryDto.builder().id(id1).build();

    UUID id2 = UUID.randomUUID();
    LtftAdminSummaryDto dto2 = LtftAdminSummaryDto.builder().id(id2).build();

    when(service.getAdminLtftSummaries(any(), any())).thenReturn(
        new PageImpl<>(List.of(dto1, dto2)));

    ResponseEntity<PagedModel<LtftAdminSummaryDto>> response = controller.getLtftAdminSummaries(
        Map.of(), null);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    List<LtftAdminSummaryDto> content = response.getBody().getContent();
    assertThat("Unexpected response size.", content, hasSize(2));
    assertThat("Unexpected response ID.", content.get(0).id(), is(id1));
    assertThat("Unexpected response ID.", content.get(1).id(), is(id2));
  }

  @Test
  void shouldNotGetDetailJsonWhenFormNotFound() {
    UUID id = UUID.randomUUID();
    when(service.getAdminLtftDetail(id)).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.getLtftAdminDetail(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldGetDetailJsonWhenFormFound() {
    UUID id = UUID.randomUUID();
    LtftFormDto dto = LtftFormDto.builder().id(id).build();
    when(service.getAdminLtftDetail(id)).thenReturn(Optional.of(dto));

    ResponseEntity<LtftFormDto> response = controller.getLtftAdminDetail(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(dto));
  }

  @Test
  void shouldNotGetDetailPdfWhenFormNotFound() {
    UUID id = UUID.randomUUID();
    when(service.getAdminLtftDetail(id)).thenReturn(Optional.empty());

    ResponseEntity<byte[]> response = controller.getLtftAdminDetailPdf(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());

    verifyNoInteractions(pdfService);
  }

  @Test
  void shouldNotGetDetailPdfWhenPdfGenerationFails() throws IOException {
    UUID id = UUID.randomUUID();
    LtftFormDto dto = LtftFormDto.builder().id(id).build();
    when(service.getAdminLtftDetail(id)).thenReturn(Optional.of(dto));

    when(pdfService.generatePdf(dto, "admin")).thenThrow(IOException.class);

    ResponseEntity<byte[]> response = controller.getLtftAdminDetailPdf(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(UNPROCESSABLE_ENTITY));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldGetDetailPdfWhenFormFound() throws IOException {
    UUID id = UUID.randomUUID();
    LtftFormDto dto = LtftFormDto.builder().id(id).build();
    when(service.getAdminLtftDetail(id)).thenReturn(Optional.of(dto));

    byte[] body = "body".getBytes();
    when(pdfService.generatePdf(dto, "admin")).thenReturn(body);

    ResponseEntity<byte[]> response = controller.getLtftAdminDetailPdf(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(body));
  }

  @Test
  void shouldThrowExceptionWhenApprovalNotValid() throws MethodArgumentNotValidException {
    UUID id = UUID.randomUUID();
    when(service.updateStatusAsAdmin(id, APPROVED, null)).thenThrow(
        MethodArgumentNotValidException.class);

    assertThrows(MethodArgumentNotValidException.class, () -> controller.approveLtft(id));
  }

  @Test
  void shouldReturnNotFoundWhenApprovalFormNotFound() throws MethodArgumentNotValidException {
    UUID id = UUID.randomUUID();
    when(service.updateStatusAsAdmin(id, APPROVED, null)).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.approveLtft(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldReturnApprovedFormWhenFormApproved() throws MethodArgumentNotValidException {
    UUID id = UUID.randomUUID();
    LtftFormDto dto = LtftFormDto.builder().id(id).build();
    when(service.updateStatusAsAdmin(id, APPROVED, null)).thenReturn(Optional.of(dto));

    ResponseEntity<LtftFormDto> response = controller.approveLtft(id);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(dto));
  }

  @Test
  void shouldThrowExceptionWhenUnsubmitNotValid() throws MethodArgumentNotValidException {
    UUID id = UUID.randomUUID();
    LftfStatusInfoDetailDto detail = LftfStatusInfoDetailDto.builder().reason("reason").build();
    when(service.updateStatusAsAdmin(id, UNSUBMITTED, detail)).thenThrow(
        MethodArgumentNotValidException.class);

    assertThrows(MethodArgumentNotValidException.class, () -> controller.unsubmitLtft(id, detail));
  }

  @Test
  void shouldReturnNotFoundWhenUnsubmitFormNotFound() throws MethodArgumentNotValidException {
    UUID id = UUID.randomUUID();
    LftfStatusInfoDetailDto detail = LftfStatusInfoDetailDto.builder().reason("reason").build();
    when(service.updateStatusAsAdmin(id, UNSUBMITTED, detail)).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.unsubmitLtft(id, detail);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldReturnUnsubmittedFormWhenFormUnsubmitted() throws MethodArgumentNotValidException {
    UUID id = UUID.randomUUID();
    LtftFormDto dto = LtftFormDto.builder().id(id).build();
    LftfStatusInfoDetailDto detail = LftfStatusInfoDetailDto.builder().reason("reason").build();
    when(service.updateStatusAsAdmin(id, UNSUBMITTED, detail)).thenReturn(Optional.of(dto));

    ResponseEntity<LtftFormDto> response = controller.unsubmitLtft(id, detail);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(dto));
  }
}
