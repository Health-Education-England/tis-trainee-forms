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
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.ADMIN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.REVIEWER;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole.TRAINEE;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.List;
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
 * <p>The role is derived from each entry's own lifecycle state, assigned reviewer and review
 * outcome using a fixed copy of the rule as it stood when this migration was written, deliberately
 * not delegating to {@link LtftForm#resolveResponsibleRole} so that any later rule changes are
 * never applied retrospectively.
 * <ul>
 *   <li>UNSUBMITTED: TRAINEE.</li>
 *   <li>SUBMITTED or UNDER_REVIEW, with an assigned reviewer and no review outcome: REVIEWER.</li>
 *   <li>SUBMITTED or UNDER_REVIEW, otherwise: ADMIN.</li>
 *   <li>Any other state: no responsible role.</li>
 * </ul>
 * The criteria for each role are mutually exclusive, so the order of the updates does not matter.
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
   * The responsible roles populated by this migration.
   */
  static final List<ResponsibleRole> ROLES = List.of(TRAINEE, REVIEWER, ADMIN);

  private static final List<String> REVIEWABLE_STATES = List.of(SUBMITTED.name(),
      UNDER_REVIEW.name());

  private static final String CURRENT_PREFIX = "status.current.";
  private static final String HISTORY_FIELD = "status.history";
  private static final String STATE_FIELD = "state";
  private static final String ASSIGNED_REVIEWER_FIELD = "assignedReviewer";
  private static final String REVIEW_OUTCOME_FIELD = "reviewOutcome";
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
    ROLES.forEach(role -> {
      long currentCount = populateCurrent(role);
      long historyCount = populateHistory(role);
      log.info("Populated responsible role {} for {} current statuses and the status history of "
          + "{} LTFT forms.", role, currentCount, historyCount);
    });
  }

  /**
   * Populate the responsible role of the current status, where not already set.
   *
   * @param role The responsible role to set.
   * @return The number of modified forms.
   */
  private long populateCurrent(ResponsibleRole role) {
    Query query = Query.query(entryCriteria(role, CURRENT_PREFIX));
    Update update = new Update().set(CURRENT_PREFIX + ROLE_FIELD, role.name());
    return mongoTemplate.updateMulti(query, update, LtftForm.class).getModifiedCount();
  }

  /**
   * Populate the responsible role of every matching status history entry, where not already set.
   *
   * @param role The responsible role to set.
   * @return The number of modified forms.
   */
  private long populateHistory(ResponsibleRole role) {
    // Restricting to forms with a matching entry is required, as array filters error if the
    // array path does not exist on a document.
    Query query = Query.query(Criteria.where(HISTORY_FIELD).elemMatch(entryCriteria(role, "")));

    Update update = new Update()
        .set(HISTORY_FIELD + ".$[" + ELEMENT_IDENTIFIER + "]." + ROLE_FIELD, role.name())
        .filterArray(entryCriteria(role, ELEMENT_IDENTIFIER + "."));
    return mongoTemplate.updateMulti(query, update, LtftForm.class).getModifiedCount();
  }

  /**
   * Build the criteria matching a status entry, without a responsible role, which should be
   * populated with the given role.
   *
   * @param role   The responsible role to match entries for.
   * @param prefix The prefix of the status entry's fields, empty for relative fields.
   * @return The built criteria.
   */
  static Criteria entryCriteria(ResponsibleRole role, String prefix) {
    Criteria criteria = Criteria.where(prefix + ROLE_FIELD).exists(false);

    return switch (role) {
      case TRAINEE -> criteria.and(prefix + STATE_FIELD).is(UNSUBMITTED.name());
      case REVIEWER -> criteria.and(prefix + STATE_FIELD).in(REVIEWABLE_STATES)
          .and(prefix + ASSIGNED_REVIEWER_FIELD).ne(null)
          .and(prefix + REVIEW_OUTCOME_FIELD).is(null);
      case ADMIN -> criteria.and(prefix + STATE_FIELD).in(REVIEWABLE_STATES)
          .orOperator(
              Criteria.where(prefix + ASSIGNED_REVIEWER_FIELD).is(null),
              Criteria.where(prefix + REVIEW_OUTCOME_FIELD).ne(null));
    };
  }

  /**
   * Do not attempt rollback, the derived field is harmless if left in place.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'populateLtftResponsibleRole' migration.");
  }
}

