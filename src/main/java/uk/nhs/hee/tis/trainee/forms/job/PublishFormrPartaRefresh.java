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
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;

/**
 * A job to publish all exportable Form-R applications as if they have been updated, useful for
 * refreshing downstream dependants after data discrepancies.
 */
@Slf4j
@Component
@XRayEnabled
public class PublishFormrPartaRefresh extends AbstractPublishRefresh<FormRPartA> {

  private final FormRPartARepository repository;
  private final FormRPartAService service;
  private final FormRPartAMapper mapper;
  private final String topic;

  /**
   * Create an instance of a job for publishing Form-R refreshes.
   *
   * @param repository The repository used to retrieve Form-R records.
   * @param service    The Form-R service used to send notifications.
   * @param topic      The refresh topic to publish to.
   */
  public PublishFormrPartaRefresh(FormRPartARepository repository, FormRPartAService service,
      FormRPartAMapper mapper, @Value("${application.aws.sns.formr-refresh}") String topic) {
    this.repository = repository;
    this.service = service;
    this.mapper = mapper;
    this.topic = topic;
  }

  @Override
  public String getFormTypeName() {
    return "Form-R Part A";
  }

  @Override
  public UUID getFormId(FormRPartA form) {
    return form.getId();
  }

  @Override
  public Stream<FormRPartA> streamForms(Optional<LocalDate> cutoffDate) {
    Set<LifecycleState> states = Set.of(DELETED, SUBMITTED, UNSUBMITTED);
    // Listing allowed (non-DRAFT) states avoids any accidental inclusions of future states.
    if (cutoffDate.isPresent()) {
      return repository.streamByLifecycleStateInAndLastModifiedDateGreaterThanEqual(
          states, cutoffDate.get().atStartOfDay());
    }
    return repository.streamByLifecycleStateIn(states);
  }

  @Override
  public void publishForm(FormRPartA form) {
    service.publishUpdateNotification(mapper.toDto(form), topic);
  }

  /**
   * Execute the scheduled job to publish all exportable Form-R Part A applications as a refresh.
   */
  @Scheduled(cron = "${application.schedules.publish-all-formr-partas}")
  @SchedulerLock(name = "PublishFormrPartaRefresh.execute")
  @Override
  public Integer execute() {
    return super.execute();
  }

  /**
   * Execute the job to publish exportable Form-R Part A applications as a refresh.
   *
   * @param cutoffDate An optional cutoff start date; only forms last modified on or after this date
   *                   will be refreshed. If empty, all forms are refreshed.
   * @return The number of published forms.
   */
  @Override
  public Integer execute(Optional<LocalDate> cutoffDate) {
    return super.execute(cutoffDate);
  }
}
