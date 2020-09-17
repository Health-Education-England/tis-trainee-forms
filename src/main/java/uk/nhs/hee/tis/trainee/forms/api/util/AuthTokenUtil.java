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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Slf4j
public class AuthTokenUtil {

  private static final String TIS_ID_ATTRIBUTE = "custom:tisId";

  private static ObjectMapper mapper = new ObjectMapper();

  public static String getTraineeTisId(String token) throws IOException {
    String[] tokenSections = token.split("\\.");
    byte[] payloadBytes = Base64.getDecoder()
        .decode(tokenSections[1].getBytes(StandardCharsets.UTF_8));

    Map payload = mapper.readValue(payloadBytes, Map.class);
    return (String) payload.get(TIS_ID_ATTRIBUTE);
  }

  public static <T> Optional<ResponseEntity<T>> verifyTraineeTisId(String requestedTraineeTisId,
      String token) {
    ResponseEntity<T> responseEntity = null;

    String tokenTraineeTisId;
    try {
      tokenTraineeTisId = getTraineeTisId(token);

      if (!requestedTraineeTisId.equals(tokenTraineeTisId)) {
        log.warn("The form's trainee TIS ID did not match authenticated user.");
        responseEntity = ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    } catch (IOException e) {
      log.warn("Unable to read tisId from token.", e);
      responseEntity = ResponseEntity.badRequest().build();
    }

    return Optional.ofNullable(responseEntity);
  }
}
