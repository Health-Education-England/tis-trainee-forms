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

package uk.nhs.hee.tis.trainee.forms.job;

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.REJECTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.WITHDRAWN;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

/**
 * A job to publish all exportable LTFT applications as if they have been updated, useful for
 * refreshing downstream dependants after data discrepancies.
 */
@Slf4j
@Component
@XRayEnabled
public class PublishLtftRefresh extends AbstractPublishRefresh<LtftForm> {

  private final LtftFormRepository repository;
  private final LtftService service;
  private final String topic;

  /**
   * Create an instance of a job for publishing LTFT refreshes.
   *
   * @param repository The repository used to retrieve LTFT records.
   * @param service    The LTFT service used to send notifications.
   * @param topic      The refresh topic to publish to.
   */
  public PublishLtftRefresh(LtftFormRepository repository, LtftService service,
      @Value("${application.aws.sns.ltft-refresh}") String topic) {
    this.repository = repository;
    this.service = service;
    this.topic = topic;
  }

  @Override
  public String getFormTypeName() {
    return "LTFT";
  }

  @Override
  public UUID getFormId(LtftForm form) {
    return form.getId();
  }

  @Override
  public Stream<LtftForm> streamForms() {
    // Listing allowed (non-DRAFT) states avoids any accidental inclusions of future states.
    return repository.streamByStatus_Current_StateIn(Set.of(
        APPROVED,
        DELETED,
        REJECTED,
        SUBMITTED,
        UNSUBMITTED,
        WITHDRAWN
    ));
  }

  @Override
  public void publishForm(LtftForm form) {
    service.publishUpdateNotification(form, null, topic);
  }

  /**
   * Execute the scheduled job to publish all exportable LTFT applications as a refresh.
   */
  @Scheduled(cron = "${application.schedules.publish-all-ltfts}")
  @SchedulerLock(name = "PublishLtftRefresh.execute")
  @Override
  public Integer execute() {
    return super.execute();
  }
}
