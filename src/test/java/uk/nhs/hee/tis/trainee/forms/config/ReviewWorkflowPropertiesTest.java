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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewWorkflowPropertiesTest {

  private static final String DBC_1 = "1-1RSSQ6R";
  private static final String DBC_2 = "1-1RUZUSF";

  private ReviewWorkflowProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ReviewWorkflowProperties();
  }

  private static StateStage stage(String label, boolean enabled) {
    return new StateStage(label, enabled);
  }

  @Test
  void shouldDefaultToEmptyMapWhenNoWorkflowsConfigured() {
    assertThat("Unexpected review workflows.", properties.getReviewWorkflows().isEmpty(), is(true));
  }

  @Test
  void shouldNotThrowWhenNoWorkflowsConfigured() {
    assertDoesNotThrow(() -> properties.validate());
  }

  @Test
  void shouldPopulateReviewWorkflowsForSingleDbc() {
    List<StateStage> stages = List.of(
        stage("Stage One", true),
        stage("Stage Two", false),
        stage("Stage Three", true)
    );
    properties.setReviewWorkflows(Map.of(DBC_1, stages));

    assertThat("Unexpected DBC.", properties.getReviewWorkflows(), hasKey(DBC_1));
    List<StateStage> result = properties.getReviewWorkflows().get(DBC_1);
    assertThat("Unexpected stage count.", result.size(), is(3));
    assertThat("Unexpected label.", result.get(0).label(), is("Stage One"));
    assertThat("Unexpected enabled.", result.get(0).enabled(), is(true));
    assertThat("Unexpected label.", result.get(1).label(), is("Stage Two"));
    assertThat("Unexpected enabled.", result.get(1).enabled(), is(false));
    assertThat("Unexpected label.", result.get(2).label(), is("Stage Three"));
    assertThat("Unexpected enabled.", result.get(2).enabled(), is(true));
  }

  @Test
  void shouldPopulateReviewWorkflowsForMultipleDbcs() {
    Map<String, List<StateStage>> workflows = Map.of(
        DBC_1, List.of(stage("Triage", true), stage("Manager Review", true),
            stage("Dean Approval", false)),
        DBC_2, List.of(stage("Completeness checks", true))
    );
    properties.setReviewWorkflows(workflows);

    assertThat("Unexpected DBC 1.", properties.getReviewWorkflows(), hasKey(DBC_1));
    assertThat("Unexpected DBC 2.", properties.getReviewWorkflows(), hasKey(DBC_2));
    assertThat("Unexpected stage count for DBC 1.",
        properties.getReviewWorkflows().get(DBC_1).size(), is(3));
    assertThat("Unexpected stage count for DBC 2.",
        properties.getReviewWorkflows().get(DBC_2).size(), is(1));
  }

  @Test
  void shouldPreserveEnabledFlagPerStage() {
    List<StateStage> stages = List.of(
        stage("Active Stage", true),
        stage("Inactive Stage", false)
    );
    properties.setReviewWorkflows(Map.of(DBC_1, stages));

    List<StateStage> result = properties.getReviewWorkflows().get(DBC_1);
    assertThat("Unexpected enabled for stage 0.", result.get(0).enabled(), is(true));
    assertThat("Unexpected enabled for stage 1.", result.get(1).enabled(), is(false));
  }

  @Test
  void shouldAllowSingleStageWorkflow() {
    properties.setReviewWorkflows(Map.of(DBC_1, List.of(stage("Only Stage", true))));

    assertDoesNotThrow(() -> properties.validate());
  }

  @Test
  void shouldAllowDuplicateStageLabelsBetweenDbcs() {
    Map<String, List<StateStage>> workflows = Map.of(
        DBC_1, List.of(stage("Triage", true), stage("Manager Review", false)),
        DBC_2, List.of(stage("Triage", true), stage("APD Approval", true))
    );
    properties.setReviewWorkflows(workflows);

    assertDoesNotThrow(() -> properties.validate());
  }

  @Test
  void shouldAllowDuplicateStageLabelsWithinSameDbc() {
    properties.setReviewWorkflows(
        Map.of(DBC_1, List.of(stage("Review", true), stage("Review", false),
            stage("Final Review", true))));

    assertDoesNotThrow(() -> properties.validate());
  }

  @Test
  void shouldNotThrowWhenAllStageLabelsAreValid() {
    Map<String, List<StateStage>> workflows = Map.of(
        DBC_1, List.of(stage("Programme/Education Team Triage", true),
            stage("Programme Manager Review", true), stage("Associate Dean Approval", false)),
        DBC_2, List.of(stage("Education Team Review", true), stage("APD Approval", false))
    );
    properties.setReviewWorkflows(workflows);

    assertDoesNotThrow(() -> properties.validate());
  }

  @Test
  void shouldNotThrowWhenAllStagesAreDisabled() {
    properties.setReviewWorkflows(
        Map.of(DBC_1, List.of(stage("Stage One", false), stage("Stage Two", false))));

    assertDoesNotThrow(() -> properties.validate());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {" ", "\t", "\n", ""})
  void shouldThrowWhenStageLabelIsBlankOrEmpty(String blankLabel) {
    StateStage bad = stage(blankLabel, true);
    properties.setReviewWorkflows(Map.of(DBC_1, List.of(stage("Valid Stage", true), bad)));

    assertThrows(IllegalStateException.class, () -> properties.validate());
  }

  @Test
  void shouldThrowWhenFirstStageLabelIsBlank() {
    List<StateStage> stages = new ArrayList<>();
    stages.add(stage("", false));
    stages.add(stage("Stage Two", true));
    properties.setReviewWorkflows(Map.of(DBC_1, stages));

    assertThrows(IllegalStateException.class, () -> properties.validate());
  }

  @Test
  void shouldThrowWhenBlankLabelAppearsInSecondDbc() {
    Map<String, List<StateStage>> workflows = new HashMap<>();
    workflows.put(DBC_1, List.of(stage("Valid Stage One", true), stage("Valid Stage Two", false)));
    List<StateStage> invalidStages = new ArrayList<>();
    invalidStages.add(stage("Valid Stage", true));
    invalidStages.add(stage(" ", false));
    workflows.put(DBC_2, invalidStages);
    properties.setReviewWorkflows(workflows);

    assertThrows(IllegalStateException.class, () -> properties.validate());
  }

  @Test
  void shouldThrowWhenDisabledStageLabelIsBlank() {
    StateStage bad = stage("", false);
    properties.setReviewWorkflows(Map.of(DBC_1, List.of(stage("Valid Stage", true), bad)));

    assertThrows(IllegalStateException.class, () -> properties.validate());
  }
}
