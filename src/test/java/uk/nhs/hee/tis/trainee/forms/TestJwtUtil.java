/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/**
 * A utility for generating test JWT tokens.
 */
public class TestJwtUtil {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static final String TIS_ID_ATTRIBUTE = "custom:tisId";

  /**
   * Create a token with the TIS ID attribute as the payload.
   *
   * @param traineeTisId The TIS ID to inject in to the payload.
   * @return The generated token.
   */
  public static Jwt createTokenForTisId(String traineeTisId) {
    String payload = String.format("{\"%s\":\"%s\"}", TIS_ID_ATTRIBUTE, traineeTisId);
    return createToken(payload);
  }

  /**
   * Create a token with the given claims.
   *
   * @param claimsJson The claims as a JSON string.
   * @return The built token.
   */
  public static Jwt createToken(String claimsJson) {
    try {
      Map<String, Object> claims = MAPPER.readValue(claimsJson, new TypeReference<>() {
      });
      claims.put(JwtClaimNames.SUB, UUID.randomUUID());

      return Jwt.withTokenValue("mock-token")
          .header("alg", "none")
          .claims(c -> c.putAll(claims))
          .build();
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
