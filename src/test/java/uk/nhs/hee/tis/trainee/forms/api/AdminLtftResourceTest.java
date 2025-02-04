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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

class AdminLtftResourceTest {

  private AdminLtftResource controller;
  private LtftService service;

  @BeforeEach
  void setUp() {
    service = mock(LtftService.class);
    controller = new AdminLtftResource(service);
  }

  @Test
  void shouldGetCountUsingStatusFilter() {
    Set<LifecycleState> states = Set.of(UNSUBMITTED, SUBMITTED);

    controller.getAdminLtftCount(states);

    ArgumentCaptor<Set<LifecycleState>> statesCaptor = ArgumentCaptor.captor();
    verify(service).getAdminLtftCount(statesCaptor.capture());

    Set<LifecycleState> capturedStates = statesCaptor.getValue();
    assertThat("Unexpected filter state count.", capturedStates, hasSize(2));
    assertThat("Unexpected filter states.", capturedStates, hasItems(UNSUBMITTED, SUBMITTED));
  }

  @Test
  void shouldGetCountResponse() {
    when(service.getAdminLtftCount(any())).thenReturn(40L);

    ResponseEntity<String> response = controller.getAdminLtftCount(Set.of());

    assertThat("Unexpected response code.", response.getStatusCode().value(), is(200));
    assertThat("Unexpected response body.", response.getBody(), is("40"));
    assertThat("Unexpected response type.", response.getHeaders().getContentType(), is(TEXT_PLAIN));
  }
}
