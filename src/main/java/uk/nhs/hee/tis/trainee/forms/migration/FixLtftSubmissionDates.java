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

package uk.nhs.hee.tis.trainee.forms.migration;

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

/**
 * Repair submission dates for LTFT forms where admin assignment has incorrectly overwritten them.
 */
@Slf4j
@ChangeUnit(id = "fixLtftSubmissionDates", order = "8")
public class FixLtftSubmissionDates {

  private final MongoTemplate mongoTemplate;

  public FixLtftSubmissionDates(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Repair submission dates for LTFT forms.
   */
  @Execution
  public void migrate() {

    //scenarios (non-exhaustive):
    //most common: form draft, then submitted, then admin assigned:
    //  form.submitted will be timestamp of history.2 instead of history.1
    //form draft, form submitted, admin assigned, form unsubmitted
    //  form.submitted will be timestamp of history.2 instead of history.1 (though not important as unsubmitted)
    //form draft, form submitted, admin assigned, another admin assigned
    //  form.submitted will be timestamp of history.3 instead of history.1

    //identification of affected forms:
    //any form with a submitted date where
    // the latest history entry with state = SUBMITTED and modifiedBy.role = ADMIN is after
    // the latest history entry with state = SUBMITTED and modifiedBy.role = TRAINEE
    // will have an incorrect submitted date
    // we can use the modifiedBy.role because the only admin change that would apply to a SUBMITTED form is assignment of an admin
    // NB: when a trainee resubmits a form which had previous had an admin assigned, the admin assignment remains, so we cannot just check for presence of an assigned admin

    //query:
    //for all forms with submitted date:
    // filter history for state = SUBMITTED and modifiedBy.role = TRAINEE,
    // and set form submitted date to latest filtered history timestamp


    var submittedCriteria = Criteria.where("status.submitted").exists(true);
    var submittedQuery = Query.query(submittedCriteria);

    List<LtftForm> submittedForms = mongoTemplate.find(submittedQuery, LtftForm.class);
    int formsFixed = 0;

    for (LtftForm form : submittedForms) {
      if (form.getStatus() == null || form.getStatus().history() == null) {
        continue;
      }

      List<AbstractAuditedForm.Status.StatusInfo> history = form.getStatus().history();
      Instant lastAdminSubmittedTimestamp = null;
      Instant lastTraineeSubmittedTimestamp = null;

      for (AbstractAuditedForm.Status.StatusInfo info : history) {
        if (info.state() == SUBMITTED && info.modifiedBy() != null) {
          if ("ADMIN".equals(info.modifiedBy().role())) {
            if (lastAdminSubmittedTimestamp == null
                || info.timestamp().isAfter(lastAdminSubmittedTimestamp)) {
              lastAdminSubmittedTimestamp = info.timestamp();
            }
          } else if ("TRAINEE".equals(info.modifiedBy().role())) {
            if (lastTraineeSubmittedTimestamp == null
                || info.timestamp().isAfter(lastTraineeSubmittedTimestamp)) {
              lastTraineeSubmittedTimestamp = info.timestamp();
            }
          }
        }
      }

      // If admin submitted history item is after trainee submitted, form submission date needs fixing
      if (lastTraineeSubmittedTimestamp != null
          && lastAdminSubmittedTimestamp != null
          && lastAdminSubmittedTimestamp.isAfter(lastTraineeSubmittedTimestamp)) {
        var updateQuery = Query.query(Criteria.where("id").is(form.getId()));
        var update = new Update().set("status.submitted", lastTraineeSubmittedTimestamp);
        log.info("Fixing submission date for LTFT form id {}: was {}, setting to {}",
            form.getId(), form.getStatus().submitted(), lastTraineeSubmittedTimestamp);
        mongoTemplate.updateFirst(updateQuery, update, LtftForm.class);
        formsFixed = formsFixed + 1;
      }
    }
    log.info("Fixed submission dates for {} LTFT forms", formsFixed);

    //need to republish the forms with correct submission dates, or just republish all of them?
  }

  /**
   * Do not attempt rollback, any successfully fixed forms should stay fixed.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'fixLtftSubmissionDates' migration.");
  }
}
