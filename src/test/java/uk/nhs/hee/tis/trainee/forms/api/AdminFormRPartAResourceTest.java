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
 *
 */

package uk.nhs.hee.tis.trainee.forms.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;

class AdminFormRPartAResourceTest {

  private static final UUID FORM_ID = UUID.randomUUID();

  private AdminFormRPartAResource controller;
  private FormRPartAService service;

  @BeforeEach
  void setUp() {
    service = mock(FormRPartAService.class);
    controller = new AdminFormRPartAResource(service);
  }

  @Test
  void shouldReturnNotFoundWhenUnsubmittingAndFormNotFound() {
    when(service.unsubmitFormRPartAById(FORM_ID)).thenReturn(Optional.empty());

    ResponseEntity<FormRPartADto> response = controller.unsubmitFormRPartA(FORM_ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldReturnUnsubmittedFormWhenUnsubmittingAndFormFound() {
    FormRPartADto dto = new FormRPartADto();
    dto.setId(FORM_ID.toString());

    when(service.unsubmitFormRPartAById(FORM_ID)).thenReturn(Optional.of(dto));

    ResponseEntity<FormRPartADto> response = controller.unsubmitFormRPartA(FORM_ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(dto));
  }

  @Test
  void shouldReturnNotFoundWhenDeletingAndFormNotFound() {
    when(service.partialDeleteFormRPartAById(FORM_ID)).thenReturn(Optional.empty());

    ResponseEntity<FormRPartADto> response = controller.deleteById(FORM_ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldReturnPartiallyDeletedFormWhenDeletingAndFormFound() {
    FormRPartADto dto = new FormRPartADto();
    dto.setId(FORM_ID.toString());

    when(service.partialDeleteFormRPartAById(FORM_ID)).thenReturn(Optional.of(dto));

    ResponseEntity<FormRPartADto> response = controller.deleteById(FORM_ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(dto));
  }

  @Test
  void shouldReturnTraineeFormsWhenGetTraineeFormRPartAs() {
    String traineeId = "12345";
    FormRPartSimpleDto simpleDto1 = new FormRPartSimpleDto();
    simpleDto1.setId(FORM_ID.toString());
    simpleDto1.setTraineeTisId(traineeId);

    FormRPartSimpleDto simpleDto2 = new FormRPartSimpleDto();
    simpleDto2.setId(UUID.randomUUID().toString());
    simpleDto2.setTraineeTisId(traineeId);

    List<FormRPartSimpleDto> forms = Arrays.asList(simpleDto1, simpleDto2);

    when(service.getFormRPartAs(traineeId)).thenReturn(forms);

    ResponseEntity<List<FormRPartSimpleDto>> response = controller.getTraineeFormRPartAs(traineeId);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(forms));
    assertThat("Unexpected number of forms.", response.getBody().size(), is(2));
  }

  @Test
  void shouldReturnEmptyListWhenGetTraineeFormRPartAsWithNoForms() {
    String traineeId = "12345";
    List<FormRPartSimpleDto> emptyList = Collections.emptyList();

    when(service.getFormRPartAs(traineeId)).thenReturn(emptyList);

    ResponseEntity<List<FormRPartSimpleDto>> response = controller.getTraineeFormRPartAs(traineeId);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(emptyList));
    assertThat("Unexpected number of forms.", response.getBody().size(), is(0));
  }

  @Test
  void shouldReturnNotFoundWhenGetFormRPartAsByIdAndFormNotFound() {
    when(service.getFormRPartAById(FORM_ID.toString())).thenReturn(null);

    ResponseEntity<FormRPartADto> response = controller.getFormRPartAsById(FORM_ID.toString());

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldReturnFormWhenGetFormRPartAsByIdAndFormFound() {
    FormRPartADto dto = new FormRPartADto();
    dto.setId(FORM_ID.toString());
    dto.setTraineeTisId("12345");

    when(service.getFormRPartAById(FORM_ID.toString())).thenReturn(dto);

    ResponseEntity<FormRPartADto> response = controller.getFormRPartAsById(FORM_ID.toString());

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(dto));
    assertThat("Unexpected form ID.", response.getBody().getId(), is(FORM_ID.toString()));
  }
}
