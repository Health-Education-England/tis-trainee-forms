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

import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

/**
 * A controller for Less Than Full-time (LTFT) endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@XRayEnabled
public class LtftResource {

  private final LtftService service;

  /**
   * Construct a REST controller for LTFT related endpoints.
   *
   * @param service A service providing LTFT functionality.
   */
  public LtftResource(LtftService service) {
    this.service = service;
  }

  /**
   * Retrieve a list of LTFT summaries for the logged-in user.
   *
   * @return The list of LTFT summaries, or an empty list if none found.
   */
  @GetMapping("/ltft")
  public ResponseEntity<List<LtftSummaryDto>> getLtftSummaryList() {
    log.info("Request to get summary list of LTFT records.");
    List<LtftSummaryDto> ltfts = service.getLtftSummaries();
    return ResponseEntity.ok(ltfts);
  }
}
