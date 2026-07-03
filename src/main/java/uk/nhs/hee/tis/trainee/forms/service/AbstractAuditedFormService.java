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

package uk.nhs.hee.tis.trainee.forms.service;

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.UserIdentity;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusDetail;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.repository.BaseAuditedFormRepository;

/**
 * An abstract service class for managing audited forms in a consistent way.
 *
 * @param <F> The type of audited form.
 */
@Slf4j
public abstract class AbstractAuditedFormService<F extends AbstractAuditedForm<?>> {

  private final BaseAuditedFormRepository<F> repository;
  private final SubmissionHistoryService<F> historyService;

  /**
   * Constructor for AbstractAuditedFormService.
   *
   * @param repository     The repository for audited forms.
   * @param historyService The service for managing submission history of audited forms.
   */
  protected AbstractAuditedFormService(BaseAuditedFormRepository<F> repository,
      SubmissionHistoryService<F> historyService) {
    this.repository = repository;
    this.historyService = historyService;
  }

  /**
   * Publishes a status update notification for the given form.
   *
   * @param form The form for which to publish the status update notification.
   */
  protected abstract void publishStatusUpdateNotification(F form);

  /**
   * Update the status of a form, the current status and history will both be updated.
   *
   * @param form        The form to update the status of.
   * @param targetState The state to change to.
   * @param identity    Who is performing the status change.
   * @param detail      A detailed reason for the change, may be null.
   * @return The updated form.
   * @throws MethodArgumentNotValidException If the state transition is not allowed.
   */
  protected F updateStatus(F form, LifecycleState targetState,
      UserIdentity identity, @Nullable StatusDetail detail)
      throws MethodArgumentNotValidException {

    if (!LifecycleState.canTransitionTo(form, targetState)) {
      log.warn(
          "Could not update form {}, invalid lifecycle transition from {} to {} for form type '{}'",
          form.getId(), form.getLifecycleState(), targetState, form.getFormType());

      BeanPropertyBindingResult result = new BeanPropertyBindingResult(form, "form");
      result.addError(new FieldError(form.getClass().getSimpleName(), "status.current.state",
          "can not be transitioned to %s".formatted(targetState)));

      try {
        MethodParameter parameter = new MethodParameter(AbstractAuditedFormService.class
            .getDeclaredMethod("updateStatus", AbstractAuditedForm.class, LifecycleState.class,
                UserIdentity.class, StatusDetail.class), 1);
        throw new MethodArgumentNotValidException(parameter, result);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("Unable to reflect updateStatus method.", e);
      }
    }

    if (targetState.isRequiresDetails() && (detail == null || detail.reason() == null)) {
      log.warn("Form {} requires a reason to change to state [{}]", form.getId(), targetState);

      BeanPropertyBindingResult result = new BeanPropertyBindingResult(detail, "detail");
      String field = detail == null ? "detail" : "detail.reason";
      result.addError(new FieldError("StatusInfo", field,
          "must not be null when transitioning to %s".formatted(targetState)));

      try {
        MethodParameter parameter = new MethodParameter(AbstractAuditedFormService.class
            .getDeclaredMethod("updateStatus", AbstractAuditedForm.class, LifecycleState.class,
                UserIdentity.class, StatusDetail.class), 3);
        throw new MethodArgumentNotValidException(parameter, result);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("Unable to reflect updateStatus method.", e);
      }
    }

    if (targetState.isIncrementsRevision()) {
      form.setRevision(form.getRevision() + 1);
    }

    Person modifiedBy = Person.builder()
        .name(identity.getName())
        .email(identity.getEmail())
        .role(identity.getRole())
        .build();
    form.setLifecycleState(targetState, detail, modifiedBy, form.getRevision());

    generateFormReference(form);

    F savedForm = repository.save(form);
    if (targetState == SUBMITTED) {
      historyService.takeSnapshot(savedForm);
    }

    publishStatusUpdateNotification(savedForm);

    return savedForm;
  }

  /**
   * Generates a form reference for the given form if it has been submitted and does not already
   * have a reference.
   *
   * @param form The form to generate the reference for.
   */
  private void generateFormReference(F form) {
    if (form.getFormRef() == null && form.getLifecycleState() == SUBMITTED) {
      String traineeId = form.getTraineeTisId();
      long count = repository.countSubmittedByTraineeId(traineeId);
      String formRef = "%s_%s_%03d".formatted(form.getFormReferencePrefix(), traineeId, count + 1);
      log.debug("Assigning form reference {} to form {}", formRef, form.getId());
      form.setFormRef(formRef);
    }
  }
}
