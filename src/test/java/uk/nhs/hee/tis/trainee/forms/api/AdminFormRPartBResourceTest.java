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

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

class AdminFormRPartBResourceTest {

  private static final UUID FORM_ID = UUID.randomUUID();

  private AdminFormRPartBResource controller;
  private FormRPartBService service;

  @BeforeEach
  void setUp() {
    service = mock(FormRPartBService.class);
    controller = new AdminFormRPartBResource(service);
  }

  @Test
  void shouldReturnNotFoundWhenDeletingAndFormNotFound() {
    when(service.partialDeleteFormRPartBById(FORM_ID)).thenReturn(Optional.empty());

    ResponseEntity<FormRPartBDto> response = controller.deleteById(FORM_ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(NOT_FOUND));
    assertThat("Unexpected response body.", response.getBody(), nullValue());
  }

  @Test
  void shouldReturnPartiallyDeletedFormWhenDeletingAndFormFound() {
    FormRPartBDto dto = new FormRPartBDto();
    dto.setId(FORM_ID.toString());

    when(service.partialDeleteFormRPartBById(FORM_ID)).thenReturn(Optional.of(dto));

    ResponseEntity<FormRPartBDto> response = controller.deleteById(FORM_ID);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), sameInstance(dto));
  }
}
