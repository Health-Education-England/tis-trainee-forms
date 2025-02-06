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

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractAuditedForm extends AbstractForm {

  private List<LifecycleStateHistory> status;

  @Override
  public LifecycleState getLifecycleState() {
    return status.stream()
        .max(Comparator.comparing(LifecycleStateHistory::timestamp))
        .map(LifecycleStateHistory::state)
        .orElseGet(null);
  }

  @Builder
  public record LifecycleStateHistory(
      LifecycleState state,
      String detail,
      Instant timestamp) {

  }
}
