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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.nhs.hee.tis.trainee.forms.api.util.AuthTokenUtil;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;

/**
 * An interceptor for creating a {@link AdminIdentity} from a request.
 */
@Slf4j
public class AdminIdentityInterceptor implements HandlerInterceptor {

  private static final String EMAIL_ATTRIBUTE = "email";
  private static final String GIVEN_NAME_ATTRIBUTE = "given_name";
  private static final String FAMILY_NAME_ATTRIBUTE = "family_name";
  private static final String GROUPS_ATTRIBUTE = "cognito:groups";
  private static final String ROLES_ATTRIBUTE = "cognito:roles";

  private final AdminIdentity adminIdentity;

  public AdminIdentityInterceptor(AdminIdentity adminIdentity) {
    this.adminIdentity = adminIdentity;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
    Set<String> adminRoles = null;

    String authToken = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authToken != null) {
      try {
        String email = AuthTokenUtil.getAttribute(authToken, EMAIL_ATTRIBUTE);
        adminIdentity.setEmail(email);
        Set<String> adminGroups = AuthTokenUtil.getAttributes(authToken, GROUPS_ATTRIBUTE);
        adminIdentity.setGroups(adminGroups);
        adminRoles = AuthTokenUtil.getAttributes(authToken, ROLES_ATTRIBUTE);

        String givenName = AuthTokenUtil.getAttribute(authToken, GIVEN_NAME_ATTRIBUTE);
        String familyName = AuthTokenUtil.getAttribute(authToken, FAMILY_NAME_ATTRIBUTE);
        if (givenName != null && familyName != null) {
          adminIdentity.setName("%s %s".formatted(givenName, familyName));
        }
      } catch (IOException e) {
        log.warn("Unable to extract the admin identity from authorization token.", e);
      }
    }

    if (!adminIdentity.isComplete()) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      return false;
    }

    // LTFT endpoints require the appropriate role to access.
    if (request.getRequestURI().matches("^/api/admin/ltft(/.+)?$")
       && (adminRoles == null || !adminRoles.contains("NHSE LTFT Admin"))) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      return false;
    }

    return true;
  }
}
