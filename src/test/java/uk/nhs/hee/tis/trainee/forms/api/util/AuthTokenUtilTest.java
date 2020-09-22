/*
 * The MIT License (MIT)
 *
 * Copyright 2020 Crown Copyright (Health Education England)
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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthTokenUtilTest {

  private static final String TIS_ID_ATTRIBUTE = "custom:tisId";

  @Test
  void getTraineeTisIdShouldThrowExceptionWhenTokenPayloadNotMap() {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("[]".getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    assertThrows(IOException.class, () -> AuthTokenUtil.getTraineeTisId(token));
  }

  @Test
  void getTraineeTisIdShouldReturnNullWhenTisIdNotInToken() throws IOException {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("{}".getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    String tisId = AuthTokenUtil.getTraineeTisId(token);

    assertThat("Unexpected trainee TIS ID", tisId, nullValue());
  }

  @Test
  void getTraineeTisIdShouldReturnIdWhenTisIdInToken() throws IOException {
    String payload = String.format("{\"%s\":\"%s\"}", TIS_ID_ATTRIBUTE, "40");
    String encodedPayload = Base64.getEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    String tisId = AuthTokenUtil.getTraineeTisId(token);

    assertThat("Unexpected trainee TIS ID", tisId, is("40"));
  }

  @Test
  void verifyTraineeTisIdShouldReturnBadRequestWhenTokenIdNotReadable() {
    String encodedPayload = Base64.getEncoder()
        .encodeToString("[]".getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    Optional<ResponseEntity<Object>> response = AuthTokenUtil.verifyTraineeTisId("1", token);

    assertThat("Unexpected response isPresent.", response.isPresent(), is(true));
    assertThat("Unexpected response status.", response.get().getStatusCode(),
        is(HttpStatus.BAD_REQUEST));
  }

  @Test
  void verifyTraineeTisIdShouldReturnForbiddenWhenTokenIdNotMatching() {
    String payload = String.format("{\"%s\":\"%s\"}", TIS_ID_ATTRIBUTE, "40");
    String encodedPayload = Base64.getEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    Optional<ResponseEntity<Object>> response = AuthTokenUtil.verifyTraineeTisId("1", token);

    assertThat("Unexpected response isPresent.", response.isPresent(), is(true));
    assertThat("Unexpected response status.", response.get().getStatusCode(),
        is(HttpStatus.FORBIDDEN));
  }

  @Test
  void verifyTraineeTisIdShouldReturnEmptyWhenTokenIdMatching() {
    String payload = String.format("{\"%s\":\"%s\"}", TIS_ID_ATTRIBUTE, "40");
    String encodedPayload = Base64.getEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String token = String.format("aa.%s.cc", encodedPayload);

    Optional<ResponseEntity<Object>> response = AuthTokenUtil.verifyTraineeTisId("40", token);

    assertThat("Unexpected response isPresent.", response.isPresent(), is(false));
  }
}
