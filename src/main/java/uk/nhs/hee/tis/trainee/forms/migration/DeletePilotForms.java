/*
 * The MIT License (MIT)
 *
 * Copyright 2022 Crown Copyright (Health Education England)
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

import static java.time.Month.SEPTEMBER;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

/**
 *
 */
@Slf4j
@ChangeUnit(id = "deletePilotForms", order = "1")
public class DeletePilotForms {

  private final MongoTemplate mongoTemplate;

  public DeletePilotForms(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   *
   */
  @Execution
  public void migrate() {
    var pilotEndDate = LocalDate.of(2021, SEPTEMBER, 1);
    var lessThanPilotEndDateCriteria = Criteria.where("lastModifiedDate").lt(pilotEndDate);
    var lessThanPilotEndDateQuery = Query.query(lessThanPilotEndDateCriteria);

    mongoTemplate.remove(lessThanPilotEndDateQuery, FormRPartA.class);
    mongoTemplate.remove(lessThanPilotEndDateQuery, FormRPartB.class);
  }

  /**
   *
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'deletePilotForms' migration.");
  }
}
