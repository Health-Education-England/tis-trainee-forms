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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;

class TraineeIdentityInterceptorTest {

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final String EMAIL = "trainee@example.com";
  private static final String GIVEN_NAME = "Anthony";
  private static final String FAMILY_NAME = "Gilliam";
  private static final String FULL_NAME = "Anthony Gilliam";

  private TraineeIdentityInterceptor interceptor;
  private TraineeIdentity traineeIdentity;

  private SecurityContext securityContext;
  private Authentication auth;

  @BeforeEach
  void setUp() {
    traineeIdentity = new TraineeIdentity();
    interceptor = new TraineeIdentityInterceptor(traineeIdentity);

    auth = mock(Authentication.class);
    securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(auth);
    SecurityContextHolder.setContext(securityContext);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldReturnFalseAndNotSetTraineeIdWhenNoAuthToken() {
    when(securityContext.getAuthentication()).thenReturn(null);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), nullValue());
  }

  @Test
  void shouldReturnFalseAndNotSetTraineeIdWhenNoTisIdInAuthToken() {
    Jwt token = TestJwtUtil.createToken("{}");
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), nullValue());
  }

  @Test
  void shouldReturnTrueAndSetTraineeIdWhenTisIdInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "custom:tisId": "%s"
        }
        """.formatted(TRAINEE_ID));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), is(TRAINEE_ID));
  }

  @Test
  void shouldReturnTrueAndIncludeOtherDetailsWhenInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "custom:tisId": "%s",
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s"
        }
        """.formatted(TRAINEE_ID, EMAIL, GIVEN_NAME, FAMILY_NAME));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected trainee ID.", traineeIdentity.getTraineeId(), is(TRAINEE_ID));
    assertThat("Unexpected email.", traineeIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected name.", traineeIdentity.getName(), is(FULL_NAME));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
                 |
                 | "Gilliam"
       "Anthony" |
      """)
  void shouldExcludeNameWhenGivenOrFamilyNameMissingInAuthToken(
      String givenName, String familyName) {
    Jwt token = TestJwtUtil.createToken("""
        {
          "custom:tisId": "%s",
          "email": "%s",
          "given_name": %s,
          "family_name": %s
        }
        """.formatted(TRAINEE_ID, EMAIL, givenName, familyName));
    when(auth.getPrincipal()).thenReturn(token);

    interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
        new Object());

    assertThat("Unexpected name.", traineeIdentity.getName(), is(nullValue()));
  }
}
