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

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartBRepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.EventBroadcastService.FormrFileEventDto;

@Slf4j
@Service
@Transactional
@XRayEnabled
public class FormRPartBService {

  public static final String FORM_TYPE = "formr-b";

  private static final String ATTRIBUTE_NAME_LIFE_CYCLE_STATE = "lifecycleState";

  private static final Set<String> FIXED_FIELDS = Set.of(
      "id",
      "traineeTisId",
      ATTRIBUTE_NAME_LIFE_CYCLE_STATE,
      "submissionDate",
      "lastModifiedDate"
  );

  private final FormRPartBMapper formRPartBMapper;

  private final FormRPartBRepository formRPartBRepository;

  private final S3FormRPartBRepositoryImpl s3ObjectRepository;

  private final ObjectMapper objectMapper;

  private final TraineeIdentity traineeIdentity;

  private final EventBroadcastService eventBroadcastService;

  private final String formRPartBUpdatedTopic;

  @Value("${application.file-store.always-store}")
  private boolean alwaysStoreFiles;

  /**
   * Constructor for a FormR PartB service.
   *
   * @param formRPartBRepository   spring data repository
   * @param s3ObjectRepository     S3 Repository for forms
   * @param formRPartBMapper       maps between the form entity and dto
   * @param objectMapper           The object mapper.
   * @param traineeIdentity        The trainee identity.
   * @param eventBroadcastService  The event broadcast service.
   * @param formRPartBUpdatedTopic The SNS topic for FormR PartB updated events.
   */
  public FormRPartBService(FormRPartBRepository formRPartBRepository,
      S3FormRPartBRepositoryImpl s3ObjectRepository,
      FormRPartBMapper formRPartBMapper,
      ObjectMapper objectMapper, TraineeIdentity traineeIdentity,
      EventBroadcastService eventBroadcastService,
      @Value("${application.aws.sns.formr-updated}") String formRPartBUpdatedTopic) {
    this.formRPartBRepository = formRPartBRepository;
    this.formRPartBMapper = formRPartBMapper;
    this.s3ObjectRepository = s3ObjectRepository;
    this.objectMapper = objectMapper;
    this.traineeIdentity = traineeIdentity;
    this.eventBroadcastService = eventBroadcastService;
    this.formRPartBUpdatedTopic = formRPartBUpdatedTopic;
  }

  /**
   * save FormRPartB.
   */
  public FormRPartBDto save(FormRPartBDto formRPartBDto) {
    log.info("Request to save FormRPartB : {}", formRPartBDto);
    FormRPartB formRPartB = formRPartBMapper.toEntity(formRPartBDto);
    if (alwaysStoreFiles || formRPartB.getLifecycleState() == LifecycleState.SUBMITTED) {
      s3ObjectRepository.save(formRPartB);
    }

    // Forms stored in cloud are still stored to Mongo for backwards compatibility.
    formRPartB = formRPartBRepository.save(formRPartB);
    FormRPartBDto formDto = formRPartBMapper.toDto(formRPartB);
    if (formRPartB.getLifecycleState() == LifecycleState.SUBMITTED) {
      publishFormRUpdateEvent(formDto);
    }
    return formDto;
  }

  /**
   * get FormRPartBs for logged-in trainee.
   */
  public List<FormRPartSimpleDto> getFormRPartBs() {
    String traineeTisId = traineeIdentity.getTraineeId();
    log.info("Request to get FormRPartB list by trainee profileId : {}", traineeTisId);
    List<FormRPartB> storedFormRPartBs = s3ObjectRepository.findByTraineeTisId(traineeTisId);
    List<FormRPartB> formRPartBList = formRPartBRepository
        .findByTraineeTisIdAndLifecycleState(traineeTisId, LifecycleState.DRAFT);
    storedFormRPartBs.addAll(formRPartBList);
    return formRPartBMapper.toSimpleDtos(storedFormRPartBs);
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
        .findNotDraftNorDeletedByTraineeTisId(traineeId);

    return formRPartBMapper.toSimpleDtos(formRPartBList);
  }

