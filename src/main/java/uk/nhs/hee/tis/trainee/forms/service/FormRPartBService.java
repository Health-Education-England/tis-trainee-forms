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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.UserIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.UserIdentityResolver;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusDetail;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.EventBroadcastService.FormrFileEventDto;

@Slf4j
@Service
@Transactional
@XRayEnabled
public class FormRPartBService extends AbstractAuditedFormService<FormRPartB> {

  public static final String FORM_TYPE = "formr-b";

  private final FormRPartBMapper formRPartBMapper;

  private final FormRPartBRepository formRPartBRepository;

  private final ObjectMapper objectMapper;

  private final UserIdentityResolver identityResolver;

  private final EventBroadcastService eventBroadcastService;

  private final String formRPartBUpdatedTopic;

  /**
   * Constructor for a FormR PartB service.
   *
   * @param formRPartBRepository   spring data repository
   * @param formRPartBMapper       maps between the form entity and dto
   * @param objectMapper           The object mapper.
   * @param identityResolver       The user identity resolver.
   * @param eventBroadcastService  The event broadcast service.
   * @param formRPartBUpdatedTopic The SNS topic for FormR PartB updated events.
   */
  public FormRPartBService(FormRPartBRepository formRPartBRepository,
      FormRPartBMapper formRPartBMapper, ObjectMapper objectMapper,
      UserIdentityResolver identityResolver, EventBroadcastService eventBroadcastService,
      SubmissionHistoryService<FormRPartB> historyService,
      @Value("${application.aws.sns.formr-updated}") String formRPartBUpdatedTopic) {
    super(formRPartBRepository, historyService);

    this.formRPartBRepository = formRPartBRepository;
    this.formRPartBMapper = formRPartBMapper;
    this.objectMapper = objectMapper;
    this.identityResolver = identityResolver;
    this.eventBroadcastService = eventBroadcastService;
    this.formRPartBUpdatedTopic = formRPartBUpdatedTopic;
  }

  /**
   * save FormRPartB.
   */
  public FormRPartBDto save(FormRPartBDto formRPartBDto) {
    log.info("Request to save FormRPartB : {}", formRPartBDto);
    FormRPartB existingForm = null;
    Set<LifecycleState> modifiableStates = Set.of(DRAFT, UNSUBMITTED);

    if (formRPartBDto.getId() != null) {
      Optional<FormRPartB> foundForm = formRPartBRepository.findById(
          UUID.fromString(formRPartBDto.getId()));

      if (foundForm.isPresent()) {
        existingForm = foundForm.get();
        if (!modifiableStates.contains(existingForm.getLifecycleState())) {
          String message = String.format("Form %s is in lifecycle state %s and cannot be modified.",
              existingForm.getId(), existingForm.getLifecycleState());
          throw new IllegalArgumentException(message);
        }
      }
    }

    FormRPartB formRPartB = formRPartBMapper.toEntity(formRPartBDto, existingForm);

    if (existingForm == null
        || formRPartBDto.getLifecycleState() != existingForm.getLifecycleState()) {
      LifecycleState newLifecycleState = formRPartBDto.getLifecycleState();

      try {
        UserIdentity userIdentity = identityResolver.getUserIdentity();
        formRPartB = updateStatus(formRPartB, newLifecycleState, userIdentity, null);
      } catch (MethodArgumentNotValidException e) {
        // TODO: allow exception to bubble up to provide appropriate API responses.
        throw new IllegalArgumentException(e);
      }
    } else {
      formRPartB = formRPartBRepository.save(formRPartB);
    }

    FormRPartBDto formDto = formRPartBMapper.toDto(formRPartB);
    if (formRPartB.getLifecycleState() == SUBMITTED) {
      publishFormRUpdateEvent(formDto);
    }
    return formDto;
  }

