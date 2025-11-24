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
 *
 */

package uk.nhs.hee.tis.trainee.forms.config.security;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class SecurityConfigurationTest {

  private static final String STRING_ATTRIBUTE = "custom:my-string";
  private static final String ARRAY_ATTRIBUTE = "my-array";

  private static final String TOKEN_HEADER = Base64.getEncoder().encodeToString("""
      {
        "alg": "HS256",
        "type": "JWT"
      }
      """.getBytes());
  private static final String TOKEN_SIGNATURE = Base64.getEncoder().encodeToString("{}".getBytes());
  private static final String TOKEN_TEMPLATE = TOKEN_HEADER + ".%s." + TOKEN_SIGNATURE;

  private SecurityConfiguration configuration;
  private JwtDecoder decoder;

  @BeforeEach
  void setUp() {
    configuration = new SecurityConfiguration();
    decoder = configuration.unsafeJwtDecoder();
  }

  @Test
  void shouldThrowExceptionDecodingWhenPayloadNotMap() {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("[]".getBytes(StandardCharsets.UTF_8));
    String token = String.format(TOKEN_TEMPLATE, encodedPayload);

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(token));
  }

  @Test
  void shouldThrowExceptionDecodingWhenPayloadEmpty() {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("{}".getBytes(StandardCharsets.UTF_8));
    String token = String.format(TOKEN_TEMPLATE, encodedPayload);

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(token));
  }

  @Test
  void shouldDecodeEmptyArray() {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("""
             {
                "my-array": []
             }
            """
            .getBytes(StandardCharsets.UTF_8));
    String token = String.format(TOKEN_TEMPLATE, encodedPayload);

    Jwt jwt = decoder.decode(token);

    assertThat("Unexpected claim size.", jwt.getClaim(ARRAY_ATTRIBUTE), hasSize(0));
  }

  @Test
  void shouldDecodeArrayWithUrlCharactersInToken() {
    // The payload is specifically crafted to include an underscore, the group is 123.
    String token = TOKEN_HEADER + ".eyJteS1hcnJheSI6WyIxMjMiXSwibmFtZSI6IkpvaG4gRG_DqyJ9.c2ln";

    Jwt jwt = decoder.decode(token);

    assertThat("Unexpected claim size.", jwt.getClaim(ARRAY_ATTRIBUTE), hasSize(1));
    assertThat("Unexpected claim value.", jwt.getClaim(ARRAY_ATTRIBUTE), hasItem("123"));
  }

  @Test
  void shouldDecodeStringWithUrlCharactersInToken() {
    // The payload is specifically crafted to include an underscore, the ID is 123.
    String token =
        TOKEN_HEADER + ".eyJjdXN0b206bXktc3RyaW5nIjoiMTIzIiwibmFtZSI6IkpvaG4gRG_DqyJ9.c2ln";

    Jwt jwt = assertDoesNotThrow(() -> decoder.decode(token));

    assertThat("Unexpected claim.", jwt.getClaim(STRING_ATTRIBUTE), is("123"));
  }

  @Test
  void shouldDecodeArray() {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("""
             {
                "my-array": [
                  "123456",
                  "ABCDEF"
                ]
             }
            """
            .getBytes(StandardCharsets.UTF_8));
    String token = String.format(TOKEN_TEMPLATE, encodedPayload);

    Jwt jwt = decoder.decode(token);

    assertThat("Unexpected claim size.", jwt.getClaim(ARRAY_ATTRIBUTE), hasSize(2));
    assertThat("Unexpected claim values.", jwt.getClaim(ARRAY_ATTRIBUTE),
        hasItems("123456", "ABCDEF"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"iat", "exp", "nbf", "auth_time"})
  void shouldDecodeTimestamps(String timestampClaim) {
    Instant now = Instant.now();
    String payload = String.format("{\"%s\":%s}", timestampClaim, now.getEpochSecond());
    String encodedPayload = Base64.getEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String token = String.format(TOKEN_TEMPLATE, encodedPayload);

    Jwt jwt = decoder.decode(token);

    assertThat("Unexpected claim value.", jwt.getClaimAsInstant(timestampClaim),
        is(now.truncatedTo(SECONDS)));
  }

  @Test
  void shouldDecodeString() {
    String payload = String.format("{\"%s\":\"%s\"}", STRING_ATTRIBUTE, "40");
    String encodedPayload = Base64.getEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String token = String.format(TOKEN_TEMPLATE, encodedPayload);

    Jwt jwt = decoder.decode(token);

    assertThat("Unexpected claim value.", jwt.getClaim(STRING_ATTRIBUTE), is("40"));
  }

  @Test
  void shouldRegisterAuthConverter() {
    JwtAuthenticationConverter authConverter = configuration.jwtAuthenticationConverter();

    assertThat("Unexpected JWT converter.", authConverter,
        instanceOf(JwtAuthenticationConverter.class));
  }
}
