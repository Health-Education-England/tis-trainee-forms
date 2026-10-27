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

import jakarta.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;

/**
 * A LTFT form entity.
 *
 * <p>The responsible role index is declared here, rather than on the shared status record, so
 * that it is only created for the LTFT collection and not for other audited form collections.
 */
@Document("LtftForm")
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@CompoundIndex(name = LtftForm.RESPONSIBLE_ROLE_FIELD, def = "{'"
    + LtftForm.RESPONSIBLE_ROLE_FIELD + "': 1}")
public class LtftForm extends AbstractAuditedForm<LtftContent> {

  public static final String RESPONSIBLE_ROLE_FIELD = "status.current.responsibleRole";

  /**
   * Resolve the role responsible for progressing the form for the given status entry.
   *
   * @param statusInfo The status entry to resolve the responsible role for.
   * @return The responsible role, or null if no role is responsible (e.g. DRAFT or a final state).
   */
  @Nullable
  @Override
  public ResponsibleRole resolveResponsibleRole(StatusInfo statusInfo) {
    LifecycleState lifecycleState = statusInfo.state();

    if (lifecycleState == null) {
      return null;
    }

    return switch (lifecycleState) {
      case UNSUBMITTED -> ResponsibleRole.TRAINEE;
      // TODO: pre-approver - return REVIEWER when a pre-approver is assigned and
      //  statusInfo.reviewOutcome() is null (i.e. the pre-approval is not complete), otherwise
      //  fall through to ADMIN. The pre-approver is distinct from statusInfo.assignedReviewer().
      case SUBMITTED, UNDER_REVIEW -> ResponsibleRole.ADMIN;
      default -> null;
    };
  }

  @Override
  public String getFormType() {
    return "ltft";
  }

  @Override
  public String getFormReferencePrefix() {
    return "ltft";
  }
}
