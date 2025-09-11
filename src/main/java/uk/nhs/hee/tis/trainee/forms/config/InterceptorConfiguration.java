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

package uk.nhs.hee.tis.trainee.forms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.interceptor.AdminIdentityInterceptor;
import uk.nhs.hee.tis.trainee.forms.interceptor.TraineeIdentityInterceptor;

/**
 * Configuration for interceptors.
 */
@Configuration
public class InterceptorConfiguration implements WebMvcConfigurer {

  // Endpoints are a mix of authenticated (public) and unauthenticated (internal), limit
  // trainee ID verification to LTFT, COJ and FormR endpoints for now.
  protected static final String[] TRAINEE_ID_APIS = {
      "/api/coj",
      "/api/formr-parta", "/api/formr-partas", "/api/formr-parta-pdf", "/api/formr-parta/**",
      "/api/formr-partb", "/api/formr-partbs", "/api/formr-partb-pdf", "/api/formr-partb/**",
      "/api/ltft", "/api/ltft/**"
  };

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(adminIdentityInterceptor()).addPathPatterns("/api/admin/**");
    registry.addInterceptor(traineeIdentityInterceptor()).addPathPatterns(TRAINEE_ID_APIS);
  }

  /**
   * Create an interceptor for creating a {@link AdminIdentity} from a request.
   *
   * @return The admin identity interceptor.
   */
  @Bean
  public AdminIdentityInterceptor adminIdentityInterceptor() {
    return new AdminIdentityInterceptor(adminIdentity());
  }

  /**
   * Create a {@link AdminIdentity} for each request.
   *
   * @return The created admin identity.
   */
  @Bean
  @RequestScope
  public AdminIdentity adminIdentity() {
    return new AdminIdentity();
  }

  /**
   * Create an interceptor for creating a {@link TraineeIdentity} from a request.
   *
   * @return The trainee identity interceptor.
   */
  @Bean
  public TraineeIdentityInterceptor traineeIdentityInterceptor() {
    return new TraineeIdentityInterceptor(traineeIdentity());
  }

  /**
   * Create a {@link TraineeIdentity} for each request.
   *
   * @return The created trainee identity.
   */
  @Bean
  @RequestScope
  public TraineeIdentity traineeIdentity() {
    return new TraineeIdentity();
  }
}
