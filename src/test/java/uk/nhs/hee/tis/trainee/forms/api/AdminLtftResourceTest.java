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
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import java.util.List;
import java.util.Set;
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
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
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

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is("40"));
    assertThat("Unexpected response type.", response.getHeaders().getContentType(), is(TEXT_PLAIN));
  }

  @Test
  void shouldGetSummariesUsingStatusFilter() {
    when(service.getAdminLtftSummaries(any(), any())).thenReturn(Page.empty());

    Set<LifecycleState> states = Set.of(UNSUBMITTED, SUBMITTED);
    controller.getLtftAdminSummaries(states, null);

    ArgumentCaptor<Set<LifecycleState>> statesCaptor = ArgumentCaptor.captor();
    verify(service).getAdminLtftSummaries(statesCaptor.capture(), any());

    Set<LifecycleState> capturedStates = statesCaptor.getValue();
    assertThat("Unexpected filter state count.", capturedStates, hasSize(2));
    assertThat("Unexpected filter states.", capturedStates, hasItems(UNSUBMITTED, SUBMITTED));
  }

  @Test
  void shouldGetSummariesUsingPageable() {
    when(service.getAdminLtftSummaries(any(), any())).thenReturn(Page.empty());

    PageRequest pageable = PageRequest.of(1, 2);
    controller.getLtftAdminSummaries(Set.of(), pageable);

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
        Set.of(), null);

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    List<LtftAdminSummaryDto> content = response.getBody().getContent();
    assertThat("Unexpected response size.", content, hasSize(2));
    assertThat("Unexpected response ID.", content.get(0).id(), is(id1));
    assertThat("Unexpected response ID.", content.get(1).id(), is(id2));
  }
}
