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

package uk.nhs.hee.tis.trainee.forms.dto.enumeration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.AbstractFormR;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

public class LifecycleStateTest {

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldNotAllowLtftTransitionFromApprovedToAnything(LifecycleState state) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.APPROVED);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    assertFalse(canTransition, "Expected LTFT transition from APPROVED to " + state
        + " to be disallowed.");
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldNotAllowLtftTransitionFromRejectedToAnything(LifecycleState state) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.REJECTED);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    assertFalse(canTransition, "Expected LTFT transition from REJECTED to " + state
        +  " to be disallowed.");
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldNotAllowLtftTransitionFromWithdrawnToAnything(LifecycleState state) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.WITHDRAWN);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    assertFalse(canTransition, "Expected LTFT transition from WITHDRAWN to " + state
        +  " to be disallowed.");
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldNotAllowFormRTransitionFromDeletedToAnything(LifecycleState state) {
    //complete tests would need to verify subclasses of AbstractFormR, i.e. FormRPartA, FormRPartB
    StubFormRForm form = new StubFormRForm();
    form.setLifecycleState(LifecycleState.DELETED);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    assertFalse(canTransition, "Expected FormR transition from DELETED to " + state
        +  " to be disallowed.");
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldAllowFormTransitionFromDraftToSubmittedOnly(LifecycleState state) {
    StubForm form = new StubForm();
    form.setLifecycleState(LifecycleState.DRAFT);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    if (state != LifecycleState.SUBMITTED) {
      assertFalse(canTransition, "Expected form transition from DRAFT to " + state
          +  " to be disallowed.");
    } else {
      assertTrue(canTransition, "Expected form transition from DRAFT to " + state
          +  " to be allowed.");
    }
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldAllowFormToTransitionFromSubmittedToUnsubmitted(LifecycleState state) {
    StubForm form = new StubForm();
    form.setLifecycleState(LifecycleState.SUBMITTED);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    if (state != LifecycleState.UNSUBMITTED) {
      assertFalse(canTransition, "Expected form transition from SUBMITTED to " + state
          +  " to be disallowed.");
    } else {
      assertTrue(canTransition, "Expected form transition from SUBMITTED to " + state
          +  " to be allowed.");
    }
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldAllowFormRToTransitionFromSubmittedToDeletedOrUnsubmitted(LifecycleState state) {
    StubFormRForm form = new StubFormRForm();
    form.setLifecycleState(LifecycleState.SUBMITTED);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    if (state != LifecycleState.DELETED && state != LifecycleState.UNSUBMITTED) {
      assertFalse(canTransition, "Expected formR transition from SUBMITTED to " + state
          +  " to be disallowed.");
    } else {
      assertTrue(canTransition, "Expected formR transition from SUBMITTED to " + state
          +  " to be allowed.");
    }
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldAllowLtftToTransitionFromSubmittedToApprovedOrRejectedOrWithdrawnOrUnsubmitted(
      LifecycleState state) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.SUBMITTED);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    if (state != LifecycleState.APPROVED && state != LifecycleState.REJECTED
        && state != LifecycleState.WITHDRAWN && state != LifecycleState.UNSUBMITTED) {
      assertFalse(canTransition, "Expected LTFT transition from SUBMITTED to " + state
          +  " to be disallowed.");
    } else {
      assertTrue(canTransition, "Expected LTFT transition from SUBMITTED to " + state
          +  " to be allowed.");
    }
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldAllowFormTransitionFromUnsubmittedToSubmitted(LifecycleState state) {
    StubForm form = new StubForm();
    form.setLifecycleState(LifecycleState.UNSUBMITTED);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    if (state != LifecycleState.SUBMITTED) {
      assertFalse(canTransition, "Expected form transition from UNSUBMITTED to " + state
          +  " to be disallowed.");
    } else {
      assertTrue(canTransition, "Expected form transition from UNSUBMITTED to " + state
          +  " to be allowed.");
    }
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldAllowLtftTransitionFromUnsubmittedToWithdrawnOrSubmitted(LifecycleState state) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(LifecycleState.UNSUBMITTED);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    if (state != LifecycleState.WITHDRAWN && state != LifecycleState.SUBMITTED) {
      assertFalse(canTransition, "Expected form transition from UNSUBMITTED to " + state
          +  " to be disallowed.");
    } else {
      assertTrue(canTransition, "Expected form transition from UNSUBMITTED to " + state
          +  " to be allowed.");
    }
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldNotAllowTransitionIfCurrentStateIsNull(LifecycleState state) {
    //complete tests would need to verify all subclasses of AbstractForm
    StubForm form = new StubForm();
    form.setLifecycleState(null);

    boolean canTransition = LifecycleState.canTransitionTo(form, state);

    assertFalse(canTransition,
        "Expected form transition to be disallowed if current state is null.");
  }

  /**
   * A stub for testing the behaviour of the AbstractFormR transitions.
   */
  private static class StubFormRForm extends AbstractFormR {

    @Override
    public String getFormType() {
      return "test-formr";
    }
  }

  /**
   * A stub for testing the behaviour of the AbstractForm transitions.
   */
  private static class StubForm extends AbstractForm {

    private LifecycleState state;

    @Override
    public String getFormType() {
      return "test-form";
    }

    @Override
    public LifecycleState getLifecycleState() {
      return state;
    }

    @Override
    public void setLifecycleState(LifecycleState lifecycleState) {
      state = lifecycleState;
    }
  }
}
