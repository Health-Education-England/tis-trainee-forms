/*
 * The MIT License (MIT)
 *
 * Copyright 2020 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.service;

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DELETED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.UserIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.UserIdentityResolver;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusDetail;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.EventBroadcastService.FormrFileEventDto;

@Slf4j
@Service
@Transactional
@XRayEnabled
public class FormRPartAService extends AbstractAuditedFormService<FormRPartA> {

  public static final String FORM_TYPE = "formr-a";

  private final FormRPartAMapper mapper;

  private final FormRPartARepository repository;

  private final ObjectMapper objectMapper;

  private final UserIdentityResolver identityResolver;

  private final EventBroadcastService eventBroadcastService;

  private final String formRPartAUpdatedTopic;

  /**
   * Constructor for a FormR PartA service.
   *
   * @param repository             Spring data repository.
   * @param mapper                 Maps between the form entity and DTO.
   * @param objectMapper           The object mapper.
   * @param identityResolver       A resolver for the identity of the user making the request.
   * @param eventBroadcastService  The event broadcast service.
   * @param formRPartAUpdatedTopic The SNS topic for FormR PartA updated events.
   */
  public FormRPartAService(FormRPartARepository repository, FormRPartAMapper mapper,
      ObjectMapper objectMapper, UserIdentityResolver identityResolver,
      EventBroadcastService eventBroadcastService,
      SubmissionHistoryService<FormRPartA> historyService,
      @Value("${application.aws.sns.formr-updated}") String formRPartAUpdatedTopic) {
    super(repository, historyService);

    this.eventBroadcastService = eventBroadcastService;
    this.formRPartAUpdatedTopic = formRPartAUpdatedTopic;
    this.repository = repository;
    this.mapper = mapper;
    this.objectMapper = objectMapper;
    this.identityResolver = identityResolver;
  }

  /**
   * save FormRPartA.
   */
  public FormRPartADto save(FormRPartADto formRPartADto) {
    log.info("Request to save FormRPartA : {}", formRPartADto);
    FormRPartA existingForm = null;
    Set<LifecycleState> modifiableStates = Set.of(DRAFT, UNSUBMITTED);

    if (formRPartADto.getId() != null) {
      Optional<FormRPartA> foundForm = repository.findById(UUID.fromString(formRPartADto.getId()));

      if (foundForm.isPresent()) {
        existingForm = foundForm.get();
        if (!modifiableStates.contains(existingForm.getLifecycleState())) {
          String message = String.format("Form %s is in lifecycle state %s and cannot be modified.",
              existingForm.getId(), existingForm.getLifecycleState());
          throw new IllegalArgumentException(message);
        }
      }
    }

    FormRPartA formRPartA = mapper.toEntity(formRPartADto, existingForm);

    if (existingForm == null
        || formRPartADto.getLifecycleState() != existingForm.getLifecycleState()) {
      LifecycleState newLifecycleState = formRPartADto.getLifecycleState();

      try {
        UserIdentity userIdentity = identityResolver.getUserIdentity();
        formRPartA = updateStatus(formRPartA, newLifecycleState, userIdentity, null);
      } catch (MethodArgumentNotValidException e) {
        // TODO: allow exception to bubble up to provide appropriate API responses.
        throw new IllegalArgumentException(e);
      }
    } else {
      formRPartA = repository.save(formRPartA);
    }

    FormRPartADto formDto = mapper.toDto(formRPartA);
    if (formRPartA.getLifecycleState() == SUBMITTED) {
      publishFormRUpdateEvent(formDto);
    }
    return formDto;
  }

  @Override
  protected FormRPartA updateStatus(FormRPartA form, LifecycleState targetState,
      UserIdentity identity, @Nullable StatusDetail detail) throws MethodArgumentNotValidException {
    // Temporary solution for creation of FormRs at DRAFT or SUBMITTED.
    if (form.getLifecycleState() == null && (targetState == DRAFT || targetState == SUBMITTED)) {
      Person modifiedBy = Person.builder()
          .name(identity.getName())
          .email(identity.getEmail())
          .role(identity.getRole())
          .build();
      form.setLifecycleState(DRAFT, null, modifiedBy, 0);

      if (targetState == DRAFT) {
        return repository.save(form);
      }
    }

    return super.updateStatus(form, targetState, identity, detail);
  }

  /**
   * get FormRPartAs for logged-in trainee.
   */
  public List<FormRPartSimpleDto> getFormRPartAs() {
    String traineeId = identityResolver.requireTraineeIdentity().getTraineeId();
    log.info("Request to get FormRPartA list by trainee profileId : {}", traineeId);
    List<FormRPartA> formRPartAList = repository.findByTraineeTisId(traineeId);
    return mapper.toSimpleDtos(formRPartAList);
  }

  /**
   * get FormRPartAs for specific trainee by trainee id.
   *
   * @param traineeId The ID of the requested trainee.
   * @return a list of all the trainees submitted FormRPartAs.
   */
  public List<FormRPartSimpleDto> getFormRPartAs(String traineeId) {
    log.info("Request to get FormRPartA list by trainee profileId : {}", traineeId);

    List<FormRPartA> formRPartAList = repository.findNotDraftNorDeletedByTraineeTisId(traineeId);

    return mapper.toSimpleDtos(formRPartAList);
  }

  /**
   * get FormRPartA by id.
   */
  public FormRPartADto getFormRPartAById(String id) {
    log.info("Request to get FormRPartA by id : {}", id);

    String traineeId = identityResolver.requireTraineeIdentity().getTraineeId();
    Optional<FormRPartA> optionalDbForm = repository.findByIdAndTraineeTisId(UUID.fromString(id),
        traineeId);

    return mapper.toDto(optionalDbForm.orElse(null));
  }

  /**
   * get FormRPartA by id for admins.
   *
   * @param id The ID of the form to retrieve.
   * @return Optional containing the FormRPartA DTO, or empty if form is DRAFT/DELETED or not found.
   */
  public Optional<FormRPartADto> getAdminsFormRPartAById(String id) {
    log.info("Request to get FormRPartA by id : {}", id);

    return repository.findByIdAndNotDraftNorDeleted(UUID.fromString(id))
        .map(mapper::toDto);
  }

  /**
   * Delete the form for the given ID, only DRAFT forms are supported.
   *
   * @param id The ID of the form to delete.
   * @return true if the form was found and deleted, false if not found.
   */
  public boolean deleteFormRPartAById(String id) {
    log.info("Request to delete FormRPartA by id : {}", id);
    String traineeId = identityResolver.requireTraineeIdentity().getTraineeId();
    Optional<FormRPartA> optionalForm = repository.findByIdAndTraineeTisId(UUID.fromString(id),
        traineeId);

    if (optionalForm.isEmpty()) {
      log.info("FormRPartA {} did not exist.", id);
      return false;
    }

    FormRPartA form = optionalForm.get();
    LifecycleState state = form.getLifecycleState();

    if (!state.equals(DRAFT)) {
      String message = String.format("Unable to delete forms with lifecycle state %s.", state);
      log.warn(message);
      throw new IllegalArgumentException(message);
    }

    repository.delete(form);
    log.info("Deleted FormRPartA {} for trainee {}", id, form.getTraineeTisId());

    return true;
  }

  /**
   * Partially delete a form, leaving only the fixed fields (e.g. IDs, timestamps and state).
   *
   * @param id The ID of the target form.
   * @return The partially deleted form, or empty if the form was not found.
   */
  public Optional<FormRPartADto> partialDeleteFormRPartAById(UUID id)
      throws MethodArgumentNotValidException {
    log.info("Request to partial delete FormRPartA with id : {}", id);

    Optional<FormRPartA> found = repository.findById(id);

    if (found.isPresent()) {
      FormRPartA form = partialDelete(found.get());
      return Optional.of(mapper.toDto(form));
    }

    return Optional.empty();
  }

  /**
   * Partially delete a form, leaving only the fixed fields (e.g. IDs, timestamps and state).
   *
   * @param form The form to partially delete.
   * @return The partially deleted form.
   */
  private FormRPartA partialDelete(FormRPartA form) throws MethodArgumentNotValidException {
    form.setContent(null);
    AdminIdentity adminIdentity = identityResolver.requireAdminIdentity();
    form = updateStatus(form, DELETED, adminIdentity, null);
    publishFormRUpdateEvent(mapper.toDto(form));
    log.info("Partial delete successfully for trainee {} with form Id {} (FormRPartA)",
        form.getTraineeTisId(), form.getId());

    return form;
  }

  /**
   * Unsubmit a form, set lifecycle state to UNSUBMITTED.
   *
   * @param id The ID of the target form.
   * @return The unsubmitted form, or empty if the form was not found.
   */
  public Optional<FormRPartADto> unsubmitFormRPartAById(UUID id)
      throws MethodArgumentNotValidException {
    log.info("Request to unsubmit FormRPartA with id : {}", id);

    Optional<FormRPartA> found = repository.findById(id);
    if (found.isPresent()) {
      AdminIdentity adminIdentity = identityResolver.requireAdminIdentity();
      // TODO: update reason when provided by admin.
      StatusDetail statusDetail = StatusDetail.builder().reason("Requires correction").build();
      FormRPartA form = updateStatus(found.get(), UNSUBMITTED, adminIdentity, statusDetail);
      FormRPartADto dto = mapper.toDto(form);

      publishFormRUpdateEvent(dto);
      log.info("Unsubmitted successfully for trainee {} with form Id {} (FormRPartA)",
          form.getTraineeTisId(), form.getId());
      return Optional.of(dto);
    }

    return Optional.empty();
  }

  /**
   * Publish Form-R status update notification.
   *
   * @param form The updated LTFT form.
   */
  @Override
  public void publishStatusUpdateNotification(FormRPartA form) {
    // TODO: do nothing until Form-R events are standardised.
  }

  /**
   * Publish Form-R update notification.
   *
   * @param form     The updated Form-R.
   * @param snsTopic The SNS topic to publish the notification to.
   */
  public void publishUpdateNotification(FormRPartADto form, String snsTopic) {
    eventBroadcastService.publishFormRPartAEvent(form,
        Map.of(EventBroadcastService.MESSAGE_ATTRIBUTE_KEY_FORM_TYPE, FORM_TYPE), snsTopic);
    log.info("Published update notification for Form-R Part A form {} to SNS topic {}",
        form.getId(), snsTopic);
  }

  /**
   * Publish an updated form.
   *
   * @param formDto The form DTO to publish.
   */
  private void publishFormRUpdateEvent(FormRPartADto formDto) {
    log.debug("Publishing FormRPartA {} event for form id: {}",
        formDto.getLifecycleState(), formDto.getId());
    publishUpdateNotification(formDto, formRPartAUpdatedTopic);

    // TODO: temporary hack until actions and notifications are migrated to full-form events.
    Map<String, Object> content = objectMapper.convertValue(formDto, Map.class);
    FormrFileEventDto fileEvent = new FormrFileEventDto(formDto.getId() + ".json",
        formDto.getLifecycleState().toString(), formDto.getTraineeTisId(), FORM_TYPE, Instant.now(),
        content);
    eventBroadcastService.publishFormrFileEvent(fileEvent);
  }
}
