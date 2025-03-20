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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.StatusInfoDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

class LtftResourceTest {

  private static final UUID ID = UUID.randomUUID();

  private LtftResource controller;
  private LtftService service;

  @BeforeEach
  void setUp() {
    service = mock(LtftService.class);
    controller = new LtftResource(service);
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
    when(service.saveLtftForm(any())).thenReturn(Optional.empty());

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
    when(service.saveLtftForm(any())).thenReturn(Optional.of(savedForm));

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
  void postShouldReturnBadRequestWhenServiceFailToSaveLtftWhenSubmitForm() {
    LtftFormDto formDto = buildLtftFormDto();
    when(service.saveLtftForm(any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.submitNewLtft(formDto);

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void postShouldReturnBadRequestWhenServiceFailToSubmitLtftForm() {
    LtftFormDto formDto = buildLtftFormDto();
    when(service.submitLtftForm(any(), any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.submitNewLtft(formDto);

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void postShouldReturnSubmittedLtftFormWhenSubmitted() {
    LtftFormDto formDto = buildLtftFormDto();
    when(service.saveLtftForm(any())).thenReturn(Optional.of(formDto));
    when(service.submitLtftForm(any(), any())).thenReturn(Optional.of(formDto));

    ResponseEntity<LtftFormDto> response = controller.submitNewLtft(formDto);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    LtftFormDto responseDto = response.getBody();
    assertThat("Unexpected response body.", responseDto, is(formDto));
  }

  @Test
  void putShouldReturnBadRequestWhenServiceFailToUpdateLtftWhenSubmitForm() {
    LtftFormDto existingForm = buildLtftFormDto();
    when(service.updateLtftForm(any(), any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.submitLtft(ID, existingForm);

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void putShouldReturnBadRequestWhenServiceFailToSubmitLtftForm() {
    LtftFormDto existingForm = buildLtftFormDto();
    when(service.submitLtftForm(any(), any())).thenReturn(Optional.empty());

    ResponseEntity<LtftFormDto> response = controller.submitLtft(ID, existingForm);

    assertThat("Unexpected response code.", response.getStatusCode(), is(BAD_REQUEST));
  }

  @Test
  void putShouldReturnSubmittedLtftFormWhenSubmitted() {
    LtftFormDto existingForm = buildLtftFormDto();
    when(service.updateLtftForm(any(), any())).thenReturn(Optional.of(existingForm));
    when(service.submitLtftForm(any(), any())).thenReturn(Optional.of(existingForm));

    ResponseEntity<LtftFormDto> response = controller.submitLtft(ID, existingForm);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    LtftFormDto responseDto = response.getBody();
    assertThat("Unexpected response body.", responseDto, is(existingForm));
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

  private LtftFormDto buildLtftFormDto() {
    LftfStatusInfoDetailDto detailsDto = LftfStatusInfoDetailDto.builder()
        .reason("some reason")
        .message("some message")
        .build();
    StatusInfoDto currentDto = StatusInfoDto.builder()
        .detail(detailsDto)
        .build();
    StatusDto statusDto = StatusDto.builder()
        .current(currentDto)
        .build();
    return LtftFormDto.builder()
        .id(ID)
        .traineeTisId("some trainee")
        .status(statusDto)
        .build();
  }
}
