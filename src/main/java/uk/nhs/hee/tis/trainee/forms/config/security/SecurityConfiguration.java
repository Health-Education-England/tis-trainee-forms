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

import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Application security configuration.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  /**
   * Configure the security filter chain.
   *
   * @param http The HTTP security configuration.
   * @return The built configuration chain.
   * @throws Exception If the chain could not be built.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            // TODO: split public and internal endpoints, then require authenticated.
            .anyRequest().permitAll()
        )
        // TODO: remove jwtPopulateFilter() when authenticated endpoints are configured.
        .addFilterBefore(jwtPopulateFilter(), UsernamePasswordAuthenticationFilter.class)
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
        )
        .build();
  }

  /**
   * A temporary measure to ensure that the security context gets populated, even though
   * unauthenticated requests are still permitted.
   *
   * @return The temporary filter.
   */
  @Bean
  public OncePerRequestFilter jwtPopulateFilter() {
    // TODO: remove when authenticated endpoints are fully configured.
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain)
          throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
          try {
            Jwt jwt = unsafeJwtDecoder().decode(header);
            Authentication auth = jwtAuthenticationConverter().convert(jwt);
            SecurityContextHolder.getContext().setAuthentication(auth);
          } catch (Exception e) {
            // ignore for now, leave SecurityContext empty
          }
        }
        filterChain.doFilter(request, response);
      }
    };
  }

  /**
   * A temporary decoder, which skips token verification (same as before). This gives us an
   * incremental move towards a more secure service while further refactoring to separate endpoints
   * is done.
   *
   * @return A JWT Decoder.
   */
  @Bean
  public JwtDecoder unsafeJwtDecoder() {
    // TODO: replace with safe JWT Decoder.
    return token -> {
      try {
        SignedJWT signedJwt = SignedJWT.parse(token);
        Map<String, Object> tokenClaims = signedJwt.getJWTClaimsSet().getClaims();

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
            .claims(claims -> claims.putAll(tokenClaims))
            .build();

        // Convert handles Date -> Instant conversion for common timestamps.
        Map<String, Object> convertedClaims = MappedJwtClaimSetConverter.withDefaults(Map.of())
            .convert(claimsSet.getClaims());

        return Jwt.withTokenValue(token)
            .headers(headers -> headers.putAll(signedJwt.getHeader().toJSONObject()))
            .claims(claims -> claims.putAll(convertedClaims))
            .build();
      } catch (ParseException e) {
        throw new JwtException("Failed to parse JWT.", e);
      }
    };
  }

  /**
   * A converter between JWT and Spring authorities.
   *
   * @return The converter.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new JwtAuthoritiesConverter());
    return converter;
  }
}
