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

package uk.nhs.hee.tis.trainee.forms.api.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto.FormFeatures;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto.FormFeatures.LtftFeatures;

@Slf4j
public class AuthTokenUtil {

  private static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  private AuthTokenUtil() {

  }

  /**
   * Get the features from the provided token.
   *
   * @param token The token to use.
   * @return The features DTO extracted from the token.
   */
  public static FeaturesDto getFeatures(Jwt token) {
    Map<String, Object> featuresClaim = token.getClaimAsMap("features");

    if (featuresClaim == null) {
      log.warn("No features found in the token payload {}.", token);
      return FeaturesDto.builder()
          .forms(FormFeatures.builder()
              .ltft(LtftFeatures.builder().build())
              .build())
          .build();
    }

    return mapper.convertValue(featuresClaim, FeaturesDto.class);
  }
}
