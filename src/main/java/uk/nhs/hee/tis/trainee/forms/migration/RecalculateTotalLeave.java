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

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators.Add;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

/**
 * Recalculate the total leave field on all Form R Part Bs to resolve an issue where it was not
 * populated correctly.
 */
@Slf4j
@ChangeUnit(id = "recalculateTotalLeave", order = "2")
public class RecalculateTotalLeave {

  private static final String TOTAL_LEAVE = "totalLeave";

  private final MongoTemplate mongoTemplate;

  public RecalculateTotalLeave(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Recalculate total leave on all Form R Part Bs.
   */
  @Execution
  public void migrate() {
    var criteria = Criteria.where(TOTAL_LEAVE).is(0);
    var query = Query.query(criteria);

    var add =
        Add.valueOf("sicknessAbsence")
            .add("parentalLeave")
            .add("careerBreaks")
            .add("paidLeave")
            .add("unauthorisedLeave")
            .add("otherLeave");
    var update = AggregationUpdate.update().set(TOTAL_LEAVE).toValueOf(add);

    mongoTemplate.updateMulti(query, update, FormRPartB.class);
  }

  /**
   * Do not attempt rollback, any successfully recalculate total leave should stay updated.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'recalculateTotalLeave' migration.");
  }
}
