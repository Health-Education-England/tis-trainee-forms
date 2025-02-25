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
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.With;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.Indexed;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.content.FormContent;

/**
 * An abstract for audited forms, which include status history, revisions and create/modify
 * timestamps.
 *
 * @param <T> The type of the {@link FormContent}.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractAuditedForm<T extends FormContent> extends AbstractForm implements
    Persistable<UUID> {

  @Indexed
  private String formRef;
  private int revision;

  private T content;
  private Status status;

  @CreatedDate
  private Instant created;

  @LastModifiedDate
  private Instant lastModified;

  /**
   * Get the current (most recent) lifecycle state.
   *
   * @return The lifecycle state, or null if no lifecycle states exist.
   */
  @Override
  public LifecycleState getLifecycleState() {
    if (status == null || status.current == null) {
      return null;
    }
    return status.current.state;
  }

  @Override
  public boolean isNew() {
    return created == null;
  }

  /**
   * Get the submission timestamp for the form.
   *
   * @return The submitted timestamp, or null if not submitted.
   */
  public Instant getSubmitted() {
    if (status == null || status.history == null) {
      return null;
    }

    return status.history.stream()
        .filter(h -> h.state == LifecycleState.SUBMITTED)
        .max(Comparator.comparing(StatusInfo::timestamp))
        .map(StatusInfo::timestamp)
        .orElse(null);
  }

  /**
   * The form status.
   *
   * @param current The information for the current form status.
   * @param history A list of form status history.
   */
  @Builder
  public record Status(

      @With
      StatusInfo current,
      List<StatusInfo> history) {

    /**
     * Form status information.
     *
     * @param state      The lifecycle state of the form.
     * @param detail     Any status detail.
     * @param modifiedBy The Person who made this status change.
     * @param timestamp  The timestamp of the status change.
     * @param revision   The revision number associated with this status change.
     */
    @Builder
    public record StatusInfo(

        @Indexed
        LifecycleState state,
        StatusDetail detail,
        Person modifiedBy,
        Instant timestamp,
        Integer revision
    ) {

      @Builder
      public record StatusDetail(String reason, String message) {

      }
    }
  }
}
