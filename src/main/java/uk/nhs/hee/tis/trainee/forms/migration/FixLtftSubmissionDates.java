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

import com.mongodb.client.result.UpdateResult;
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
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

/**
 * Repair submission dates for LTFT forms where admin assignment has incorrectly overwritten them.
 */
@Slf4j
@ChangeUnit(id = "fixLtftSubmissionDates", order = "8")
public class FixLtftSubmissionDates {

  private final MongoTemplate mongoTemplate;
  private final LtftService ltftService;

  public FixLtftSubmissionDates(MongoTemplate mongoTemplate, LtftService ltftService) {
    this.mongoTemplate = mongoTemplate;
    this.ltftService = ltftService;
  }

  /**
   * Repair submission dates for LTFT forms.
   */
  @Execution
  public void migrate() {
    var submittedCriteria = Criteria.where("status.submitted").exists(true);
    var submittedQuery = Query.query(submittedCriteria);

    List<LtftForm> submittedForms = mongoTemplate.find(submittedQuery, LtftForm.class);

    int formsFixed = 0;
    for (LtftForm form : submittedForms) {
      if (form.getStatus() == null || form.getStatus().history() == null) {
        continue;
      }

      List<AbstractAuditedForm.Status.StatusInfo> history = form.getStatus().history();
      Instant lastTraineeSubmittedTimestamp = null;

      for (AbstractAuditedForm.Status.StatusInfo info : history) {
        if (info.state() == SUBMITTED && info.modifiedBy() != null) {
          if ("TRAINEE".equals(info.modifiedBy().role())) {
            if (lastTraineeSubmittedTimestamp == null
                || info.timestamp().isAfter(lastTraineeSubmittedTimestamp)) {
              lastTraineeSubmittedTimestamp = info.timestamp();
            }
          }
        }
      }

      //If we found a trainee submitted timestamp, and it differs from the current one, update it
      if (lastTraineeSubmittedTimestamp != null
          && !lastTraineeSubmittedTimestamp.equals(form.getStatus().submitted())) {
        var updateQuery = Query.query(Criteria.where("id").is(form.getId()));
        var update = new Update().set("status.submitted", lastTraineeSubmittedTimestamp);
        log.info("Fixing submission date for LTFT form id {}: was {}, setting to {}",
            form.getId(), form.getStatus().submitted(), lastTraineeSubmittedTimestamp);
        UpdateResult updated = mongoTemplate.updateFirst(updateQuery, update, LtftForm.class);
        if (updated.getModifiedCount() != 1) {
          log.error("Failed to update submission date for LTFT form id {}", form.getId());
        }
        //now publish to NDW
        LtftForm updatedForm = mongoTemplate.findOne(updateQuery, LtftForm.class);
        if (updatedForm == null) {
          log.error("Failed to retrieve updated LTFT form id {}", form.getId());
        } else {
          // As in LtftService.moveLtftForms(), ltftAssignmentUpdateTopic is used here to publish an
          // update to NDW. Don't use ltftStatusUpdateTopic, which would generate emails
          // to TPD and trainee.
          ltftService.publishUpdateNotification(
              updatedForm, null, ltftService.getLtftAssignmentUpdateTopic());
        }
        formsFixed = formsFixed + 1;
      }
    }
    log.info("Fixed submission dates for {} LTFT forms", formsFixed);
  }

  /**
   * Do not attempt rollback, any successfully fixed forms should stay fixed.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'fixLtftSubmissionDates' migration.");
  }
}
