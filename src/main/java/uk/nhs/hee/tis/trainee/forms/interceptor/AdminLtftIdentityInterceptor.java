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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminLtftIdentity;

/**
 * An interceptor for creating a {@link AdminLtftIdentity} from a request.
 */
@Slf4j
public class AdminLtftIdentityInterceptor extends AdminIdentityInterceptor {

  private final AdminLtftIdentity adminLtftIdentity;

  /**
   * Constructor for AdminLtftIdentityInterceptor.
   *
   * @param adminLtftIdentity The AdminLtftIdentity to be set from the request.
   */
  public AdminLtftIdentityInterceptor(AdminLtftIdentity adminLtftIdentity) {
    super(adminLtftIdentity);
    this.adminLtftIdentity = adminLtftIdentity;
  }

  /**
   * Apply the rules for admin identity and additional checks for LTFT admin identity.
   *
   * @param request  The current HTTP request.
   * @param response The current HTTP response.
   * @param handler  The chosen handler to execute, for type and/or instance evaluation.
   * @return whether the execution chain should proceed with the next interceptor.
   */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler) {
    super.preHandle(request, response, handler);

    if (!adminLtftIdentity.isComplete()) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      return false;
    }
    return true;
  }
}
