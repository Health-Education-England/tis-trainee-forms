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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartARepositoryImpl;

@Slf4j
@Service
@Transactional
@XRayEnabled
public class FormRPartAService {

  public static final String FORM_TYPE = "formr-a";

  private static final String ATTRIBUTE_NAME_LIFE_CYCLE_STATE = "lifecycleState";

  private static final Set<String> FIXED_FIELDS = Set.of(
      "id",
      "traineeTisId",
      ATTRIBUTE_NAME_LIFE_CYCLE_STATE,
      "submissionDate",
      "lastModifiedDate"
  );

  private final FormRPartAMapper mapper;

  private final FormRPartARepository repository;

  private final S3FormRPartARepositoryImpl cloudObjectRepository;

  private final ObjectMapper objectMapper;

  private final TraineeIdentity traineeIdentity;

  private final EventBroadcastService eventBroadcastService;

  private final MongoTemplate mongoTemplate;

  private final String formRPartAUpdatedTopic;

  @Value("${application.file-store.always-store}")
  private boolean alwaysStoreFiles;

  @Value("${application.file-store.bucket}")
  private String bucketName;

  /**
   * Constructor for a FormR PartA service.
   *
   * @param repository               Spring data repository.
   * @param cloudObjectRepository    Repository to storage form in the cloud.
   * @param mapper                   Maps between the form entity and DTO.
   * @param objectMapper             The object mapper.
   * @param traineeIdentity          The trainee identity.
   * @param eventBroadcastService    The event broadcast service.
   * @param mongoTemplate The Mongo template.
   * @param formRPartAUpdatedTopic   The SNS topic for FormR PartA updated events.
   */
  public FormRPartAService(FormRPartARepository repository,
      S3FormRPartARepositoryImpl cloudObjectRepository,
      FormRPartAMapper mapper,
      ObjectMapper objectMapper, TraineeIdentity traineeIdentity,
      EventBroadcastService eventBroadcastService,
      MongoTemplate mongoTemplate,
      @Value("${application.aws.sns.formr-updated}") String formRPartAUpdatedTopic) {
    this.eventBroadcastService = eventBroadcastService;
    this.formRPartAUpdatedTopic = formRPartAUpdatedTopic;
    this.repository = repository;
    this.cloudObjectRepository = cloudObjectRepository;
    this.mapper = mapper;
    this.objectMapper = objectMapper;
    this.traineeIdentity = traineeIdentity;
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * save FormRPartA.
   */
  public FormRPartADto save(FormRPartADto formRPartADto) {
    log.info("Request to save FormRPartA : {}", formRPartADto);
    FormRPartA formRPartA = mapper.toEntity(formRPartADto);
    if (alwaysStoreFiles || formRPartA.getLifecycleState() == LifecycleState.SUBMITTED) {
      cloudObjectRepository.save(formRPartA);
    }

    // Forms stored in cloud are still stored to Mongo for backwards compatibility.
    formRPartA = repository.save(formRPartA);
    FormRPartADto formDto = mapper.toDto(formRPartA);
    if (formRPartA.getLifecycleState() == LifecycleState.SUBMITTED) {
      publishFormRUpdateEvent(formDto);
    }
    return formDto;
  }

  /**
   * get FormRPartAs for logged-in trainee.
   */
  public List<FormRPartSimpleDto> getFormRPartAs() {
    String traineeTisId = traineeIdentity.getTraineeId();
    log.info("Request to get FormRPartA list by trainee profileId : {}", traineeTisId);
    List<FormRPartA> storedFormRPartAs = cloudObjectRepository.findByTraineeTisId(traineeTisId);
    List<FormRPartA> formRPartAList = repository
        .findByTraineeTisIdAndLifecycleState(traineeTisId, LifecycleState.DRAFT);
    storedFormRPartAs.addAll(formRPartAList);
    return mapper.toSimpleDtos(storedFormRPartAs);
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

    String traineeTisId = traineeIdentity.getTraineeId();
    Optional<FormRPartA> optionalCloudForm = cloudObjectRepository.findByIdAndTraineeTisId(id,
        traineeTisId);
    Optional<FormRPartA> optionalDbForm = repository.findByIdAndTraineeTisId(UUID.fromString(id),
        traineeTisId);

    FormRPartA latestForm = null;

    if (optionalCloudForm.isPresent() && optionalDbForm.isPresent()) {
      FormRPartA cloudForm = optionalCloudForm.get();
      FormRPartA dbForm = optionalDbForm.get();
      latestForm = cloudForm.getLastModifiedDate().isBefore(dbForm.getLastModifiedDate()) ? dbForm
          : cloudForm;
    } else if (optionalCloudForm.isPresent()) {
      latestForm = optionalCloudForm.get();
    } else if (optionalDbForm.isPresent()) {
      latestForm = optionalDbForm.get();
    }

    return mapper.toDto(latestForm);
  }

  /**
   * get FormRPartA by id for admins.
   *
   * @param id The ID of the form to retrieve.
   * @return the FormRPartA DTO, or null if form is DRAFT/DELETED or not found.
   */
  public FormRPartADto getAdminsFormRPartAById(String id) {
    log.info("Request to get FormRPartA by id : {}", id);

    Optional<FormRPartA> optionalDbForm = repository.findById(UUID.fromString(id));

    if (optionalDbForm.isEmpty()) {
      return null;
    }

    FormRPartA form = optionalDbForm.get();

    if (form.getLifecycleState() == LifecycleState.DRAFT
        || form.getLifecycleState() == LifecycleState.DELETED) {
      log.info("FormRPartA {} is {}, returning null", id, form.getLifecycleState());
      return null;
    }
    return mapper.toDto(form);
  }

  /**
   * Delete the form for the given ID, only DRAFT forms are supported.
   *
   * @param id The ID of the form to delete.
   * @return true if the form was found and deleted, false if not found.
   */
  public boolean deleteFormRPartAById(String id) {
    log.info("Request to delete FormRPartA by id : {}", id);
    String traineeTisId = traineeIdentity.getTraineeId();
    Optional<FormRPartA> optionalForm = repository.findByIdAndTraineeTisId(UUID.fromString(id),
        traineeTisId);

    if (optionalForm.isEmpty()) {
      log.info("FormRPartA {} did not exist.", id);
      return false;
    }

    FormRPartA form = optionalForm.get();
    LifecycleState state = form.getLifecycleState();

    if (!state.equals(LifecycleState.DRAFT)) {
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
  public Optional<FormRPartADto> partialDeleteFormRPartAById(UUID id) {
    log.info("Request to partial delete FormRPartA with id : {}", id);

    return repository.findById(id)
        .map(this::partialDelete)
        .map(cloudObjectRepository::save) // TODO: remove S3 update when fully migrated.
        .map(mapper::toDto);
  }

  /**
   * Partially delete a form, leaving only the fixed fields (e.g. IDs, timestamps and state).
   *
   * @param form The form to partially delete.
   * @return The partially deleted form.
   */
  private FormRPartA partialDelete(FormRPartA form) {
    JsonNode jsonForm = objectMapper.valueToTree(form);

    for (Iterator<String> fieldIterator = jsonForm.fieldNames(); fieldIterator.hasNext(); ) {
      String fieldName = fieldIterator.next();

      if (!FIXED_FIELDS.contains(fieldName)) {
        fieldIterator.remove();
      }
    }
    ((ObjectNode) jsonForm).put(ATTRIBUTE_NAME_LIFE_CYCLE_STATE,
        LifecycleState.DELETED.name());
    form = objectMapper.convertValue(jsonForm, FormRPartA.class);

    repository.save(form);
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
  public Optional<FormRPartADto> unsubmitFormRPartAById(UUID id) {
    log.info("Request to unsubmit FormRPartA with id : {}", id);

    return repository.findById(id)
        .map(form -> {
          form.setLifecycleState(LifecycleState.UNSUBMITTED);
          FormRPartA formRPartA = repository.save(form);
          publishFormRUpdateEvent(mapper.toDto(form));
          log.info("Unsubmitted successfully for trainee {} with form Id {} (FormRPartA)",
              form.getTraineeTisId(), form.getId());
          return formRPartA;
        })
        .map(cloudObjectRepository::save) // TODO: remove S3 update when fully migrated.
        .map(mapper::toDto);
  }

  /**
   * Publish an updated form.
   *
   * @param formDto The form DTO to publish.
   */
  private void publishFormRUpdateEvent(FormRPartADto formDto) {
    log.debug("Publishing FormRPartA {} event for form id: {}",
        formDto.getLifecycleState(), formDto.getId());
    eventBroadcastService.publishFormRPartAEvent(
        formDto,
        Map.of(EventBroadcastService.MESSAGE_ATTRIBUTE_KEY_FORM_TYPE, FORM_TYPE),
        formRPartAUpdatedTopic);
  }
}
