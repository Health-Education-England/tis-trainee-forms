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

package uk.nhs.hee.tis.trainee.forms.service;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.LtftStatusInfoDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo.StatusDetail;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;

/**
 * A service for managing LTFT forms.
 */
@Slf4j
@Service
@XRayEnabled
public class LtftService {

  private final AdminIdentity adminIdentity;
  private final TraineeIdentity traineeIdentity;
  private final LtftFormRepository ltftFormRepository;
  private final LtftMapper mapper;

  /**
   * Instantiate the LTFT form service.
   *
   * @param adminIdentity      The logged-in admin, for admin features.
   * @param traineeIdentity    The logged-in trainee, for trainee features.
   * @param ltftFormRepository The LTFT repository.
   * @param mapper             The LTFT mapper.
   */
  public LtftService(AdminIdentity adminIdentity, TraineeIdentity traineeIdentity,
      LtftFormRepository ltftFormRepository, LtftMapper mapper) {
    this.adminIdentity = adminIdentity;
    this.traineeIdentity = traineeIdentity;
    this.ltftFormRepository = ltftFormRepository;
    this.mapper = mapper;
  }

  /**
   * Get a list of LTFT forms for the current user.
   *
   * @return Summaries of the found LTFT forms, or empty if none found.
   */
  public List<LtftSummaryDto> getLtftSummaries() {
    String traineeId = traineeIdentity.getTraineeId();
    log.info("Getting LTFT form summaries for trainee [{}]", traineeId);

    List<LtftForm> entities = ltftFormRepository.findByTraineeTisIdOrderByLastModified(
        traineeId);
    log.info("Found {} LTFT forms for trainee [{}]", entities.size(), traineeId);

    return mapper.toSummaryDtos(entities);
  }

  /**
   * Count all LTFT forms associated with the local offices of the calling admin.
   *
   * @param states The states to include in the count.
   * @return The number of found LTFT forms.
   */
  public long getAdminLtftCount(Set<LifecycleState> states) {
    Set<String> groups = adminIdentity.getGroups();

    if (states == null || states.isEmpty()) {
      log.debug("No status filter provided, counting all LTFTs.");
      return ltftFormRepository.countByContent_ProgrammeMembership_DesignatedBodyCodeIn(groups);
    }

    return ltftFormRepository
        .countByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(states,
            groups);
  }

  /**
   * Find all LTFT forms associated with the local offices of the calling admin.
   *
   * @param states   The states to include in the count.
   * @param pageable The page information to apply to the search.
   * @return A page of found LTFT forms.
   */
  public Page<LtftAdminSummaryDto> getAdminLtftSummaries(Set<LifecycleState> states,
      Pageable pageable) {
    Set<String> groups = adminIdentity.getGroups();
    Page<LtftForm> forms;

    if (states == null || states.isEmpty()) {
      log.debug("No status filter provided, searching all LTFTs.");
      forms = ltftFormRepository.findByContent_ProgrammeMembership_DesignatedBodyCodeIn(groups,
          pageable);
    } else {
      forms = ltftFormRepository
          .findByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(states,
              groups, pageable);
    }

    return forms.map(mapper::toAdminSummaryDto);
  }

  /**
   * Get the 'id' LTFT form if it belongs to the current user.
   *
   * @return The LTFT form, or optional empty if not found or does not belong to user.
   */
  public Optional<LtftFormDto> getLtftForm(UUID formId) {
    String traineeId = traineeIdentity.getTraineeId();
    log.info("Getting LTFT form {} for trainee [{}]", formId, traineeId);

    Optional<LtftForm> form = ltftFormRepository.findByTraineeTisIdAndId(traineeId, formId);
    form.ifPresentOrElse(
        value -> log.info("Found form {} for trainee [{}]", formId, traineeId),
        () -> log.info("Did not find form {} for trainee [{}]", formId, traineeId)
    );
    return form.map(mapper::toDto);
  }

  /**
   * Save the dto as a new LTFT form.
   *
   * @param dto The LTFT DTO to save.
   * @return The saved form DTO.
   */
  public Optional<LtftFormDto> saveLtftForm(LtftFormDto dto) {
    String traineeId = traineeIdentity.getTraineeId();
    log.info("Saving LTFT form for trainee [{}]: {}", traineeId, dto);
    LtftForm form = mapper.toEntity(dto);
    if (!form.getTraineeTisId().equals(traineeId)) {
      log.warn("Could not save form since it does belong to the logged-in trainee {}: {}",
          traineeId, dto);
      return Optional.empty();
    }
    LtftForm savedForm = ltftFormRepository.save(form);
    return Optional.of(mapper.toDto(savedForm));
  }

  /**
   * Update the existing LTFT form.
   *
   * @param formId The id of the LTFT form to update.
   * @param dto    The updated LTFT DTO to save.
   * @return The updated form DTO.
   */
  public Optional<LtftFormDto> updateLtftForm(UUID formId, LtftFormDto dto) {
    String traineeId = traineeIdentity.getTraineeId();
    log.info("Updating LTFT form {} for trainee [{}]: {}", formId, traineeId, dto);
    LtftForm form = mapper.toEntity(dto);
    if (form.getId() == null || !form.getId().equals(formId)) {
      log.warn("Could not update form since its id {} does not equal provided form id {}",
          form.getId(), formId);
      return Optional.empty();
    }
    if (!form.getTraineeTisId().equals(traineeId)) {
      log.warn("Could not update form since it does belong to the logged-in trainee {}: {}",
          traineeId, dto);
      return Optional.empty();
    }
    Optional<LtftForm> existingForm = ltftFormRepository.findByTraineeTisIdAndId(traineeId, formId);
    if (existingForm.isEmpty()) {
      log.warn("Could not update form {} since no existing form with this id for trainee {}",
          formId, traineeId);
      return Optional.empty();
    }
    form.setCreated(existingForm.get().getCreated()); //explicitly set otherwise form saved as 'new'
    LtftForm savedForm = ltftFormRepository.save(form);
    return Optional.of(mapper.toDto(savedForm));
  }

