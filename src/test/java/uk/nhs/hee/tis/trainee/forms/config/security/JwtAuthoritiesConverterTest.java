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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthoritiesConverterTest {

  private static final String ROLES_ATTRIBUTE = "cognito:roles";
  private static final String GROUPS_ATTRIBUTE = "cognito:groups";

  private JwtAuthoritiesConverter converter;

  @BeforeEach
  void setUp() {
    converter = new JwtAuthoritiesConverter();
  }

  @Test
  void shouldNotFailConvertingNullValues() {
    Jwt source = Jwt.withTokenValue("mock-token")
        .header("sub", UUID.randomUUID())
        .claim("claim", "value")
        .build();

    Collection<GrantedAuthority> grantedAuthorities = converter.convert(source);

    assertThat("Unexpected authority count.", grantedAuthorities, hasSize(0));
  }

  @Test
  void shouldConvertRoleClaims() {
    Jwt source = Jwt.withTokenValue("mock-token")
        .header("sub", UUID.randomUUID())
        .claim(ROLES_ATTRIBUTE, List.of("role1", "role 2"))
        .build();

    Collection<GrantedAuthority> grantedAuthorities = converter.convert(source);

    assertThat("Unexpected authority count.", grantedAuthorities, hasSize(2));

    Collection<String> authorities = grantedAuthorities.stream()
        .map(GrantedAuthority::getAuthority)
        .toList();
    assertThat("Unexpected authorities.", authorities, hasItems("ROLE_role1", "ROLE_role_2"));
  }

  @Test
  void shouldConvertGroupClaims() {
    Jwt source = Jwt.withTokenValue("mock-token")
        .header("sub", UUID.randomUUID())
        .claim(GROUPS_ATTRIBUTE, List.of("group1", "group 2"))
        .build();

    Collection<GrantedAuthority> grantedAuthorities = converter.convert(source);

    assertThat("Unexpected authority count.", grantedAuthorities, hasSize(2));

    Collection<String> authorities = grantedAuthorities.stream()
        .map(GrantedAuthority::getAuthority)
        .toList();
    assertThat("Unexpected authorities.", authorities, hasItems("ROLE_group1", "ROLE_group_2"));
  }

  @Test
  void shouldCombineConvertedRoleAndGroupClaims() {
    Jwt source = Jwt.withTokenValue("mock-token")
        .header("sub", UUID.randomUUID())
        .claim(ROLES_ATTRIBUTE, List.of("role1"))
        .claim(GROUPS_ATTRIBUTE, List.of("group1"))
        .build();

    Collection<GrantedAuthority> grantedAuthorities = converter.convert(source);

    assertThat("Unexpected authority count.", grantedAuthorities, hasSize(2));

    Collection<String> authorities = grantedAuthorities.stream()
        .map(GrantedAuthority::getAuthority)
        .toList();
    assertThat("Unexpected authorities.", authorities, hasItems("ROLE_role1", "ROLE_group1"));
  }
}
