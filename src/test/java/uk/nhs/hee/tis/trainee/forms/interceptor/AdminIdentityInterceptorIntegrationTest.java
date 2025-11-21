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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.config.InterceptorConfiguration;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.interceptor.AdminIdentityInterceptorIntegrationTest.InterceptorTestController;

@WebMvcTest(InterceptorTestController.class)
@Import(InterceptorConfiguration.class)
class AdminIdentityInterceptorIntegrationTest {

  private static final String API_PATH = "/api/admin";
  private static final String EMAIL_1 = "admin.1@example.com";
  private static final String EMAIL_2 = "admin.2@example.com";

  private static final String BEAN_NAME = "adminIdentity";
  private static final String TARGET_BEAN_NAME = ScopedProxyUtils.getTargetBeanName(BEAN_NAME);
  private static final String ADMIN_EMAIL = "email";

  @Autowired
  private MockMvc mockMvc;

  @SpyBean
  private AdminIdentityInterceptor interceptor;

  @ParameterizedTest
  @ValueSource(strings = {"/api/admin", "/api/admin/abc", "/api/admin/abc/xyz"})
  void shouldAddAdminIdentityToRequest(String apiPath) throws Exception {
    Jwt token = TestJwtUtil.createToken("""
        {
           "email": "%s",
           "cognito:groups": []
        }
        """.formatted(EMAIL_1));
    mockMvc.perform(get(apiPath)
            .with(jwt().jwt(token)))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(
            request().attribute(TARGET_BEAN_NAME, hasProperty(ADMIN_EMAIL, is(EMAIL_1))));

    verify(interceptor).preHandle(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/admin", "/api/admin/xxx", "/api/admin/xxx/yyy"})
  void shouldAddNewAdminIdentityOnEachRequest(String apiPath) throws Exception {
    Jwt token1 = TestJwtUtil.createToken("""
        {
           "email": "%s",
           "given_name": "Ad",
           "family_name": "Min-One",
           "cognito:groups": []
        }
        """.formatted(EMAIL_1));
    mockMvc.perform(get(apiPath)
            .with(jwt().jwt(token1)))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(
            request().attribute(TARGET_BEAN_NAME, hasProperty(ADMIN_EMAIL, is(EMAIL_1))));

    Jwt token2 = TestJwtUtil.createToken("""
        {
           "email": "%s",
           "given_name": "Ad",
           "family_name": "Min-Two",
           "cognito:groups": []
        }
        """.formatted(EMAIL_2));
    mockMvc.perform(get(API_PATH)
            .with(jwt().jwt(token2)))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(
            request().attribute(TARGET_BEAN_NAME, hasProperty(ADMIN_EMAIL, is(EMAIL_2))));

    verify(interceptor, times(2)).preHandle(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api", "/api/xxx", "/api/xxx/yyy"})
  void shouldNotAddAdminIdentityToNonInterceptedRequests(String apiPath) throws Exception {
    mockMvc.perform(get(apiPath)
            .with(jwt().jwt(TestJwtUtil.createTokenForTisId(EMAIL_1))))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(request().attribute(TARGET_BEAN_NAME, nullValue()));

    verify(interceptor, never()).preHandle(any(), any(), any());
  }

  @Test
  void shouldMakeAdminIdentityAvailableToControllers() throws Exception {
    Jwt token1 = TestJwtUtil.createToken("""
        {
           "email": "%s",
           "given_name": "Ad",
           "family_name": "Min-One",
           "cognito:groups": ["123"]
        }
        """.formatted(EMAIL_1));
    mockMvc.perform(get(API_PATH)
            .with(jwt().jwt(token1)))
        .andExpect(content().string(EMAIL_1));

    Jwt token2 = TestJwtUtil.createToken("""
        {
           "email": "%s",
           "given_name": "Ad",
           "family_name": "Min-Two",
           "cognito:groups": ["321"]
        }
        """.formatted(EMAIL_2));
    mockMvc.perform(get(API_PATH)
            .with(jwt().jwt(token2)))
        .andExpect(content().string(EMAIL_2));
  }

  @SpringBootApplication
  @RestController
  public static class InterceptorTestController {

    private final AdminIdentity adminIdentity;

    public InterceptorTestController(AdminIdentity adminIdentity) {
      this.adminIdentity = adminIdentity;
    }

    @GetMapping(API_PATH)
    public String testInterceptor() {
      assertThat("Unexpected admin identity.", adminIdentity, notNullValue());
      return adminIdentity.getEmail();
    }
  }
}
