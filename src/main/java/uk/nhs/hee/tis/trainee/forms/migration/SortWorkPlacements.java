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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

/**
 * Sort the work placements on Form R Part Bs by descending endDate.
 */
@Slf4j
@ChangeUnit(id = "sortWorkPlacements", order = "4")
public class SortWorkPlacements {
  private final MongoTemplate mongoTemplate;
  private final FormRPartBService formService;
  private final FormRPartBMapper formMapper;

  /**
   * Sort the work placements on Form R Part Bs by descending endDate.
   */
  public SortWorkPlacements(MongoTemplate mongoTemplate, FormRPartBService formService,
                            FormRPartBMapper formMapper) {
    this.mongoTemplate = mongoTemplate;
    this.formService = formService;
    this.formMapper = formMapper;
  }

  /**
   * Sort work placements on all Form R Part Bs.
   */
  @Execution
  public void migrate() {
    Comparator<Work> compareEndDates = Comparator.comparing(Work::getEndDate).reversed();

    for (FormRPartB formRPartB : mongoTemplate.findAll(FormRPartB.class)) {
      List<Work> originalList = new ArrayList<>(formRPartB.getWork());
      formRPartB.getWork().sort(compareEndDates);
      if (!formRPartB.getWork().equals(originalList)) {
        FormRPartBDto updatedFormDto = formMapper.toDto(formRPartB);
        formService.save(updatedFormDto);
        log.info("Updated work placement ordering for form id [{}] for trainee [{}]",
            formRPartB.getId(), formRPartB.getTraineeTisId());
      }
    }
  }

  /**
   * Do not attempt rollback, any successfully sorted forms should stay updated.
   */
  @RollbackExecution
  public void rollback() {
    log.warn("Rollback requested but not available for 'sortWorkPlacements' migration.");
  }
}
