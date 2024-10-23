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

package uk.nhs.hee.tis.trainee.forms.config;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.amazonaws.xray.jakarta.servlet.AWSXRayServletFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import uk.nhs.hee.tis.trainee.forms.config.filter.SignedDataFilter;

class FilterConfigurationTest {

  private static final String DAEMON_PROPERTY = "com.amazonaws.xray.emitters.daemon-address";

  private static final String SIGNATURE_SECRET_KEY = "test-secret-key";

  FilterConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new FilterConfiguration();
  }

  @Test
  void shouldNotRegisterTracingFilterWhenDaemonAddressNotSet() {
    ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(FilterConfiguration.class)
        .withBean(SignedDataFilter.class, () -> new SignedDataFilter(null, null));

    runner
        .withPropertyValues(DAEMON_PROPERTY + "=")
        .run(context -> assertThat("Unexpected bean presence.",
            context.containsBean("registerTracingFilter"), is(false)));
  }

  @Test
  void shouldRegisterTracingFilterWhenDaemonAddressSet() {
    ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(FilterConfiguration.class)
        .withBean(SignedDataFilter.class, () -> new SignedDataFilter(null, null));

    runner
        .withPropertyValues(DAEMON_PROPERTY + "=https://localhost:1234")
        .run(context -> assertAll(
            () -> assertThat("Unexpected bean presence.",
                context.containsBean("registerTracingFilter"), is(true)),
            () -> assertThat("Unexpected bean type.", context.getBean("registerTracingFilter"),
                instanceOf(FilterRegistrationBean.class))
        ));
  }

  @Test
  void shouldRegisterTracingFilter() {
    var registrationBean = configuration.registerTracingFilter("test");
    AWSXRayServletFilter registeredFilter = registrationBean.getFilter();
    assertThat("Unexpected registered filter.", registeredFilter, notNullValue());
  }

  @Test
  void shouldRegisterSignedDataFilter() {
    SignedDataFilter filter = new SignedDataFilter(new ObjectMapper(), SIGNATURE_SECRET_KEY);

    var registrationBean = configuration.registerSignedDataFilter(filter);

    SignedDataFilter registeredFilter = registrationBean.getFilter();
    assertThat("Unexpected registered filter.", registeredFilter, sameInstance(filter));
  }

  @Test
  void shouldRegisterSignedDataFilterOnCojEndpoints() {
    SignedDataFilter filter = new SignedDataFilter(new ObjectMapper(), SIGNATURE_SECRET_KEY);

    var registrationBean = configuration.registerSignedDataFilter(filter);

    Collection<String> urlPatterns = registrationBean.getUrlPatterns();
    assertThat("Unexpected filter patterns.", urlPatterns, hasItem("/api/coj"));
  }
}
