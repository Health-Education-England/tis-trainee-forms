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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts a JWT token in to Spring authorities.
 */
public class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final String COGNITO_ROLES = "cognito:roles";
  private static final String COGNITO_GROUPS = "cognito:groups";

  @Override
  public Collection<GrantedAuthority> convert(Jwt source) {
    List<String> roles = source.getClaimAsStringList(COGNITO_ROLES);
    List<String> groups = source.getClaimAsStringList(COGNITO_GROUPS);

    return Stream.concat(
            Stream.ofNullable(roles).flatMap(List::stream),
            Stream.ofNullable(groups).flatMap(List::stream))
        .map(s -> "ROLE_" + s.replace(" ", "_"))
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toSet());
  }
}
