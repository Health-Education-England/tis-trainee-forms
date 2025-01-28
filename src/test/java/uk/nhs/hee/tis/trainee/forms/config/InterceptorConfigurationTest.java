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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.nhs.hee.tis.trainee.forms.interceptor.TraineeIdentityInterceptor.TRAINEE_ID_APIS;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import uk.nhs.hee.tis.trainee.forms.interceptor.TraineeIdentityInterceptor;

public class InterceptorConfigurationTest {

  InterceptorConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new InterceptorConfiguration();
  }

  @Test
  void shouldAddTraineeIdentityInterceptorToRegistry() {
    InterceptorRegistry registry = mock(InterceptorRegistry.class);
    configuration.addInterceptors(registry);

    ArgumentCaptor<MappedInterceptor> mappedInterceptorCaptor
        = ArgumentCaptor.forClass(MappedInterceptor.class);
    verify(registry).addInterceptor(mappedInterceptorCaptor.capture());

    MappedInterceptor actualMappedInterceptor = mappedInterceptorCaptor.getValue();
    String[] pathPatterns = actualMappedInterceptor.getIncludePathPatterns();
    assert pathPatterns != null;
    assertThat("Unexpected included path patterns.",
        pathPatterns.length, is(TRAINEE_ID_APIS.length));
    assertThat("Unexpected included path patterns.",
        Arrays.equals(pathPatterns, TRAINEE_ID_APIS), is(true));

    assertThat("Unexpected interceptor class.",
        actualMappedInterceptor.getInterceptor().getClass(), is(TraineeIdentityInterceptor.class));
  }
}
