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
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

/**
 * Recalculate the total leave field on all Form R Part Bs to resolve an issue where it was not
 * populated correctly.
 */
@Slf4j
@ChangeUnit(id = "recalculateTotalLeave", order = "2")
public class RecalculateTotalLeave {

  private static final String TOTAL_LEAVE = "totalLeave";

  private final MongoTemplate mongoTemplate;
  private final FormRPartBService formService;
  private final FormRPartBMapper formMapper;

  public RecalculateTotalLeave(MongoTemplate mongoTemplate, FormRPartBService formService,
      FormRPartBMapper formMapper) {
    this.mongoTemplate = mongoTemplate;
    this.formService = formService;
    this.formMapper = formMapper;
  }

  /**
   * Recalculate total leave on all Form R Part Bs.
   */
  @Execution
  public void migrate() {
    var criteria = new Criteria().andOperator(
        Criteria.where(TOTAL_LEAVE).is(0).orOperator(
            Criteria.where("sicknessAbsence").ne(0),
            Criteria.where("parentalLeave").ne(0),
            Criteria.where("careerBreaks").ne(0),
            Criteria.where("paidLeave").ne(0),
            Criteria.where("unauthorisedLeave").ne(0),
            Criteria.where("otherLeave").ne(0)
        )
    );
    var query = Query.query(criteria);

    List<FormRPartB> zeroLeaveForms = mongoTemplate.find(query, FormRPartB.class);
    log.info("Found {} forms with un-calculated total leave.", zeroLeaveForms.size());

    for (FormRPartB zeroLeaveForm : zeroLeaveForms) {
      log.info("Updating total leave for form ID {}", zeroLeaveForm.getId());

      int totalLeave = 0;
      totalLeave += zeroLeaveForm.getSicknessAbsence();
      totalLeave += zeroLeaveForm.getParentalLeave();
      totalLeave += zeroLeaveForm.getCareerBreaks();
      totalLeave += zeroLeaveForm.getPaidLeave();
      totalLeave += zeroLeaveForm.getUnauthorisedLeave();
      totalLeave += zeroLeaveForm.getOtherLeave();
      zeroLeaveForm.setTotalLeave(totalLeave);

      FormRPartBDto zeroLeaveFormDto = formMapper.toDto(zeroLeaveForm);
      formService.save(zeroLeaveFormDto);
    }
  }

  /**
   * Do not attempt rollback, any successfully recalculate total leave should stay updated.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'recalculateTotalLeave' migration.");
  }
}
