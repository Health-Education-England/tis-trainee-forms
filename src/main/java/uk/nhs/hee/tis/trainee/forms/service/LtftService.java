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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;

/**
 * A service for managing LTFT forms.
 */
@Slf4j
@Service
@XRayEnabled
public class LtftService {

  private final TraineeIdentity traineeIdentity;
  private final LtftFormRepository ltftFormRepository;
  private final LtftMapper mapper;

  /**
   * Instantiate the LTFT form service.
   *
   * @param traineeIdentity    The logged-in trainee.
   * @param ltftFormRepository The LTFT repository.
   * @param mapper             The LTFT mapper.
   */
  public LtftService(TraineeIdentity traineeIdentity, LtftFormRepository ltftFormRepository,
      LtftMapper mapper) {
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
   * Count all LTFT forms associated with the admin's local office.
   *
   * @param states The states to include in the count.
   * @return The number of found LTFT forms.
   */
  public long getAdminLtftCount(Set<LifecycleState> states) {
    if (states == null || states.isEmpty()) {
      log.debug("No status filter provided, counting all LTFTs.");
      return ltftFormRepository.count();
    }

    return ltftFormRepository.countByStatus_CurrentIn(states);
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
    LtftForm savedForm = ltftFormRepository.save(form);
    return Optional.of(mapper.toDto(savedForm));
  }
}
