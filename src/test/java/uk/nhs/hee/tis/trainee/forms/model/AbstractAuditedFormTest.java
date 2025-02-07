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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

class AbstractAuditedFormTest {

  @Test
  void shouldConsiderFormWithoutCreatedAsNew() {
    StubForm stubForm = new StubForm();
    assertThat("Unexpected isNew.", stubForm.isNew(), is(true));
  }

  @Test
  void shouldNotConsiderFormWithCreatedAsNew() {
    StubForm stubForm = new StubForm();
    stubForm.setCreated(Instant.now());
    assertThat("Unexpected isNew.", stubForm.isNew(), is(false));
  }

  /**
   * A stub for testing the behaviour of the AbstractForm event listener.
   */
  private static class StubForm extends AbstractAuditedForm {

    @Override
    public String getFormType() {
      return "test-auditedform";
    }

    @Override
    public LifecycleState getLifecycleState() {
      return null;
    }

  }
}
