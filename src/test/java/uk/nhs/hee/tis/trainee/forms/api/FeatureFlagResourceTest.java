/*
 * The MIT License (MIT)
 *
 * Copyright 2021 Crown Copyright (Health Education England)
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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.nhs.hee.tis.trainee.forms.config.FeatureConfigurationProperties;

@ExtendWith(SpringExtension.class)
@WebMvcTest(FeatureFlagResource.class)
@ContextConfiguration(classes = {FeatureConfigurationProperties.class})
class FeatureFlagResourceTest {

  @Autowired
  private FeatureConfigurationProperties properties;

  private MockMvc mockMvc;

  private boolean covidDeclaration;

  @BeforeEach
  void setUp() {
    covidDeclaration = properties.getFormRPartB().isCovidDeclaration();

    FeatureFlagResource featureFlagResource = new FeatureFlagResource(properties);
    mockMvc = MockMvcBuilders.standaloneSetup(featureFlagResource).build();
  }

  @AfterEach
  void tearDown() {
    properties.getFormRPartB().setCovidDeclaration(covidDeclaration);
  }

  @ParameterizedTest(name = "Should return COVID feature flag when flag is {0}")
  @ValueSource(booleans = {true, false})
  void testGetFeatureFlags(boolean covidDeclaration,
      @Autowired FeatureConfigurationProperties properties) throws Exception {
    properties.getFormRPartB().setCovidDeclaration(covidDeclaration);

    mockMvc.perform(get("/api/feature-flags")
        .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.formRPartB.covidDeclaration").value(is(covidDeclaration)));
  }
}
