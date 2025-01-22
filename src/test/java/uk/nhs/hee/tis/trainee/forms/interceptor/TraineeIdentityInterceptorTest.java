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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.TraineeIdentity;

class TraineeIdentityInterceptorTest {

  private TraineeIdentityInterceptor interceptor;
  private TraineeIdentity traineeIdentity;

  @BeforeEach
  void setUp() {
    traineeIdentity = new TraineeIdentity();
    interceptor = new TraineeIdentityInterceptor(traineeIdentity);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/feature-flags", "/api/form-relocate/id"})
  void shouldReturnTrueAndNotSetTraineeIdWhenNoAuthTokenAndNonTraineeIdEndpoint(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), nullValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/coj", "/api/formr-parta", "/api/formr-partas",
      "/api/formr-parta/id", "/api/formr-partb", "/api/formr-partbs", "/api/formr-partb/id",
      "/api/ltft"})
  void shouldReturnFalseAndNotSetTraineeIdWhenNoAuthTokenAndTraineeIdEndpoint(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), nullValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/feature-flags", "/api/form-relocate/id"})
  void shouldReturnTrueAndNotSetTraineeIdWhenTokenNotMapAndNonTraineeIdEndpoint(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("[]"));
    request.setRequestURI(uri);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), nullValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/coj", "/api/formr-parta", "/api/formr-partas",
      "/api/formr-parta/id", "/api/formr-partb", "/api/formr-partbs", "/api/formr-partb/id",
      "/api/ltft"})
  void shouldReturnFalseAndNotSetTraineeIdWhenTokenNotMapAndTraineeIdEndpoint(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("[]"));
    request.setRequestURI(uri);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), nullValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/feature-flags", "/api/form-relocate/id"})
  void shouldReturnTrueAndNotSetTraineeIdWhenNoTisIdInAuthTokenAndNonTraineeIdEndpoint(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("{}"));
    request.setRequestURI(uri);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), nullValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/coj", "/api/formr-parta", "/api/formr-partas",
      "/api/formr-parta/id", "/api/formr-partb", "/api/formr-partbs", "/api/formr-partb/id",
      "/api/ltft"})
  void shouldReturnFalseAndNotSetTraineeIdWhenNoTisIdInAuthTokenAndTraineeIdEndpoint(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("{}"));
    request.setRequestURI(uri);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), nullValue());
  }

  @Test
  void shouldReturnTrueAndSetTraineeIdWhenTisIdInAuthTokenAndTraineeIdEndpoint() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId("40"));

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), is("40"));
  }
}
