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
 *  substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 *  NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 *  DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.hee.tis.trainee.forms.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@Document("LtftForm")
@Builder
public record LtftForm(
  @Id
  ObjectId id,
  @Indexed
  String traineeId,

  String name,
  LtftProgrammeMembership programmeMembership,
  LifecycleState status,

  @CreatedDate
  Instant created,

  @LastModifiedDate
  Instant lastModified
) {

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
}
