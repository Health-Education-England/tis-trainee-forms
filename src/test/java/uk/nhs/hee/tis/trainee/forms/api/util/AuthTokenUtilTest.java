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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto.FormFeatures.LtftFeatures;

class AuthTokenUtilTest {

  @Test
  void getFeaturesShouldReturnEmptyFeaturesWhenFeaturesNotInToken() {
    Jwt authToken = TestJwtUtil.createToken("""
        {
          "claim": "value"
        }
        """);

    FeaturesDto features = AuthTokenUtil.getFeatures(authToken);
    LtftFeatures ltftFeatures = features.forms().ltft();

    assertThat("Unexpected features ltft value.", ltftFeatures.enabled(), is(false));
    assertThat("Unexpected features ltft programmes value.", ltftFeatures.qualifyingProgrammes(),
        nullValue());
  }

  @Test
  void getFeaturesShouldReturnFeaturesFromToken() {
    Jwt authToken = TestJwtUtil.createToken("""
         {
            "features": {
              "forms": {
                "ltft": {
                  "enabled": true,
                  "qualifyingProgrammes": [
                    "LTFT Programme 1",
                    "LTFT Programme 2"
                  ]
                }
              }
            }
         }
        """);

    FeaturesDto features = AuthTokenUtil.getFeatures(authToken);

    LtftFeatures ltftFeatures = features.forms().ltft();
    assertThat("Unexpected features ltft value.", ltftFeatures.enabled(), is(true));

    Set<String> qualifyingProgrammes = ltftFeatures.qualifyingProgrammes();
    assertThat("Unexpected features ltft programmes count.", qualifyingProgrammes, hasSize(2));
    assertThat("Unexpected features ltft programmes.", qualifyingProgrammes,
        hasItems("LTFT Programme 1", "LTFT Programme 2"));
  }

  @Test
  void shouldNotFailOnUnknownFeature() {
    Jwt authToken = TestJwtUtil.createToken("""
         {
            "features": {
              "unknown": "feature",
              "forms": {
                "ltft": {
                  "enabled": true,
                  "qualifyingProgrammes": [
                    "LTFT Programme 1",
                    "LTFT Programme 2"
                  ]
                }
              }
            }
         }
        """);

    assertDoesNotThrow(() -> AuthTokenUtil.getFeatures(authToken));
  }
}