  /**
   * get FormRPartB by id.
   */
  public FormRPartBDto getFormRPartBById(String id) {
    log.info("Request to get FormRPartB by id : {}", id);

    String traineeTisId = traineeIdentity.getTraineeId();
    Optional<FormRPartB> optionalS3Form = s3ObjectRepository.findByIdAndTraineeTisId(id,
        traineeTisId);
    Optional<FormRPartB> optionalDbForm = formRPartBRepository.findByIdAndTraineeTisId(
        UUID.fromString(id), traineeTisId);

    FormRPartB latestForm = null;

    if (optionalS3Form.isPresent() && optionalDbForm.isPresent()) {
      FormRPartB cloudForm = optionalS3Form.get();
      FormRPartB dbForm = optionalDbForm.get();
      latestForm = cloudForm.getLastModifiedDate().isBefore(dbForm.getLastModifiedDate()) ? dbForm
          : cloudForm;
    } else if (optionalS3Form.isPresent()) {
      latestForm = optionalS3Form.get();
    } else if (optionalDbForm.isPresent()) {
      latestForm = optionalDbForm.get();
    }

    return formRPartBMapper.toDto(latestForm);
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
    String traineeTisId = traineeIdentity.getTraineeId();
    Optional<FormRPartB> optionalForm = formRPartBRepository.findByIdAndTraineeTisId(
        UUID.fromString(id), traineeTisId);

    if (optionalForm.isEmpty()) {
      log.info("FormRPartB {} did not exist.", id);
      return false;
    }

    FormRPartB form = optionalForm.get();
    LifecycleState state = form.getLifecycleState();

    if (!state.equals(LifecycleState.DRAFT)) {
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
  public Optional<FormRPartBDto> partialDeleteFormRPartBById(UUID id) {
    log.info("Request to partial delete FormRPartB with id : {}", id);

    return formRPartBRepository.findById(id)
        .map(this::partialDelete)
        .map(s3ObjectRepository::save) // TODO: remove S3 update when fully migrated.
        .map(formRPartBMapper::toDto);
  }

  /**
   * Partially delete a form, leaving only the fixed fields (e.g. IDs, timestamps and state).
   *
   * @param form The form to partially delete.
   * @return The partially deleted form.
   */
  private FormRPartB partialDelete(FormRPartB form) {
    JsonNode jsonForm = objectMapper.valueToTree(form);

    for (Iterator<String> fieldIterator = jsonForm.fieldNames(); fieldIterator.hasNext(); ) {
      String fieldName = fieldIterator.next();

      if (!FIXED_FIELDS.contains(fieldName)) {
        fieldIterator.remove();
      }
    }
    ((ObjectNode) jsonForm).put(ATTRIBUTE_NAME_LIFE_CYCLE_STATE,
        LifecycleState.DELETED.name());
    form = objectMapper.convertValue(jsonForm, FormRPartB.class);

    formRPartBRepository.save(form);
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
  public Optional<FormRPartBDto> unsubmitFormRPartBById(UUID id) {
    log.info("Request to unsubmit FormRPartB with id : {}", id);

    return formRPartBRepository.findById(id)
        .map(form -> {
          form.setLifecycleState(LifecycleState.UNSUBMITTED);
          FormRPartB formRPartB = formRPartBRepository.save(form);
          publishFormRUpdateEvent(formRPartBMapper.toDto(form));
          log.info("Unsubmitted successfully for trainee {} with form Id {} (FormRPartB)",
              form.getTraineeTisId(), form.getId());
          return formRPartB;
        })
        .map(s3ObjectRepository::save) // TODO: remove S3 update when fully migrated.
        .map(formRPartBMapper::toDto);
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
  private void publishFormRUpdateEvent(FormRPartBDto formDto) {
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
