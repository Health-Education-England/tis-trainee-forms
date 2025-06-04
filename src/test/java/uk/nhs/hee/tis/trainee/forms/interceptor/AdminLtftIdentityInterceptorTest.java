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
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminLtftIdentity;

class AdminLtftIdentityInterceptorTest {

  private static final String EMAIL = "ad.min@example.com";
  private static final String GIVEN_NAME = "Ad";
  private static final String FAMILY_NAME = "Min";
  private static final String GROUP_1 = "123456";
  private static final String GROUP_2 = "ABCDEF";
  private static final String ROLE_1 = "TSS Support Admin";
  private static final String ROLE_2 = "NHSE LTFT Admin";

  private AdminLtftIdentityInterceptor interceptor;
  private AdminLtftIdentity adminLtftIdentity;

  @BeforeEach
  void setUp() {
    adminLtftIdentity = new AdminLtftIdentity();
    interceptor = new AdminLtftIdentityInterceptor(adminLtftIdentity);
  }

  @Test
  void shouldReturnFalseWhenNoRolesInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s"
        },
        "cognito:groups": [
            "%s",
            "%s"
        ]
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
  }

  @Test
  void shouldReturnFalseWhenEmptyRolesInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s"
        },
        "cognito:groups": [
            "%s",
            "%s"
        ],
        "cognito:roles": []
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
  }

  @Test
  void shouldReturnFalseWhenNoMatchingRoleInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s"
        },
        "cognito:groups": [
            "%s",
            "%s"
        ],
        "cognito:roles": [
            "some role",
            "another role"
        ]
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
  }

  @Test
  void shouldReturnTrueWhenMatchingRoleInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ],
          "cognito:roles": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2, ROLE_1, ROLE_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
  }
}
