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
 *
 */

package uk.nhs.hee.tis.trainee.forms.job;

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

/**
 * A job to publish all exportable Form-R applications as if they have been updated, useful for
 * refreshing downstream dependants after data discrepancies.
 */
@Slf4j
@Component
@XRayEnabled
public class PublishFormrPartbRefresh {

  private final FormRPartBRepository repository;
  private final FormRPartBService service;
  private final FormRPartBMapper mapper;
  private final String topic;

  /**
   * Create an instance of a job for publishing Form-R refreshes.
   *
   * @param repository The repository used to retrieve Form-R records.
   * @param service    The Form-R service used to send notifications.
   * @param topic      The refresh topic to publish to.
   */
  public PublishFormrPartbRefresh(FormRPartBRepository repository, FormRPartBService service,
      FormRPartBMapper mapper, @Value("${application.aws.sns.formr-refresh}") String topic) {
    this.repository = repository;
    this.service = service;
    this.mapper = mapper;
    this.topic = topic;
  }

  /**
   * Execute the scheduled job to publish all exportable Form-R Part B applications as a refresh.
   */
  @Scheduled(cron = "${application.schedules.publish-all-formr-partbs}")
  @SchedulerLock(name = "PublishFormrPartbRefresh.execute")
  public Integer execute() {
    log.info("Starting Form-R Part B downstream refresh.");
    // Listing allowed (non-DRAFT) states avoids any accidental inclusions of future states.
    List<FormRPartB> formrs = repository.findByLifecycleStateIn(Set.of(
        DELETED,
        SUBMITTED,
        UNSUBMITTED
    ));

    int total = formrs.size();
    log.info("Found {} Form-R Part Bs to refresh.", total);

    int published = 0;

    for (FormRPartB formr : formrs) {
      log.debug("Publishing refresh notification for Form-R Part B {}.", formr.getId());

      try {

        service.publishUpdateNotification(mapper.toDto(formr), topic);
        published++;
      } catch (Exception e) {
        log.error("Unable to publish refresh notification for Form-R Part B {}.", formr.getId());
      }
    }

    log.info("Finished Form-R Part B downstream refresh, published count: {}/{}.", published,
        total);
    return published;
  }
}
