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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminLtftIdentity;
import uk.nhs.hee.tis.trainee.forms.interceptor.AdminLtftIdentityInterceptorIntegrationTest.InterceptorTestController;

@WebMvcTest(InterceptorTestController.class)
@Import(InterceptorConfiguration.class)
class AdminLtftIdentityInterceptorIntegrationTest {
  private static final String API_PATH = "/api/admin/ltft";
  private static final String EMAIL_1 = "admin.ltft.1@example.com";

  private static final String BEAN_NAME = "adminLtftIdentity";
  private static final String TARGET_BEAN_NAME = ScopedProxyUtils.getTargetBeanName(BEAN_NAME);
  private static final String ADMIN_EMAIL = "email";

  @Autowired
  private MockMvc mockMvc;

  @SpyBean(name = "adminLtftIdentityInterceptor")
  private AdminLtftIdentityInterceptor interceptor;

  @ParameterizedTest
  @ValueSource(strings = {"/api/admin/ltft", "/api/admin/ltft/abc", "/api/admin/ltft/abc/xyz"})
  void shouldAddAdminLtftIdentityToRequest(String apiPath) throws Exception {
    String token = TestJwtUtil.generateToken("""
        {
           "email": "%s",
           "cognito:groups": [],
            "cognito:roles": []
        }
        """.formatted(EMAIL_1));
    mockMvc.perform(get(apiPath)
            .header(HttpHeaders.AUTHORIZATION, token))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(
            request().attribute(TARGET_BEAN_NAME, hasProperty(ADMIN_EMAIL, is(EMAIL_1))));

    verify(interceptor).preHandle(any(), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api", "/api/admin", "/api/admin/xxx"})
  void shouldNotAddAdminLtftIdentityToNonInterceptedRequests(String apiPath) throws Exception {
    mockMvc.perform(get(apiPath)
            .header(HttpHeaders.AUTHORIZATION, TestJwtUtil.generateTokenForTisId(EMAIL_1)))
        .andExpect(request().attribute(BEAN_NAME, nullValue()))
        .andExpect(request().attribute(TARGET_BEAN_NAME, nullValue()));

    verify(interceptor, never()).preHandle(any(), any(), any());
  }

  @SpringBootApplication
  @RestController
  public static class InterceptorTestController {

    private final AdminLtftIdentity adminLtftIdentity;

    public InterceptorTestController(AdminLtftIdentity adminLtftIdentity) {
      this.adminLtftIdentity = adminLtftIdentity;
    }

    @GetMapping(API_PATH)
    public String testInterceptor() {
      assertThat("Unexpected admin LTFT identity.", adminLtftIdentity, notNullValue());
      return adminLtftIdentity.getEmail();
    }
  }
}
