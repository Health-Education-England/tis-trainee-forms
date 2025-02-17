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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.config.InterceptorConfiguration.TRAINEE_ID_APIS;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import uk.nhs.hee.tis.trainee.forms.interceptor.AdminIdentityInterceptor;
import uk.nhs.hee.tis.trainee.forms.interceptor.TraineeIdentityInterceptor;

/**
 * A test class for the Interceptor configuration.
 */
class InterceptorConfigurationTest {

  InterceptorConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new InterceptorConfiguration();
  }

  @Test
  void shouldAddAdminIdentityInterceptorToRegistry() {
    InterceptorRegistry registry = mock(InterceptorRegistry.class);
    when(registry.addInterceptor(any())).thenReturn(mock(InterceptorRegistration.class));

    InterceptorRegistration registration = mock(InterceptorRegistration.class);
    when(registry.addInterceptor(any(AdminIdentityInterceptor.class))).thenReturn(registration);

    configuration.addInterceptors(registry);

    ArgumentCaptor<AdminIdentityInterceptor> interceptorCaptor = ArgumentCaptor.captor();
    verify(registry).addInterceptor(interceptorCaptor.capture());

    AdminIdentityInterceptor interceptor = interceptorCaptor.getValue();
    assertThat("Unexpected interceptor class.", interceptor,
        instanceOf(AdminIdentityInterceptor.class));

    ArgumentCaptor<String[]> pathPatternsCaptor = ArgumentCaptor.captor();
    verify(registration).addPathPatterns(pathPatternsCaptor.capture());

    String[] pathPatterns = pathPatternsCaptor.getValue();
    assert pathPatterns != null;
    assertThat("Unexpected included path patterns count.", pathPatterns.length, is(1));
    assertThat("Unexpected included path patterns.", pathPatterns,
        is(new String[]{"/api/admin/**"}));
  }

  @Test
  void shouldAddTraineeIdentityInterceptorToRegistry() {
    InterceptorRegistry registry = mock(InterceptorRegistry.class);
    when(registry.addInterceptor(any())).thenReturn(mock(InterceptorRegistration.class));

    InterceptorRegistration registration = mock(InterceptorRegistration.class);
    when(registry.addInterceptor(any(TraineeIdentityInterceptor.class))).thenReturn(registration);

    configuration.addInterceptors(registry);

    ArgumentCaptor<TraineeIdentityInterceptor> interceptorCaptor = ArgumentCaptor.captor();
    verify(registry).addInterceptor(interceptorCaptor.capture());

    TraineeIdentityInterceptor interceptor = interceptorCaptor.getValue();
    assertThat("Unexpected interceptor class.", interceptor,
        instanceOf(TraineeIdentityInterceptor.class));

    ArgumentCaptor<String[]> pathPatternsCaptor = ArgumentCaptor.captor();
    verify(registration).addPathPatterns(pathPatternsCaptor.capture());

    String[] pathPatterns = pathPatternsCaptor.getValue();
    assert pathPatterns != null;
    assertThat("Unexpected included path patterns.",
        pathPatterns.length, is(TRAINEE_ID_APIS.length));
    assertThat("Unexpected included path patterns.", pathPatterns, is(TRAINEE_ID_APIS));
  }
}
