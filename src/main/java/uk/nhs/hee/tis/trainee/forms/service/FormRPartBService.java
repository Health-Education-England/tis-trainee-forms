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
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

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

  private final String formRPartBSubmittedTopic;

  @Value("${application.file-store.always-store}")
  private boolean alwaysStoreFiles;

  /**
   * Constructor for a FormR PartB service.
   *
   * @param formRPartBRepository     spring data repository
   * @param s3ObjectRepository       S3 Repository for forms
   * @param formRPartBMapper         maps between the form entity and dto
   * @param objectMapper             The object mapper.
   * @param traineeIdentity          The trainee identity.
   * @param eventBroadcastService    The event broadcast service.
   * @param formRPartBSubmittedTopic The SNS topic for FormR PartB submitted events.
   */
  public FormRPartBService(FormRPartBRepository formRPartBRepository,
      S3FormRPartBRepositoryImpl s3ObjectRepository,
      FormRPartBMapper formRPartBMapper,
      ObjectMapper objectMapper, TraineeIdentity traineeIdentity,
      EventBroadcastService eventBroadcastService,
      @Value("${application.aws.sns.formr-updated}") String formRPartBSubmittedTopic) {
    this.formRPartBRepository = formRPartBRepository;
    this.formRPartBMapper = formRPartBMapper;
    this.s3ObjectRepository = s3ObjectRepository;
    this.objectMapper = objectMapper;
    this.traineeIdentity = traineeIdentity;
    this.eventBroadcastService = eventBroadcastService;
    this.formRPartBSubmittedTopic = formRPartBSubmittedTopic;
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
      log.debug("Publishing FormRPartB submitted event for form id: {}", formRPartB.getId());
      eventBroadcastService.publishFormRPartBEvent(
          formDto,
          Map.of(EventBroadcastService.MESSAGE_ATTRIBUTE_KEY_FORM_TYPE, FORM_TYPE),
          formRPartBSubmittedTopic);
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
      latestForm = cloudForm.getLastModifiedDate().isAfter(dbForm.getLastModifiedDate()) ? cloudForm
          : dbForm;
    } else if (optionalS3Form.isPresent()) {
      latestForm = optionalS3Form.get();
    } else if (optionalDbForm.isPresent()) {
      latestForm = optionalDbForm.get();
    }

    return formRPartBMapper.toDto(latestForm);
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
   * @param id           The ID of the target form.
   * @param traineeTisId The ID of the owning trainee.
   * @return The partially deleted form, or empty if the form was not found.
   */
  public Optional<FormRPartBDto> partialDeleteFormRPartBById(String id, String traineeTisId) {
    log.info("Request to partial delete FormRPartB for trainee {} with id : {}", traineeTisId, id);

    try {
      return formRPartBRepository.findByIdAndTraineeTisId(UUID.fromString(id), traineeTisId)
          .map(this::partialDelete)
          .map(formRPartBMapper::toDto);
    } catch (Exception e) {
      log.error("Fail to partial delete FormRPartB: {}", id);
      throw new ApplicationException("Fail to partial delete FormRPartB:", e);
    }
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
    log.info("Partial delete successfully for trainee {} with form Id {} (FormRPartB)",
        form.getTraineeTisId(), form.getId());

    return form;
  }
}
