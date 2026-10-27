/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.migration;

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNDER_REVIEW;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

/**
 * Populate the responsible role for the current status and every status history entry of
 * existing LTFT forms.
 *
 * <p>The role is derived from each entry's own lifecycle state using a fixed copy of the rule as
 * it stood when this migration was written, deliberately not delegating to
 * {@link LtftForm#resolveResponsibleRole}. Pre-approvers and review outcomes did not exist before
 * this migration (the assigned reviewer is a distinct concept and does not affect the role), so
 * state alone is an exact derivation for historical entries, whereas later pre-approval dependent
 * rules must never be applied retrospectively.
 *
 * <p>Only entries without a responsible role are updated, so values recorded by the application
 * are never overwritten and the migration is safe to re-run. Bulk partial updates are used, which
 * do not modify the form's last modified timestamp. No events are published, to avoid triggering
 * downstream processing for every existing form, so event consumers will only receive the
 * responsible role from a form's next update.
 */
@Slf4j
@ChangeUnit(id = "populateLtftResponsibleRole", order = "012")
public class PopulateLtftResponsibleRole {

  /**
   * The lifecycle states associated with each responsible role at the time of this migration.
   */
  static final Map<ResponsibleRole, List<String>> ROLE_STATES = Map.of(
      ResponsibleRole.ADMIN, List.of(SUBMITTED.name(), UNDER_REVIEW.name()),
      ResponsibleRole.TRAINEE, List.of(UNSUBMITTED.name()));

  private static final String CURRENT_FIELD = "status.current";
  private static final String HISTORY_FIELD = "status.history";
  private static final String STATE_FIELD = "state";
  private static final String ROLE_FIELD = "responsibleRole";
  private static final String ELEMENT_IDENTIFIER = "entry";

  private final MongoTemplate mongoTemplate;

  public PopulateLtftResponsibleRole(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Populate the responsible role for LTFT form statuses.
   */
  @Execution
  public void migrate() {
    ROLE_STATES.forEach((role, states) -> {
      long currentCount = populateCurrent(role, states);
      long historyCount = populateHistory(role, states);
      log.info("Populated responsible role {} for {} current statuses and the status history of "
          + "{} LTFT forms.", role, currentCount, historyCount);
    });
  }

  /**
   * Populate the responsible role of the current status, where not already set.
   *
   * @param role   The responsible role to set.
   * @param states The lifecycle states associated with the role.
   * @return The number of modified forms.
   */
  private long populateCurrent(ResponsibleRole role, List<String> states) {
    Query query = Query.query(Criteria.where(CURRENT_FIELD + "." + STATE_FIELD).in(states)
        .and(CURRENT_FIELD + "." + ROLE_FIELD).exists(false));
    Update update = new Update().set(CURRENT_FIELD + "." + ROLE_FIELD, role.name());
    return mongoTemplate.updateMulti(query, update, LtftForm.class).getModifiedCount();
  }

  /**
   * Populate the responsible role of every matching status history entry, where not already set.
   *
   * @param role   The responsible role to set.
   * @param states The lifecycle states associated with the role.
   * @return The number of modified forms.
   */
  private long populateHistory(ResponsibleRole role, List<String> states) {
    // Restricting to forms with a matching entry is required, as array filters error if the
    // array path does not exist on a document.
    Query query = Query.query(Criteria.where(HISTORY_FIELD).elemMatch(
        Criteria.where(STATE_FIELD).in(states).and(ROLE_FIELD).exists(false)));

    String element = ELEMENT_IDENTIFIER + ".";
    Update update = new Update()
        .set(HISTORY_FIELD + ".$[" + ELEMENT_IDENTIFIER + "]." + ROLE_FIELD, role.name())
        .filterArray(Criteria.where(element + STATE_FIELD).in(states)
            .and(element + ROLE_FIELD).exists(false));
    return mongoTemplate.updateMulti(query, update, LtftForm.class).getModifiedCount();
  }

  /**
   * Do not attempt rollback, the derived field is harmless if left in place.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'populateLtftResponsibleRole' migration.");
  }
}

