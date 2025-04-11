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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.index.Indexed;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusDetail;
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
  @JsonIgnore
  public LifecycleState getLifecycleState() {
    if (status == null || status.current == null) {
      return null;
    }
    return status.current.state;
  }

  /**
   * Set the current assigned admin of the form, appending to the status history.
   *
   * @param admin      The new assigned admin.
   * @param modifiedBy The Person who assigned the new admin.
   */
  public void setAssignedAdmin(Person admin, Person modifiedBy) {
    StatusInfo statusInfo = StatusInfo.builder()
        .state(getLifecycleState())
        .detail(status == null || status.current == null ? null : status.current.detail)
        .assignedAdmin(admin)
        .modifiedBy(modifiedBy)
        .timestamp(Instant.now())
        .revision(status == null || status.current == null ? null : status.current.revision)
        .build();

    updateStatusInfo(statusInfo);
  }

  /**
   * Set the current lifecycle state of the form, appending to the status history.
   *
   * @param lifecycleState The new lifecycle state. The current revision number will be used and
   *                       other details set to null.
   */
  @Override
  public void setLifecycleState(LifecycleState lifecycleState) {
    setLifecycleState(lifecycleState, null, null, revision);
  }

  /**
   * Set the current lifecycle state of the form, appending to the status history. This does not
   * consider whether a state transition is valid or not, it simply sets the state.
   *
   * @param lifecycleState The new lifecycle state.
   * @param detail         Any status detail.
   * @param modifiedBy     The Person who made this status change.
   * @param revision       The revision number associated with this status change.
   */
  public void setLifecycleState(LifecycleState lifecycleState, StatusDetail detail,
      Person modifiedBy, int revision) {
    StatusInfo statusInfo = StatusInfo.builder()
        .state(lifecycleState)
        .detail(detail)
        .assignedAdmin(
            status == null || status.current == null ? null : status.current.assignedAdmin)
        .modifiedBy(modifiedBy)
        .timestamp(Instant.now())
        .revision(revision)
        .build();

    updateStatusInfo(statusInfo);
  }

  /**
   * Update the form's current and historical status to include in given {@link StatusInfo}.
   *
   * @param statusInfo The status info to add to the form.
   */
  private void updateStatusInfo(StatusInfo statusInfo) {
    Instant submitted;

    if (statusInfo.state == LifecycleState.SUBMITTED) {
      submitted = statusInfo.timestamp;
    } else {
      submitted = status != null ? status.submitted : null;
    }

    if (status == null || status.history == null) {
      setStatus(new Status(statusInfo, submitted, List.of(statusInfo)));
    } else {
      List<StatusInfo> newHistory = new ArrayList<>(status.history);
      newHistory.add(statusInfo);
      setStatus(new Status(statusInfo, submitted, newHistory));
    }
  }

  @Override
  @JsonIgnore
  public boolean isNew() {
    return created == null;
  }

  /**
   * The form status.
   *
   * @param current   The information for the current form status.
   * @param submitted When the form was last submitted.
   * @param history   A list of form status history.
   */
  @Builder
  public record Status(

      StatusInfo current,
      Instant submitted,
      List<StatusInfo> history) {

    /**
     * Form status information.
     *
     * @param state         The lifecycle state of the form.
     * @param detail        Any status reason detail.
     * @param assignedAdmin The admin who is assigned to process the form.
     * @param modifiedBy    The Person who made this status change.
     * @param timestamp     The timestamp of the status change.
     * @param revision      The revision number associated with this status change.
     */
    @Builder
    public record StatusInfo(

        @Indexed
        LifecycleState state,
        StatusDetail detail,
        Person assignedAdmin,
        Person modifiedBy,
        Instant timestamp,
        Integer revision
    ) {

    }

    /**
     * Form status reason detail.
     *
     * @param reason  The reason for the status.
     * @param message Any additional message.
     */
    @Builder
    public record StatusDetail(String reason, String message) {

    }
  }
}
