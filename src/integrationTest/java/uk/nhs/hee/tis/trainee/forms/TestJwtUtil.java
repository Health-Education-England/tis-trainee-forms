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

package uk.nhs.hee.tis.trainee.forms;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A utility for generating test JWT tokens.
 */
public class TestJwtUtil {

  public static final String TIS_ID_ATTRIBUTE = "custom:tisId";
  public static final String EMAIL_ATTRIBUTE = "email";
  public static final String GIVEN_NAME_ATTRIBUTE = "given_name";
  public static final String FAMILY_NAME_ATTRIBUTE = "family_name";
  public static final String FEATURES_ATTRIBUTE = "features";
  public static final String FEATURES_LTFT_PROGRAMME = "607ddcfd-bbe9-4835-b795-ca26aebba990";

  /**
   * Generate a token with the given payload.
   *
   * @param payload The payload to inject in to the token.
   * @return The generated token.
   */
  public static String generateToken(String payload) {
    String encodedPayload = Base64.getUrlEncoder()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return String.format("aGVhZGVy.%s.c2lnbmF0dXJl", encodedPayload);
  }

  /**
   * Generate a token with the various attributes as the payload.
   *
   * @param traineeTisId The TIS ID to inject in to the payload.
   * @param email        The email to inject in to the payload.
   * @param givenName    The given name to inject in to the payload.
   * @param familyName   The family name to inject in to the payload.
   * @return The generated token.
   */
  public static String generateTokenForTrainee(String traineeTisId, String email, String givenName,
      String familyName) {
    String features = String.format("{\"ltft\":true,"
        + "\"ltftProgrammes\":[\"%s\"]}", FEATURES_LTFT_PROGRAMME);
    String payload = String.format("{\"%s\":\"%s\"", TIS_ID_ATTRIBUTE, traineeTisId)
        + (email == null ? "" : String.format(",\"%s\":\"%s\"", EMAIL_ATTRIBUTE, email)
        + (givenName == null ? "" : String.format(",\"%s\":\"%s\"", GIVEN_NAME_ATTRIBUTE, givenName)
        + (familyName == null ? "" : String.format(",\"%s\":\"%s\"", FAMILY_NAME_ATTRIBUTE,
        familyName))
        + String.format(",\"%s\":%s", FEATURES_ATTRIBUTE, features)))
        + "}"; // :tears:
    return generateToken(payload);
  }

  /**
   * Generate a token with default values for the various attributes as the payload.
   *
   * @param traineeTisId The TIS ID to inject in to the payload.
   * @return The generated token.
   */
  public static String generateTokenForTrainee(String traineeTisId) {
    return generateTokenForTrainee(traineeTisId, "email", "givenName", "familyName");
  }

  /**
   * Generate an admin token with the given groups and default attributes for other fields.
   *
   * @param groups The groups to add in to the token.
   * @return The generated token.
   */
  public static String generateAdminTokenForGroups(List<String> groups) {
    String groupString = groups.stream()
        .map(s -> s.replaceAll("^|$", "\""))
        .collect(Collectors.joining(","));

    String payload = """
        {
          "email": "ad.min@example.com",
          "given_name": "Ad",
          "family_name": "Min",
          "cognito:groups": [%s],
          "cognito:roles": ["NHSE LTFT Admin"]
        }
        """.formatted(groupString);

    return generateToken(payload);
  }
}
