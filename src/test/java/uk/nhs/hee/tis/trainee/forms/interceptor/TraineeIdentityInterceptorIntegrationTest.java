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

package uk.nhs.hee.tis.trainee.forms.interceptor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.config.InterceptorConfiguration;
import uk.nhs.hee.tis.trainee.forms.dto.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.interceptor.TraineeIdentityInterceptorIntegrationTest.InterceptorTestController;

@WebMvcTest(InterceptorTestController.class)
@Import(InterceptorConfiguration.class)
class TraineeIdentityInterceptorIntegrationTest {

  private static final String API_PATH = "/api/formr-partas";
  private static final String ID_1 = "40";
  private static final String ID_2 = "41";

  private static final String BEAN_NAME = "traineeIdentity";
  private static final String TARGET_BEAN_NAME = ScopedProxyUtils.getTargetBeanName(BEAN_NAME);
  private static final String TRAINEE_ID = "traineeTisId";

  @Autowired
  private MockMvc mockMvc;

  @SpyBean
  private TraineeIdentityInterceptor interceptor;

  @ParameterizedTest
  @ValueSource(strings = {"/api/coj",
      "/api/formr-parta", "/api/formr-partas", "/api/formr-parta/xxx", "/api/formr-parta/xxx/yyy",
      "/api/formr-partb", "/api/formr-partbs", "/api/formr-partb/xxx", "/api/formr-partb/xxx/yyy",
      "/api/ltft", "/api/ltft/xxx", "/api/ltft/xxx/yyy"})
  void shouldAddTraineeIdToRequest(String apiPath) throws Exception {
    mockMvc.perform(get(apiPath)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(ID_1)))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(request().attribute(TARGET_BEAN_NAME, hasProperty(TRAINEE_ID, is(ID_1))));

    verify(interceptor).preHandle(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/coj",
      "/api/formr-parta", "/api/formr-partas", "/api/formr-parta/xxx", "/api/formr-parta/xxx/yyy",
      "/api/formr-partb", "/api/formr-partbs", "/api/formr-partb/xxx", "/api/formr-partb/xxx/yyy",
      "/api/ltft", "/api/ltft/xxx", "/api/ltft/xxx/yyy"})
  void shouldAddNewTraineeIdOnEachRequest(String apiPath) throws Exception {
    mockMvc.perform(get(apiPath)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(ID_1)))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(request().attribute(TARGET_BEAN_NAME, hasProperty(TRAINEE_ID, is(ID_1))));

    mockMvc.perform(get(API_PATH)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(ID_2)))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(request().attribute(TARGET_BEAN_NAME, hasProperty(TRAINEE_ID, is(ID_2))));

    verify(interceptor, times(2)).preHandle(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api", "/api/xxx", "/api/xxx/yyy", "/api/feature-flags",
      "/api/form-relocate/xxx"})
  void shouldNotAddTraineeIdToNonInterceptedRequests(String apiPath) throws Exception {
    mockMvc.perform(get(apiPath)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(ID_1)))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(request().attribute(TARGET_BEAN_NAME, nullValue()));

    verify(interceptor, never()).preHandle(any(), any(), any());
  }

  @Test
  void shouldMakeTraineeIdentityAvailableToControllers() throws Exception {
    mockMvc.perform(get(API_PATH)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(ID_1)))
        .andExpect(content().string(ID_1));

    mockMvc.perform(get(API_PATH)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(ID_2)))
        .andExpect(content().string(ID_2));
  }

  @SpringBootApplication
  @RestController
  public static class InterceptorTestController {

    private final TraineeIdentity traineeIdentity;

    public InterceptorTestController(TraineeIdentity traineeIdentity) {
      this.traineeIdentity = traineeIdentity;
    }

    @GetMapping(API_PATH)
    public String testInterceptor() {
      assertThat("Unexpected trainee identity.", traineeIdentity, notNullValue());
      return traineeIdentity.getTraineeId();
    }

  }
}
