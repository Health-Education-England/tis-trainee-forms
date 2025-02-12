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

package uk.nhs.hee.tis.trainee.forms.model;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

class LtftFormTest {

  @Test
  void shouldGetFormType() {
    assertThat("Unexpected form type.", new LtftForm().getFormType(), is("ltft"));
  }

  @Test
  void shouldGetLifecycleStateIfNull() {
    assertThat("Unexpected lifecycle state.", new LtftForm().getLifecycleState(), is(nullValue()));
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldGetLifecycleState(LifecycleState s) {
    LtftForm form = new LtftForm();
    List<LtftForm.LtftStatusInfo> history = List.of(
        new LtftForm.LtftStatusInfo(LifecycleState.SUBMITTED, "test", Instant.now(), null));
    form.setStatus(new LtftForm.LtftStatus(s, history));
    assertThat("Unexpected lifecycle state.", form.getLifecycleState(), is(s));
  }
}
