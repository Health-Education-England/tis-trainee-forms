/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;

class AbstractFormMongoEventListenerTest {

  private AbstractFormMongoEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new AbstractFormMongoEventListener();
  }

  @Test
  void shouldPopulateIdBeforeConvertWhenIdNull() {
    StubForm form = new StubForm();

    BeforeConvertEvent<AbstractForm> event = new BeforeConvertEvent<>(form, "StubForm");
    listener.onBeforeConvert(event);

    UUID id = form.getId();
    assertThat("Unexpected form ID.", id, notNullValue());
  }

  @Test
  void shouldNotModifyIdBeforeConvertWhenIdPopulated() {
    StubForm form = new StubForm();
    UUID uuid = UUID.randomUUID();
    form.setId(uuid);

    BeforeConvertEvent<AbstractForm> event = new BeforeConvertEvent<>(form, "StubForm");
    listener.onBeforeConvert(event);

    assertThat("Unexpected form ID.", form.getId(), is(uuid));
  }

  /**
   * A stub for testing the behaviour of the AbstractForm event listener.
   */
  private static class StubForm extends AbstractFormR {

    @Override
    public String getFormType() {
      return "test-form";
    }
  }
}
