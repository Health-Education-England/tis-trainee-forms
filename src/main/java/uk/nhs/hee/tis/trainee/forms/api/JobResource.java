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

import com.amazonaws.xray.spring.aop.XRayEnabled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.job.PublishFormrPartaRefresh;
import uk.nhs.hee.tis.trainee.forms.job.PublishFormrPartbRefresh;
import uk.nhs.hee.tis.trainee.forms.job.PublishLtftRefresh;

/**
 * A controller with endpoints for manually triggering scheduled jobs.
 */
@Slf4j
@RestController
@RequestMapping("/api/job")
@XRayEnabled
public class JobResource {

  private final PublishFormrPartaRefresh publishFormrPartaRefreshJob;
  private final PublishFormrPartbRefresh publishFormrPartbRefreshJob;
  private final PublishLtftRefresh publishLtftRefreshJob;

  /**
   * Create a job controller.
   * @param publishFormrPartaRefreshJob A job for publishing Form R Part A refreshes.
   * @param publishFormrPartbRefreshJob The job for publishing Form R Part B refreshes.
   * @param publishLtftRefreshJob The job for publishing LTFT refreshes.
   */
  public JobResource(PublishFormrPartaRefresh publishFormrPartaRefreshJob,
      PublishFormrPartbRefresh publishFormrPartbRefreshJob,
      PublishLtftRefresh publishLtftRefreshJob) {
    this.publishFormrPartaRefreshJob = publishFormrPartaRefreshJob;
    this.publishFormrPartbRefreshJob = publishFormrPartbRefreshJob;
    this.publishLtftRefreshJob = publishLtftRefreshJob;
  }

  /**
   * Publish all exportable Form-R Part A records as a refresh.
   *
   * @return The number of exported Form-R Part A records.
   */
  @PostMapping("/formr-parta/publish-refresh")
  public ResponseEntity<Integer> publishFormrPartaRefresh() {
    log.info("Received request to publish Form-R Part A refresh.");
    int publishCount = publishFormrPartaRefreshJob.execute();
    return ResponseEntity.ok(publishCount);
  }

  /**
   * Publish all exportable Form-R Part B records as a refresh.
   *
   * @return The number of exported Form-R Part B records.
   */
  @PostMapping("/formr-partb/publish-refresh")
  public ResponseEntity<Integer> publishFormrPartbRefresh() {
    log.info("Received request to publish Form-R Part B refresh.");
    int publishCount = publishFormrPartbRefreshJob.execute();
    return ResponseEntity.ok(publishCount);
  }

  /**
   * Publish all exportable LTFT records as a refresh.
   *
   * @return The number of exported LTFT records.
   */
  @PostMapping("/ltft/publish-refresh")
  public ResponseEntity<Integer> publishLtftRefresh() {
    log.info("Received request to publish LTFT refresh.");
    int publishCount = publishLtftRefreshJob.execute();
    return ResponseEntity.ok(publishCount);
  }
}
