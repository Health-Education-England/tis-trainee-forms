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

import java.time.LocalDate;
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
  LtftDiscussions discussions;

  LifecycleState status;

  @Override
  public String getFormType() {
    return "ltft";
  }

  @Override
  public LifecycleState getLifecycleState() {
    return status;
  }

  /**
   * Programme membership data for a calculation.
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
   * Details of the people who have been approached to discuss a LTFT application, including TPD.
   *
   * @param tpdName  The Training Programme Director.
   * @param tpdEmail The email for the TPD.
   * @param other    The list of other people who have been contacted.
   */
  @Builder
  public record LtftDiscussions(
      String tpdName,
      String tpdEmail,
      List<LtftPersonRole> other) {

  }

  /**
   * Details of other people involved in the discussion.
   *
   * @param name  Person name.
   * @param email Person email.
   * @param role  Their role.
   */
  @Builder
  public record LtftPersonRole(
      String name,
      String email,
      String role) {

  }
}
