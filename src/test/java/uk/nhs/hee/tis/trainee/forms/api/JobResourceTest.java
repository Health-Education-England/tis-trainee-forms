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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.OK;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import uk.nhs.hee.tis.trainee.forms.job.PublishFormrPartaRefresh;
import uk.nhs.hee.tis.trainee.forms.job.PublishFormrPartbRefresh;
import uk.nhs.hee.tis.trainee.forms.job.PublishLtftRefresh;

class JobResourceTest {

  private JobResource controller;

  private PublishFormrPartaRefresh publishFormrPartaRefreshJob;
  private PublishFormrPartbRefresh publishFormrPartbRefreshJob;
  private PublishLtftRefresh publishLtftRefreshJob;

  @BeforeEach
  void setUp() {
    publishFormrPartaRefreshJob = mock(PublishFormrPartaRefresh.class);
    publishFormrPartbRefreshJob = mock(PublishFormrPartbRefresh.class);
    publishLtftRefreshJob = mock(PublishLtftRefresh.class);
    controller = new JobResource(publishFormrPartaRefreshJob, publishFormrPartbRefreshJob,
        publishLtftRefreshJob);
  }

  @Test
  void shouldPublishFormrPartaRefreshWithNoDate() {
    when(publishFormrPartaRefreshJob.execute(Optional.empty())).thenReturn(3);

    ResponseEntity<Integer> response = controller.publishFormrPartaRefresh(Optional.empty());

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is(3));
  }

  @Test
  void shouldPublishFormrPartaRefreshWithDate() {
    LocalDate since = LocalDate.of(2025, 1, 1);
    when(publishFormrPartaRefreshJob.execute(Optional.of(since))).thenReturn(2);

    ResponseEntity<Integer> response = controller.publishFormrPartaRefresh(Optional.of(since));

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is(2));
  }

  @Test
  void shouldPublishFormrPartbRefreshWithNoDate() {
    when(publishFormrPartbRefreshJob.execute(Optional.empty())).thenReturn(4);

    ResponseEntity<Integer> response = controller.publishFormrPartbRefresh(Optional.empty());

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is(4));
  }

  @Test
  void shouldPublishFormrPartbRefreshWithDate() {
    LocalDate since = LocalDate.of(2025, 1, 1);
    when(publishFormrPartbRefreshJob.execute(Optional.of(since))).thenReturn(2);

    ResponseEntity<Integer> response = controller.publishFormrPartbRefresh(Optional.of(since));

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is(2));
  }

  @Test
  void shouldPublishLtftRefreshWithNoDate() {
    when(publishLtftRefreshJob.execute(Optional.empty())).thenReturn(5);

    ResponseEntity<Integer> response = controller.publishLtftRefresh(Optional.empty());

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is(5));
  }

  @Test
  void shouldPublishLtftRefreshWithDate() {
    LocalDate since = LocalDate.of(2025, 1, 1);
    when(publishLtftRefreshJob.execute(Optional.of(since))).thenReturn(3);

    ResponseEntity<Integer> response = controller.publishLtftRefresh(Optional.of(since));

    assertThat("Unexpected response code.", response.getStatusCode(), is(OK));
    assertThat("Unexpected response body.", response.getBody(), is(3));
  }
}
