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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;

class AdminIdentityInterceptorTest {

  private static final String EMAIL = "ad.min@example.com";
  private static final String GIVEN_NAME = "Ad";
  private static final String FAMILY_NAME = "Min";
  private static final String FULL_NAME = "Ad Min";
  private static final String GROUP_1 = "123456";
  private static final String GROUP_2 = "ABCDEF";
  private static final String ROLE_1 = "TSS Support Admin";
  private static final String ROLE_2 = "NHSE LTFT Admin";

  private AdminIdentityInterceptor interceptor;
  private AdminIdentity adminIdentity;

  @BeforeEach
  void setUp() {
    adminIdentity = new AdminIdentity();
    interceptor = new AdminIdentityInterceptor(adminIdentity);
  }

  @Test
  void shouldReturnFalseAndNotPopulateIdentityWhenNoAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), nullValue());
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), nullValue());
  }

  @Test
  void shouldReturnFalseAndNotPopulateIdentityWhenTokenNotMap() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("[]"));

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), nullValue());
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), nullValue());
  }

  @Test
  void shouldReturnFalseAndNotPopulateIdentityWhenNoAttributesInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateToken("{}"));

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), nullValue());
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), nullValue());
  }

  @Test
  void shouldReturnFalseAndPartiallyPopulateIdentityWhenNoEmailInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ]
        }
        """.formatted(GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), nullValue());
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
  }

  @Test
  void shouldReturnFalseAndPartiallyPopulateIdentityWhenNoGivenNameInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, FAMILY_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), nullValue());
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
  }

  @Test
  void shouldReturnFalseAndPartiallyPopulateIdentityWhenNoFamilyNameInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), nullValue());
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
  }

  @Test
  void shouldReturnFalseAndPartiallyPopulateIdentityWhenNoGroupsInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s"
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), nullValue());
  }

  @Test
  void shouldReturnFalseAndPopulateIdentityWhenAllFieldsAndEmptyGroupsInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": []
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/admin/ltft", "/api/admin/ltft/abc"})
  void shouldReturnFalseAndPopulateIdentityWhenAllFieldsAndNoRolesInAuthToken(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/admin/ltft", "/api/admin/ltft/abc"})
  void shouldReturnFalseAndPopulateIdentityWhenAllFieldsAndEmptyRolesInAuthToken(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ],
          "cognito:roles": []
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
  }

  @Test
  void shouldReturnTrueAndPopulateIdentityWhenAllFieldsAndGroupsInAuthToken() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    String token = TestJwtUtil.generateToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2));
    request.addHeader(HttpHeaders.AUTHORIZATION, token);

    boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/admin/ltft", "/api/admin/ltft/abc"})
  void shouldReturnTrueAndPopulateIdentityWhenAllFieldsAndLtftRoleInAuthToken(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
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
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
  }
}
