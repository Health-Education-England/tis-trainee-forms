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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.nhs.hee.tis.trainee.forms.api.util.AuthTokenUtil;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;

/**
 * An interceptor for creating a {@link TraineeIdentity} from a request.
 */
@Slf4j
public class TraineeIdentityInterceptor implements HandlerInterceptor {

  private static final String TIS_ID_ATTRIBUTE = "custom:tisId";
  private static final String EMAIL_ATTRIBUTE = "email";
  private static final String GIVEN_NAME_ATTRIBUTE = "given_name";
  private static final String FAMILY_NAME_ATTRIBUTE = "family_name";
  private static final String FEATURES_ATTRIBUTE = "features";

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final TraineeIdentity traineeIdentity;

  public TraineeIdentityInterceptor(TraineeIdentity traineeIdentity) {
    this.traineeIdentity = traineeIdentity;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
    String authToken = request.getHeader(HttpHeaders.AUTHORIZATION);

    if (authToken != null) {
      try {
        String traineeId = AuthTokenUtil.getAttribute(authToken, TIS_ID_ATTRIBUTE);
        traineeIdentity.setTraineeId(traineeId);
        String email = AuthTokenUtil.getAttribute(authToken, EMAIL_ATTRIBUTE);
        traineeIdentity.setEmail(email);
        String givenName = AuthTokenUtil.getAttribute(authToken, GIVEN_NAME_ATTRIBUTE);
        String familyName = AuthTokenUtil.getAttribute(authToken, FAMILY_NAME_ATTRIBUTE);
        if (givenName != null && familyName != null) {
          traineeIdentity.setName("%s %s".formatted(givenName, familyName));
        }
        traineeIdentity.setFeatures(AuthTokenUtil.getFeatures(authToken));
      } catch (IOException e) {
        log.warn("Unable to extract trainee ID from authorization token.", e);
      }
    }

    if (traineeIdentity.getTraineeId() == null) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      return false;
    }
    return true;
  }
}