  @Override
  protected FormRPartB updateStatus(FormRPartB form, LifecycleState targetState,
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
        return formRPartBRepository.save(form);
      }
    }

    return super.updateStatus(form, targetState, identity, detail);
  }

  /**
   * get FormRPartBs for logged-in trainee.
   */
  public List<FormRPartSimpleDto> getFormRPartBs() {
    String traineeId = identityResolver.requireTraineeIdentity().getTraineeId();
    log.info("Request to get FormRPartB list by trainee profileId : {}", traineeId);
    List<FormRPartB> formRPartBList = formRPartBRepository.findByTraineeTisId(traineeId).stream()
        .filter(form -> form.getContent() != null)
        .toList();
    return formRPartBMapper.toSimpleDtos(formRPartBList);
  }

  /**
   * get FormRPartBs for specific trainee by trainee id.
   *
   * @param traineeId The ID of the requested trainee.
   * @return a list of all the trainees submitted FormRPartBs.
   */
  public List<FormRPartSimpleDto> getFormRPartBs(String traineeId) {
    log.info("Request to get FormRPartB list by trainee profileId : {}", traineeId);

    List<FormRPartB> formRPartBList = formRPartBRepository
        .findNotDraftNorDeletedByTraineeTisId(traineeId).stream()
        .filter(form -> form.getContent() != null)
        .toList();

    return formRPartBMapper.toSimpleDtos(formRPartBList);
  }

  /**
   * get FormRPartB by id.
   */
  public Optional<FormRPartBDto> getFormRPartBById(String id) {
    log.info("Request to get FormRPartB by id : {}", id);

    String traineeId = identityResolver.requireTraineeIdentity().getTraineeId();
    return formRPartBRepository.findByIdAndTraineeTisId(UUID.fromString(id), traineeId)
        .filter(form -> form.getContent() != null)
        .map(formRPartBMapper::toDto);
  }

  /**
   * get FormRPartB by id for admins.
   *
   * @param id The ID of the form to retrieve.
   * @return Optional containing the FormRPartB DTO, or empty if form is DRAFT/DELETED or not found.
   */
  public Optional<FormRPartBDto> getAdminsFormRPartBById(String id) {
    log.info("Request to get FormRPartB by id : {}", id);

    return formRPartBRepository.findByIdAndNotDraftNorDeleted(UUID.fromString(id))
        .filter(form -> form.getContent() != null)
        .map(formRPartBMapper::toDto);
  }

  /**
   * Delete the form for the given ID, only DRAFT forms are supported.
   *
   * @param id The ID of the form to delete.
   * @return true if the form was found and deleted, false if not found.
   */
  public boolean deleteFormRPartBById(String id) {
    log.info("Request to delete FormRPartB by id : {}", id);
    String traineeId = identityResolver.requireTraineeIdentity().getTraineeId();
    Optional<FormRPartB> optionalForm = formRPartBRepository.findByIdAndTraineeTisId(
        UUID.fromString(id), traineeId);

    if (optionalForm.isEmpty()) {
      log.info("FormRPartB {} did not exist.", id);
      return false;
    }

    FormRPartB form = optionalForm.get();
    LifecycleState state = form.getLifecycleState();

    if (!state.equals(DRAFT)) {
      String message = String.format("Unable to delete forms with lifecycle state %s.", state);
      log.warn(message);
      throw new IllegalArgumentException(message);
    }

    formRPartBRepository.delete(form);
    log.info("Deleted FormRPartB {} for trainee {}", id, form.getTraineeTisId());

    return true;
  }

  /**
   * Partially delete a form, leaving only the fixed fields (e.g. IDs, timestamps and state).
   *
   * @param id The ID of the target form.
   * @return The partially deleted form, or empty if the form was not found.
   */
  public Optional<FormRPartBDto> partialDeleteFormRPartBById(UUID id)
      throws MethodArgumentNotValidException {
    log.info("Request to partial delete FormRPartB with id : {}", id);

    Optional<FormRPartB> found = formRPartBRepository.findById(id);

    if (found.isPresent()) {
      FormRPartB form = partialDelete(found.get());
      return Optional.of(formRPartBMapper.toDto(form));
    }

    return Optional.empty();
  }

  /**
   * Partially delete a form, leaving only the fixed fields (e.g. IDs, timestamps and state).
   *
   * @param form The form to partially delete.
   * @return The partially deleted form.
   */
  private FormRPartB partialDelete(FormRPartB form) throws MethodArgumentNotValidException {
    form.setContent(null);
    AdminIdentity adminIdentity = identityResolver.requireAdminIdentity();
    form = updateStatus(form, DELETED, adminIdentity, null);
    publishFormRUpdateEvent(formRPartBMapper.toDto(form));
    log.info("Partial delete successfully for trainee {} with form Id {} (FormRPartB)",
        form.getTraineeTisId(), form.getId());

    return form;
  }

  /**
   * Unsubmit a form, set lifecycle state to UNSUBMITTED.
   *
   * @param id The ID of the target form.
   * @return The unsubmitted form, or empty if the form was not found.
   */
  public Optional<FormRPartBDto> unsubmitFormRPartBById(UUID id)
      throws MethodArgumentNotValidException {
    log.info("Request to unsubmit FormRPartB with id : {}", id);

    Optional<FormRPartB> found = formRPartBRepository.findById(id);
    if (found.isPresent()) {
      AdminIdentity adminIdentity = identityResolver.requireAdminIdentity();
      // TODO: update reason when provided by admin.
      StatusDetail statusDetail = StatusDetail.builder().reason("Requires correction").build();
      FormRPartB form = updateStatus(found.get(), UNSUBMITTED, adminIdentity, statusDetail);
      FormRPartBDto dto = formRPartBMapper.toDto(form);

      publishFormRUpdateEvent(dto);
      log.info("Unsubmitted successfully for trainee {} with form Id {} (FormRPartB)",
          form.getTraineeTisId(), form.getId());
      return Optional.of(dto);
    }

    return Optional.empty();
  }

  /**
   * Publish Form-R status update notification.
   *
   * @param form The updated form.
   */
  @Override
  public void publishStatusUpdateNotification(FormRPartB form) {
    // TODO: do nothing until Form-R events are standardised.
  }

  /**
   * Publish Form-R update notification.
   *
   * @param form     The updated Form-R.
   * @param snsTopic The SNS topic to publish the notification to.
   */
  public void publishUpdateNotification(FormRPartBDto form, String snsTopic) {
    eventBroadcastService.publishFormRPartBEvent(form,
        Map.of(EventBroadcastService.MESSAGE_ATTRIBUTE_KEY_FORM_TYPE, FORM_TYPE), snsTopic);
    log.info("Published update notification for Form-R Part B form {} to SNS topic {}",
        form.getId(), snsTopic);
  }

  /**
   * Publish an updated form.
   *
   * @param formDto The form DTO to publish.
   */
  public void publishFormRUpdateEvent(FormRPartBDto formDto) {
    log.debug("Publishing FormRPartB {} event for form id: {}",
        formDto.getLifecycleState(), formDto.getId());
    publishUpdateNotification(formDto, formRPartBUpdatedTopic);

    // TODO: temporary hack until actions and notifications are migrated to full-form events.
    Map<String, Object> content = objectMapper.convertValue(formDto, Map.class);
    FormrFileEventDto fileEvent = new FormrFileEventDto(formDto.getId() + ".json",
        formDto.getLifecycleState().toString(), formDto.getTraineeTisId(), FORM_TYPE, Instant.now(),
        content);
    eventBroadcastService.publishFormrFileEvent(fileEvent);
  }
}
