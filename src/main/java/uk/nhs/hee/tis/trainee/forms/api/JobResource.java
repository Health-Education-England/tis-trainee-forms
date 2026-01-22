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
import uk.nhs.hee.tis.trainee.forms.config.OpenApiConfiguration.Internal;
import uk.nhs.hee.tis.trainee.forms.job.PublishLtftRefresh;

/**
 * A controller with endpoints for manually triggering scheduled jobs.
 */
@Slf4j
@RestController
@RequestMapping("/api/job")
@XRayEnabled
public class JobResource {

  private final PublishLtftRefresh publishLtftRefreshJob;

  public JobResource(PublishLtftRefresh publishLtftRefreshJob) {
    this.publishLtftRefreshJob = publishLtftRefreshJob;
  }

  /**
   * Publish all exportable LTFT records as a refresh.
   *
   * @return The number of exported LTFT records.
   */
  @Internal
  @PostMapping("/ltft/publish-refresh")
  public ResponseEntity<Integer> publishLtftRefresh() {
    log.info("Received request to publish LTFT refresh.");
    int publishCount = publishLtftRefreshJob.execute();
    return ResponseEntity.ok(publishCount);
  }
}
