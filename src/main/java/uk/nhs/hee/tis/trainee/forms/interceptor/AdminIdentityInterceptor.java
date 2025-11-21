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
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;
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

  private final AdminIdentity adminIdentity;

  public AdminIdentityInterceptor(AdminIdentity adminIdentity) {
    this.adminIdentity = adminIdentity;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth != null && auth.getPrincipal() instanceof Jwt authToken) {
      String email = authToken.getClaimAsString(EMAIL_ATTRIBUTE);
      adminIdentity.setEmail(email);

      List<String> adminGroups = authToken.getClaimAsStringList(GROUPS_ATTRIBUTE);
      if (adminGroups != null) {
        adminIdentity.setGroups(new HashSet<>(adminGroups));
      }

      String givenName = authToken.getClaimAsString(GIVEN_NAME_ATTRIBUTE);
      String familyName = authToken.getClaimAsString(FAMILY_NAME_ATTRIBUTE);
      if (givenName != null && familyName != null) {
        adminIdentity.setName("%s %s".formatted(givenName, familyName));
      }
    }

    if (!adminIdentity.isComplete()) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      return false;
    }

    return true;
  }
}
