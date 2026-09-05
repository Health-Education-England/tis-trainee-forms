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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;

import java.util.UUID;

class AdminIdentityInterceptorTest {

  private static final String EMAIL = "ad.min@example.com";
  private static final String GIVEN_NAME = "Ad";
  private static final String FAMILY_NAME = "Min";
  private static final String FULL_NAME = "Ad Min";
  private static final String GROUP_1 = "123456";
  private static final String GROUP_2 = "ABCDEF";;
  private static final String ROLE_1 = "LTFT_ADMIN";
  private static final String ROLE_2 = "PROGRAMME_ADMIN";
  private static final UUID PROGRAMME_1 = UUID.randomUUID();
  private static final UUID PROGRAMME_2 = UUID.randomUUID();

  private AdminIdentityInterceptor interceptor;
  private AdminIdentity adminIdentity;

  private SecurityContext securityContext;
  private Authentication auth;

  @BeforeEach
  void setUp() {
    adminIdentity = new AdminIdentity();
    interceptor = new AdminIdentityInterceptor(adminIdentity);

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
  void shouldReturnFalseAndNotPopulateIdentityWhenNoAuthToken() {
    when(securityContext.getAuthentication()).thenReturn(null);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), nullValue());
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), nullValue());
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), nullValue());
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(), nullValue());
  }

  @Test
  void shouldReturnFalseAndNotPopulateIdentityWhenNoAttributesInAuthToken() {
    Jwt token = TestJwtUtil.createToken("{}");
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), nullValue());
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), nullValue());
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), nullValue());
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(), nullValue());
  }

  @Test
  void shouldReturnFalseAndPartiallyPopulateIdentityWhenNoEmailInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ],
          "cognito:roles": [
            "%s",
            "%s"
          ],
          "cognito:programmes": [
            "%s",
            "%s"
          ]
        }
        """.formatted(GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2, ROLE_1, ROLE_2, PROGRAMME_1,
        PROGRAMME_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), nullValue());
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(2));
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(),
        hasItems(PROGRAMME_1, PROGRAMME_2));
  }

  @Test
  void shouldReturnFalseAndPartiallyPopulateIdentityWhenNoGivenNameInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "email": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ],
          "cognito:roles": [
            "%s",
            "%s"
          ],
          "cognito:programmes": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, FAMILY_NAME, GROUP_1, GROUP_2, ROLE_1, ROLE_2, PROGRAMME_1,
        PROGRAMME_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), nullValue());
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(2));
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(),
        hasItems(PROGRAMME_1, PROGRAMME_2));
  }

  @Test
  void shouldReturnFalseAndPartiallyPopulateIdentityWhenNoFamilyNameInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ],
          "cognito:roles": [
            "%s",
            "%s"
          ],
          "cognito:programmes": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, GROUP_1, GROUP_2, ROLE_1, ROLE_2, PROGRAMME_1,
        PROGRAMME_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), nullValue());
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(2));
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(),
        hasItems(PROGRAMME_1, PROGRAMME_2));
  }

  @Test
  void shouldReturnTrueAndPartiallyPopulateIdentityWhenHasProgrammeButNoGroupsInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:roles": [
            "%s",
            "%s"
          ],
          "cognito:programmes": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, ROLE_1, ROLE_2, PROGRAMME_1,
        PROGRAMME_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), nullValue());
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(2));
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(),
        hasItems(PROGRAMME_1, PROGRAMME_2));
  }

  @Test
  void shouldReturnTrueAndPopulateIdentityWhenAllFieldsAndHasProgrammeWithEmptyGroupsInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [],
          "cognito:roles": [
            "%s",
            "%s"
          ],
          "cognito:programmes": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, ROLE_1, ROLE_2, PROGRAMME_1,
        PROGRAMME_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(0));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(2));
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(),
        hasItems(PROGRAMME_1, PROGRAMME_2));
  }

  @Test
  void shouldReturnFalseAndPartiallyPopulateIdentityWhenNoRolesInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ],
          "cognito:programmes": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2, PROGRAMME_1, PROGRAMME_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), nullValue());
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(2));
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(),
        hasItems(PROGRAMME_1, PROGRAMME_2));
  }

  @Test
  void shouldReturnFalseAndPopulateIdentityWhenAllFieldsAndEmptyRolesInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [
            "%s",
            "%s"
          ],
          "cognito:roles": [],
          "cognito:programmes": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2, PROGRAMME_1, PROGRAMME_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(0));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(2));
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(),
        hasItems(PROGRAMME_1, PROGRAMME_2));
  }

  @Test
  void shouldReturnTrueAndPartiallyPopulateIdentityWhenHasGroupsButNoProgrammeInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
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
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme.", adminIdentity.getProgrammes(), nullValue());
  }

  @Test
  void shouldReturnTrueAndPopulateIdentityWhenAllFieldsAndHasGroupsWithEmptyProgrammeInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
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
          ],
          "cognito:programmes": []
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2, ROLE_1, ROLE_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(0));
  }

  @Test
  void shouldReturnFalseAndPopulateIdentityWhenAllFieldsButNoGroupsNorProgrammesInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:roles": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, ROLE_1, ROLE_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group.", adminIdentity.getGroups(), nullValue());
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme.", adminIdentity.getProgrammes(), nullValue());
  }

  @Test
  void shouldReturnFalseAndPopulateIdentityWhenAllFieldsButGroupsAndProgrammesEmptyInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
        {
          "email": "%s",
          "given_name": "%s",
          "family_name": "%s",
          "cognito:groups": [],
          "cognito:roles": [
            "%s",
            "%s"
          ],
          "cognito:programmes": []
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, ROLE_1, ROLE_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(false));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(0));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(0));
  }

  @Test
  void shouldReturnTrueAndPopulateIdentityWhenAllFieldsAndGroupsInAuthToken() {
    Jwt token = TestJwtUtil.createToken("""
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
          ],
          "cognito:programmes": [
            "%s",
            "%s"
          ]
        }
        """.formatted(EMAIL, GIVEN_NAME, FAMILY_NAME, GROUP_1, GROUP_2, ROLE_1, ROLE_2, PROGRAMME_1,
        PROGRAMME_2));
    when(auth.getPrincipal()).thenReturn(token);

    boolean result = interceptor.preHandle(new MockHttpServletRequest(),
        new MockHttpServletResponse(), new Object());

    assertThat("Unexpected result.", result, is(true));
    assertThat("Unexpected admin email.", adminIdentity.getEmail(), is(EMAIL));
    assertThat("Unexpected admin name.", adminIdentity.getName(), is(FULL_NAME));
    assertThat("Unexpected admin group count.", adminIdentity.getGroups(), hasSize(2));
    assertThat("Unexpected admin groups.", adminIdentity.getGroups(), hasItems(GROUP_1, GROUP_2));
    assertThat("Unexpected admin role count.", adminIdentity.getRoles(), hasSize(2));
    assertThat("Unexpected admin roles.", adminIdentity.getRoles(), hasItems(ROLE_1, ROLE_2));
    assertThat("Unexpected admin programme count.", adminIdentity.getProgrammes(), hasSize(2));
    assertThat("Unexpected admin programmes.", adminIdentity.getProgrammes(),
        hasItems(PROGRAMME_1, PROGRAMME_2));
  }
}