  /**
   * Update the status of an LTFT form as an admin, the form must be associated with the admin's
   * local office.
   *
   * @param formId The ID of the form to update the status of.
   * @param state  The new state.
   * @param detail A detailed reason for the change, may be null.
   * @return The updated LTFT application, empty if the form did not exist or did not belong to the
   * admin's local office.
   * @throws MethodArgumentNotValidException If the state transition is not allowed.
   */
  public Optional<LtftFormDto> updateLtftStatusAsAdmin(UUID formId, LifecycleState state,
      LftfStatusInfoDetailDto detail) throws MethodArgumentNotValidException {
    log.info("Updating LTFT form {} as admin [{}]: New state = {}", formId,
        adminIdentity.getEmail(), state);

    Set<String> dbcs = adminIdentity.getGroups();
    Optional<LtftForm> form = ltftFormRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        formId, dbcs);

    if (form.isPresent()) {
      form = updateLtftStatus(form.get(), state,
          Person.builder().name(adminIdentity.getName()).email(adminIdentity.getEmail())
              .role("ADMIN").build(), detail);
    } else {
      log.warn("Could not update form {} since no form exists with this ID for DBCs [{}]",
          formId, dbcs);
    }

    return form.map(mapper::toDto);
  }

  /**
   * Update the status of an LTFT form as an admin, the form must be associated with the admin's
   * local office.
   *
   * @param formId The ID of the form to update the status of.
   * @param state  The new state.
   * @return The updated LTFT application, empty if the form did not exist or did not belong to the
   * trainee.
   * @throws MethodArgumentNotValidException If the state transition is not allowed.
   */
  public Optional<LtftFormDto> updateLtftStatusAsTrainee(UUID formId, LifecycleState state)
      throws MethodArgumentNotValidException {
    String traineeId = traineeIdentity.getTraineeId();
    log.info("Updating LTFT form {} as trainee [{}]: New state = {}", formId, traineeId, state);

    Optional<LtftForm> form = ltftFormRepository.findByTraineeTisIdAndId(traineeId, formId);

    if (form.isPresent()) {
      form = updateLtftStatus(form.get(), state, Person.builder().role("TRAINEE").build(), null);
    } else {
      log.warn("Could not update form {} since no existing form with this id for trainee {}",
          formId, traineeId);
    }

    return form.map(mapper::toDto);
  }

  /**
   * Update the status of the LTFT, the current status and history will both be updated.
   *
   * @param form   The form to update the status of.
   * @param state  The new state.
   * @param actor  Who is performing the status change.
   * @param detail A detailed reason for the change, may be null.
   * @return The updated LTFT application.
   * @throws MethodArgumentNotValidException If the state transition is not allowed.
   */
  private Optional<LtftForm> updateLtftStatus(LtftForm form, LifecycleState state, Person actor,
      @Nullable LftfStatusInfoDetailDto detail) throws MethodArgumentNotValidException {
    if (!LifecycleState.canTransitionTo(form, state)) {

      BeanPropertyBindingResult result = new BeanPropertyBindingResult(form, "form");
      result.addError(new FieldError("LtftForm", "status.current.state",
          "can not be transitioned to %s".formatted(state)));

      log.warn(
          "Could not update form {}, invalid lifecycle transition from {}} to {} for form type '{}'",
          form.getId(), form.getStatus().current().state(), state, form.getFormType());

      throw new MethodArgumentNotValidException(null, result);
    }

    // TODO: move to mapper?
    StatusDetail detailEntity = null;
    if (detail != null) {
      detailEntity = StatusDetail.builder()
          .reason(detail.getReason())
          .message(detail.getMessage())
          .build();
    }

    StatusInfo newStatusInfo = StatusInfo.builder()
        .state(state)
        .detail(detailEntity)
        .modifiedBy(actor)
        .timestamp(Instant.now())
        .build();

    Status status = form.getStatus();
    status = status.withCurrent(newStatusInfo);
    status.history().add(newStatusInfo);
    form.setStatus(status);

    form.setCreated(form.getCreated()); //explicitly set otherwise form saved as 'new'
    LtftForm savedForm = ltftFormRepository.save(form);
    return Optional.of(savedForm);
  }

  /**
   * Delete the LTFT form with the given id.
   *
   * @param formId The id of the LTFT form to delete.
   * @return Optional empty if the form was not found, true if the form was deleted, or false if it
   * was not in a permitted state to delete.
   */
  public Optional<Boolean> deleteLtftForm(UUID formId) {
    String traineeId = traineeIdentity.getTraineeId();
    Optional<LtftForm> formOptional = ltftFormRepository.findByTraineeTisIdAndId(traineeId, formId);

    if (formOptional.isEmpty()) {
      log.info("Did not find form {} for trainee [{}]", formId, traineeId);
      return Optional.empty();
    }

    LtftForm form = formOptional.get();
    if (!LifecycleState.canTransitionTo(form, LifecycleState.DELETED)) {
      log.info("Form {} was not in a permitted state to delete [{}]", formId,
          form.getLifecycleState());
      return Optional.of(false);
    }

    log.info("Deleting form {} for trainee [{}]", formId, traineeId);
    ltftFormRepository.deleteById(formId);
    return Optional.of(true);
  }
}
