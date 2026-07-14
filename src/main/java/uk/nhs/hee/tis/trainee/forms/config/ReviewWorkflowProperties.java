/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.config;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for LTFT review workflows, keyed by designated body code (DBC).
 *
 * <p>Each entry maps a DBC to an ordered list of {@link StateStage}s, each carrying a label and
 * an {@code enabled} flag. Only local offices that require review stages need to be configured;
 * offices using the existing process need no entry.
 *
 * <pre>{@code
 * application:
 *   review-workflows:
 *     "1-1RSSQ6R":
 *       - label: "Programme/Education Team Triage"
 *         enabled: true
 *       - label: "Programme Manager Review"
 *         enabled: true
 *       - label: "Associate Dean Approval"
 *         enabled: false
 * }</pre>
 */
@Slf4j
@Data
@Component
@ConfigurationProperties("application")
public class ReviewWorkflowProperties {

  /**
   * Map of DBC code to its ordered list of review stages.
   */
  private Map<String, List<StateStage>> reviewWorkflows = new HashMap<>();

  /**
   * Validates the configured review workflows after properties have been bound.
   *
   * @throws IllegalStateException if any stage label is blank.
   */
  @PostConstruct
  void validate() {
    reviewWorkflows.forEach((dbc, stages) -> {
      for (int i = 0; i < stages.size(); i++) {
        String label = stages.get(i).label();
        if (label == null || label.isBlank()) {
          throw new IllegalStateException(
              "Review workflow for DBC '%s' contains a blank stage label at index %d."
                  .formatted(dbc, i));
        }
      }
    });

    log.info("Loaded review workflows for {} DBC(s): {}", reviewWorkflows.size(),
        reviewWorkflows.keySet());
  }
}
