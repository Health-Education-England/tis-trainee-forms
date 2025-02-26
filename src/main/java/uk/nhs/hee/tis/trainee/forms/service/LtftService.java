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

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
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
      return ltftFormRepository
          .countByStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
              Set.of(DRAFT), groups);
    }

    states = states.stream().filter(s -> s != DRAFT).collect(Collectors.toSet());
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
    log.info("Getting LTFT forms for admin {} with states {} and DBCs {}", adminIdentity.getEmail(),
        states, groups);
    Page<LtftForm> forms;

    if (states == null || states.isEmpty()) {
      log.debug("No status filter provided, searching all LTFTs.");
      forms = ltftFormRepository
          .findByStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
              Set.of(DRAFT), groups, pageable);
    } else {
      states = states.stream().filter(s -> s != DRAFT).collect(Collectors.toSet());
      forms = ltftFormRepository
          .findByStatus_Current_StateInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
              states, groups, pageable);
    }

    log.info("Found {} total LTFTs, returning page {} of {}", forms.getTotalElements(),
        pageable.getPageNumber(), forms.getTotalPages());
    return forms.map(mapper::toAdminSummaryDto);
  }

  /**
   * Find an LTFT form associated with the local offices of the calling admin.
   *
   * @param formId The ID of the form.
   * @return The found form, empty if the form does not exist or does not match the admin's DBCs.
   */
  public Optional<LtftFormDto> getAdminLtftDetail(UUID formId) {
    Set<String> groups = adminIdentity.getGroups();
    log.info("Getting LTFT form {} for admin {} with DBCs [{}]", formId, adminIdentity.getEmail(),
        groups);
    Optional<LtftForm> form = ltftFormRepository.
        findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            formId, Set.of(DRAFT), adminIdentity.getGroups());
    return form.map(v -> mapper.toDto(v, null));
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
    return form.map(v -> mapper.toDto(v, null));
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
    return Optional.of(mapper.toDto(savedForm, null));
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
    return Optional.of(mapper.toDto(savedForm, null));
  }

  /**
   * Delete the LTFT form with the given id.
   *
   * @param formId The id of the LTFT form to delete.
   * @return Optional empty if the form was not found, true if the form was deleted, or false if it
   *     was not in a permitted state to delete.
   */
  public Optional<Boolean> deleteLtftForm(UUID formId) {
    String traineeId = traineeIdentity.getTraineeId();
    Optional<LtftForm> formOptional = ltftFormRepository.findByTraineeTisIdAndId(traineeId, formId);

    if (formOptional.isEmpty()) {
      log.info("Did not find form {} for trainee [{}]", formId, traineeId);
      return Optional.empty();
    }

    LtftForm form = formOptional.get();
    if (!form.getLifecycleState().equals(DRAFT)) {
      log.info("Form {} was not in a permitted state to delete [{}]", formId,
          form.getLifecycleState());
      return Optional.of(false);
    }

    log.info("Deleting form {} for trainee [{}]", formId, traineeId);
    ltftFormRepository.deleteById(formId);
    return Optional.of(true);
  }

  /**
   * Approve the LTFT form with the given id.
   *
   * @param formId The id of the LTFT form to approve.
   * @param detail The status detail for the approval.
   *
   * @return The DTO of the approved form, or empty if form not found or could not be approved.
   */
  public Optional<LtftFormDto> submitLtftForm(UUID formId,
      LtftFormDto.StatusDto.LftfStatusInfoDetailDto detail) {
    String traineeId = traineeIdentity.getTraineeId();
    Optional<LtftForm> formOptional = ltftFormRepository.findByTraineeTisIdAndId(traineeId, formId);

    if (formOptional.isEmpty()) {
      log.info("Did not find form {} for trainee [{}]", formId, traineeId);
      return Optional.empty();
    }

    LtftForm form = formOptional.get();
    if (!LifecycleState.canTransitionTo(form, LifecycleState.SUBMITTED)) {
      log.info("Form {} was not in a permitted state to submit [{}]", formId,
          form.getLifecycleState());
      return Optional.empty();
    }

    Person modifiedBy = Person.builder()
        .name(traineeIdentity.getName())
        .email(traineeIdentity.getEmail())
        .role(TraineeIdentity.role)
        .build();
    AbstractAuditedForm.Status.StatusDetail statusDetail
        = detail == null ? null : mapper.toStatusDetail(detail);
    log.info("Submitting form {} for trainee [{}]", formId, traineeId);
    form.setLifecycleState(LifecycleState.SUBMITTED, statusDetail, modifiedBy, form.getRevision());
    LtftForm savedForm = ltftFormRepository.save(form);
    return Optional.of(mapper.toDto(savedForm, null));
  }
}
