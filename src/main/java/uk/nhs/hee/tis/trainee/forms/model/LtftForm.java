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
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * A LTFT form entity.
 */
@Document("LtftForm")
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class LtftForm extends AbstractAuditedForm {

  String name;
  LtftProgrammeMembership programmeMembership;
  List<LtftLifecycleStateHistory> status;

  @Override
  public String getFormType() {
    return "ltft";
  }

  /**
   * Get the current (most recent) lifecycle state.
   * @return The lifecycle state, or null if no lifecycle states exist.
   */
  @Override
  public LifecycleState getLifecycleState() {
    if (status == null || status.isEmpty()) {
      return null; //DRAFT?
    }
    return status.stream()
        .max(Comparator.comparing(LtftLifecycleStateHistory::timestamp))
        .get().state();
  }

  /**
   * Programme membership data for a LTFT application.
   *
   * @param id        The ID of the programme membership.
   * @param name      The name of the programme.
   * @param startDate The start date of the programme.
   * @param endDate   The end date of the programme.
   * @param wte       The whole time equivalent of the programme membership.
   */
  @Builder
  public record LtftProgrammeMembership(
      @Indexed
      @Field("id")
      UUID id,
      String name,
      LocalDate startDate,
      LocalDate endDate,
      double wte) {

  }

  /**
   * Lifecycle state history for a LTFT application.
   *
   * @param state     The lifecycle state.
   * @param detail    Details of what triggered the state change.
   * @param timestamp The timestamp of when the state changed.
   */
  @Builder
  public record LtftLifecycleStateHistory(
      LifecycleState state,
      String detail,
      Instant timestamp) {

  }
}
