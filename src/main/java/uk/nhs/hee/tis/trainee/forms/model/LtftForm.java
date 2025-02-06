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
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * A LTFT form entity.
 */
@Document("LtftForm")
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LtftForm extends AbstractAuditedForm implements Persistable<UUID> {


  private String name;
  private LtftProgrammeMembership programmeMembership;

  private LtftDiscussions discussions;

  @CreatedDate
  private Instant created;

  @LastModifiedDate
  private Instant lastModified;

  @Override
  public boolean isNew() {
    return created == null;
  }

  @Override
  public String getFormType() {
    return "ltft";
  }

  /**
   * Programme membership data for a LTFT calculation.
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
   */
  @Builder
  public record LtftDiscussions(
      String tpdName,
      String tpdEmail,
      List<LtftPersonRole> other) {

  }

  /**
   * Details of other people involved in the discussion.
   */
  @Builder
  public record LtftPersonRole(
      String name,
      String email,
      String role) {

  }
}
