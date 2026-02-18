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

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.VALID;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.WITHDRAWN;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto.FormFeatures.LtftFeatures;
import uk.nhs.hee.tis.trainee.forms.dto.FormPatchDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormPatchResultDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.UserIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusDetail;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;

/**
 * A service for managing LTFT forms.
 */
@Slf4j
@Service
@XRayEnabled
public class LtftService {

  protected static final String FORM_ATTRIBUTE_FORM_STATUS = "status.current.state";
  protected static final String FORM_ATTRIBUTE_TPD_STATUS = "content.discussions.tpdStatus";

  private final AdminIdentity adminIdentity;
  private final TraineeIdentity traineeIdentity;

  private final LtftFormRepository ltftFormRepository;
  private final MongoTemplate mongoTemplate;

  private final ObjectMapper objectMapper;
  private final LtftMapper mapper;
  private final Validator validator;

  private final EventBroadcastService eventBroadcastService;

  @Getter
  private final String ltftAssignmentUpdateTopic;
  private final String ltftStatusUpdateTopic;
  private final String ltftContentUpdateTopic;

  private final LtftSubmissionHistoryService ltftSubmissionHistoryService;

  /**
   * Instantiate the LTFT form service.
   *
   * @param adminIdentity                The logged-in admin, for admin features.
   * @param traineeIdentity              The logged-in trainee, for trainee features.
   * @param ltftFormRepository           The LTFT repository.
   * @param mongoTemplate                The Mongo template.
   * @param objectMapper                 The JSON mapper.
   * @param mapper                       The LTFT mapper.
   * @param validator                    The validator to use for validating LTFTs.
   * @param eventBroadcastService        The service for broadcasting events.
   * @param ltftAssignmentUpdateTopic    The SNS topic for LTFT assignment updates.
   * @param ltftStatusUpdateTopic        The SNS topic for LTFT status updates.
   * @param ltftContentUpdateTopic       The SNS topic for LTFT content updates.
   * @param ltftSubmissionHistoryService The service for LTFT submission history.
   */
  public LtftService(AdminIdentity adminIdentity, TraineeIdentity traineeIdentity,
      LtftFormRepository ltftFormRepository, MongoTemplate mongoTemplate, ObjectMapper objectMapper,
      LtftMapper mapper, Validator validator, EventBroadcastService eventBroadcastService,
      @Value("${application.aws.sns.ltft-assignment-updated}") String ltftAssignmentUpdateTopic,
      @Value("${application.aws.sns.ltft-status-updated}") String ltftStatusUpdateTopic,
      @Value("${application.aws.sns.ltft-content-updated}") String ltftContentUpdateTopic,
      LtftSubmissionHistoryService ltftSubmissionHistoryService) {
    this.adminIdentity = adminIdentity;
    this.traineeIdentity = traineeIdentity;
    this.ltftFormRepository = ltftFormRepository;
    this.mongoTemplate = mongoTemplate;
    this.objectMapper = objectMapper;
    this.mapper = mapper;
    this.validator = validator;
    this.ltftAssignmentUpdateTopic = ltftAssignmentUpdateTopic;
    this.ltftStatusUpdateTopic = ltftStatusUpdateTopic;
    this.ltftContentUpdateTopic = ltftContentUpdateTopic;
    this.ltftSubmissionHistoryService = ltftSubmissionHistoryService;
    this.eventBroadcastService = eventBroadcastService;
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
   * @param filterParams The parameters to filter results by.
   * @return The number of found LTFT forms.
   */
  public long getAdminLtftCount(Map<String, String> filterParams) {
    Query query = buildAdminFilteredQuery(filterParams, Pageable.unpaged());
    return mongoTemplate.count(query, LtftForm.class);
  }

  /**
   * Find all LTFT forms associated with the local offices of the calling admin.
   *
   * @param filterParams The parameters to filter results by.
   * @param pageable     The page information to apply to the search.
   * @return A page of found LTFT forms.
   */
  public Page<LtftAdminSummaryDto> getAdminLtftSummaries(Map<String, String> filterParams,
      Pageable pageable) {
    Set<String> groups = adminIdentity.getGroups();
    log.info("Getting LTFT forms for admin {} with DBCs {}", adminIdentity.getEmail(), groups);
    Page<LtftForm> forms;

    Query query = buildAdminFilteredQuery(filterParams, pageable);
    List<LtftForm> formsList = mongoTemplate.find(query, LtftForm.class);

    forms = PageableExecutionUtils.getPage(formsList, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), LtftForm.class));

    if (pageable.isPaged()) {
      log.info("Found {} total LTFTs, returning page {} of {}", forms.getTotalElements(),
          pageable.getPageNumber(), forms.getTotalPages());
    } else {
      log.info("Found {} total LTFTs, returning all results", forms.getTotalElements());
    }
    return forms.map(mapper::toAdminSummaryDto);
  }

  /**
   * Find an LTFT form associated with the local offices of the calling admin.
   *
   * @param formId The ID of the form.
   * @return The found form, empty if the form does not exist or does not match the admin's DBCs.
   */
  public Optional<LtftFormDto> getAdminLtftDetail(UUID formId) {
    return getLtftForAdmin(formId).map(mapper::toDto);
  }

  /**
   * Find an LTFT form associated with the local offices of the calling admin.
   *
   * @param formId The ID of the form.
   * @return The found form, empty if the form does not exist or does not match the admin's DBCs.
   */
  private Optional<LtftForm> getLtftForAdmin(UUID formId) {
    Set<String> groups = adminIdentity.getGroups();
    log.info("Getting LTFT form {} for admin {} with DBCs [{}]", formId, adminIdentity.getEmail(),
        groups);
    return ltftFormRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            formId, Set.of(DRAFT), adminIdentity.getGroups());
  }

  /**
   * Apply a patch update to the LTFT with the given ID.
   *
   * @param formId    The ID of the form.
   * @param formPatch The patch and metadata to update the form with.
   * @return The patched form, empty if the form does not exist or does not match the admin's DBCs.
   */
  public Optional<LtftFormDto> applyAdminPatch(UUID formId, FormPatchDto formPatch) {
    log.info("Applying patch to form '{}': {}", formId, formPatch);
    return getLtftForAdmin(formId)
        // Will result in NOT FOUND when not submitted, which mirrors GET behaviour.
        .filter(ltft -> ltft.getLifecycleState().equals(SUBMITTED))

        .map(ltft -> {
          try {
            // Patch the content and copy it back in to avoid changes to read-only fields.
            FormPatchResultDto<LtftContent> patchResult = patchLtftContent(ltft, formPatch.patch());

            // Do not increment revision or snapshot if no changes made to the content.
            if (patchResult.changed()) {
              ltft.setContent(patchResult.patchedContent());

              StatusDetail statusDetail = StatusDetail.builder()
                  .reason(formPatch.reason())
                  .message(formPatch.message())
                  .build();

              // An admin patch is considered a shortcut of the un-submit -> re-submit revision flow.
              Person modifiedBy = Person.builder()
                  .name(adminIdentity.getName())
                  .email(adminIdentity.getEmail())
                  .role(adminIdentity.getRole())
                  .build();
              ltft.setRevision(ltft.getRevision() + 1);
              ltft.setLifecycleState(ltft.getLifecycleState(), statusDetail, modifiedBy,
                  ltft.getRevision());
              ltft = ltftFormRepository.save(ltft);
              ltftSubmissionHistoryService.takeSnapshot(ltft);

              publishUpdateNotification(ltft, null, ltftContentUpdateTopic);
            } else {
              log.debug("Patch did not make changes, returning unchanged object.");
            }

            return mapper.toDto(ltft);
          } catch (JsonPatchException | JsonProcessingException e) {
            // Should not happen given validation and known JSON structures, inform caller anyway.
            throw new RuntimeException(e);
          }
        });
  }

  /**
   * Update an {@link LtftForm} content with the given patch.
   *
   * @param ltft  The LTFT form to apply the patch to.
   * @param patch The patch to apply to the form.
   * @return The patched form content.
   * @throws JsonPatchException      If the patch could not be applied.
   * @throws JsonProcessingException If the patch creates an invalid form.
   */
  private FormPatchResultDto<LtftContent> patchLtftContent(LtftForm ltft, JsonPatch patch)
      throws JsonPatchException, JsonProcessingException {
    // Convert the entity to DTO for patching, as the client will base paths on the public DTO.
    LtftFormDto dto = mapper.toDto(ltft);
    JsonNode patchedNode = patch.apply(objectMapper.convertValue(dto, JsonNode.class));
    LtftFormDto patchedDto = objectMapper.treeToValue(patchedNode, LtftFormDto.class);

    // Read-only fields can not be reliably ignored when using the Update validation group so the
    // default group is used, which may miss some otherwise expected validations.
    Set<ConstraintViolation<LtftFormDto>> violations = validator.validate(patchedDto);

    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }

    // The content is returned to avoid any unexpected changes to managed/read-only fields.
    LtftContent patchedContent = mapper.toEntity(patchedDto).getContent();
    return new FormPatchResultDto<>(patchedContent, !dto.equals(patchedDto));
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
  public Optional<LtftFormDto> createLtftForm(LtftFormDto dto) {
    String traineeId = traineeIdentity.getTraineeId();
    log.info("Saving LTFT form for trainee [{}]: {}", traineeId, dto);
    LtftForm form = mapper.toEntity(dto);

    // Set initial DRAFT status.
    Person modifiedBy = Person.builder()
        .name(traineeIdentity.getName())
        .email(traineeIdentity.getEmail())
        .role(traineeIdentity.getRole())
        .build();
    form.setLifecycleState(DRAFT, null, modifiedBy, 0);

    if (!form.getTraineeTisId().equals(traineeId)) {
      log.warn("Could not save form since it does belong to the logged-in trainee {}: {}",
          traineeId, dto);
      return Optional.empty();
    }

    UUID programmeMembershipId = form.getContent().programmeMembership().id();
    if (!isProgrammeMembershipValidForLtft(programmeMembershipId)) {
      log.warn("Could not save form as it is not for a LTFT-enabled programme membership {}: {}",
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
    Optional<LtftForm> foundForm = ltftFormRepository.findByTraineeTisIdAndId(traineeId, formId);
    if (foundForm.isEmpty()) {
      log.warn("Could not update form {} since no existing form with this id for trainee {}",
          formId, traineeId);
      return Optional.empty();
    }

    LtftForm existingForm = foundForm.get();
    LifecycleState existingState = existingForm.getLifecycleState();

    if (existingState != DRAFT && existingState != UNSUBMITTED) {
      log.warn("Could not update form {} for trainee {} since state {} is not editable",
          formId, traineeId, existingState);
      return Optional.empty();
    }

    UUID programmeMembershipId = form.getContent().programmeMembership().id();
    if (!isProgrammeMembershipValidForLtft(programmeMembershipId)) {
      log.warn("Could not update form {} for trainee {} as new programme membership {} is not "
          + "LTFT-enabled", formId, traineeId, programmeMembershipId);
      return Optional.empty();
    }

    // Merge the new content in to the existing form.
    existingForm.setContent(form.getContent());

    LtftForm savedForm = ltftFormRepository.save(existingForm);
    return Optional.of(mapper.toDto(savedForm));
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
   * Submit the LTFT form with the given id.
   *
   * @param formId The id of the LTFT form to submit.
   * @param detail The status detail for the submission.
   * @return The DTO of the submitted form, or empty if form not found or could not be submitted.
   */
  public Optional<LtftFormDto> submitLtftForm(UUID formId, LftfStatusInfoDetailDto detail) {
    return changeLtftFormState(formId, detail, SUBMITTED);
  }

  /**
   * Unsubmit the LTFT form with the given id.
   *
   * @param formId The id of the LTFT form to unsubmit.
   * @param detail The status detail for the unsubmission.
   * @return The DTO of the unsubmitted form, or empty if form not found or could not be
   *     unsubmitted.
   */
  public Optional<LtftFormDto> unsubmitLtftForm(UUID formId, LftfStatusInfoDetailDto detail) {
    return changeLtftFormState(formId, detail, UNSUBMITTED);
  }

  /**
   * Withdraw the LTFT form with the given id.
   *
   * @param formId The id of the LTFT form to withdraw.
   * @param detail The status detail for the withdrawal.
   * @return The DTO of the withdrawn form, or empty if form not found or could not be withdrawn.
   */
  public Optional<LtftFormDto> withdrawLtftForm(UUID formId, LftfStatusInfoDetailDto detail) {
    return changeLtftFormState(formId, detail, WITHDRAWN);
  }

  /**
   * Change the state of the LTFT form with the given id.
   *
   * @param formId      The id of the LTFT form to change.
   * @param detail      The status detail for the change.
   * @param targetState The state to change to.
   * @return The DTO of the form after the state change, or empty if form not found or could not be
   *     changed to the target state.
   */
  protected Optional<LtftFormDto> changeLtftFormState(UUID formId, LftfStatusInfoDetailDto detail,
      LifecycleState targetState) {
    String traineeId = traineeIdentity.getTraineeId();
    Optional<LtftForm> formOptional = ltftFormRepository.findByTraineeTisIdAndId(traineeId, formId);

    if (formOptional.isEmpty()) {
      log.info("Did not find form {} for trainee [{}] to change state to {}",
          formId, traineeId, targetState);
      return Optional.empty();
    }

    log.info("Changing form {} state to {} for trainee [{}]", formId, targetState, traineeId);
    LtftForm form = formOptional.get();

    try {
      LtftForm updatedForm = updateStatus(form, targetState, traineeIdentity, detail);
      return Optional.of(mapper.toDto(updatedForm));
    } catch (MethodArgumentNotValidException e) {
      return Optional.empty();
    }
  }

  /**
   * Assign an admin to the LTFT application.
   *
   * @param formId The ID of the LTFT application.
   * @param admin  The admin to assign to the application.
   * @return The updated LTFT, empty if the form did not exist or did not belong to the admin's
   *     local office.
   */
  public Optional<LtftFormDto> assignAdmin(UUID formId, PersonDto admin) {
    log.info("Assigning admin {} to LTFT form {}", admin.email(), formId);

    Set<String> dbcs = adminIdentity.getGroups();
    Optional<LtftForm> form =
        ltftFormRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(formId,
            dbcs);

    if (form.isPresent()) {
      LtftForm ltftForm = form.get();

      Person assignedAdmin = mapper.toEntity(admin).withRole("ADMIN");

      if (ltftForm.getStatus() != null && ltftForm.getStatus().current() != null
          && Objects.equals(ltftForm.getStatus().current().assignedAdmin(), assignedAdmin)) {
        log.info("Skipping assigning admin {} to LTFT form {}, as they are already assigned.",
            admin.email(), formId);
        return Optional.of(mapper.toDto(ltftForm));
      }

      Person modifiedBy = Person.builder()
          .name(adminIdentity.getName())
          .email(adminIdentity.getEmail())
          .role(adminIdentity.getRole())
          .build();

      ltftForm.setAssignedAdmin(assignedAdmin, modifiedBy);
      LtftForm updatedForm = ltftFormRepository.save(ltftForm);

      publishUpdateNotification(updatedForm, null, ltftAssignmentUpdateTopic);

      return Optional.of(mapper.toDto(updatedForm));
    } else {
      log.warn("Could not assign admin to form {} since no form exists with this ID for DBCs [{}]",
          formId, dbcs);
      return Optional.empty();
    }
  }

  /**
   * Update the TPD notification status of an LTFT form.
   *
   * @param formId The ID of the LTFT form to update.
   * @param status The new TPD notification status to set.
   * @return The admin summary DTO of the updated LTFT form, or empty if the form does not exist.
   */
  public Optional<LtftAdminSummaryDto> updateTpdNotificationStatus(UUID formId, String status) {
    log.info("Updating TPD notification status for LTFT form {}: {}", formId, status);

    Optional<LtftForm> optionalForm = ltftFormRepository.findById(formId);
    EmailValidityType updatedEmailValidity = mapper.toEmailValidity(status);

    if (optionalForm.isPresent()) {
      LtftForm form = optionalForm.get();
      if (form.getContent().tpdEmailValidity() != null
          && form.getContent().tpdEmailValidity() == updatedEmailValidity) {
        log.info("Skipping update of TPD notification status for form {} as it is already {}.",
            formId, updatedEmailValidity);
        return Optional.of(mapper.toAdminSummaryDto(form));
      }
      if (form.getContent().tpdEmailValidity() != null
          && form.getContent().tpdEmailValidity() == VALID) {
        log.warn("Cannot update TPD notification status for form {} as it is already VALID.",
            formId);
        return Optional.empty();
      }
      LtftContent newContent = form.getContent().withTpdEmailValidity(updatedEmailValidity);
      form.setContent(newContent);
      LtftForm savedForm = ltftFormRepository.save(form);
      publishUpdateNotification(savedForm, FORM_ATTRIBUTE_TPD_STATUS,
          ltftStatusUpdateTopic);
      return Optional.of(mapper.toAdminSummaryDto(savedForm));
    } else {
      log.warn("Could not update TPD notification status: form {} cannot be found.", formId);
      return Optional.empty();
    }
  }

  /**
   * Update the status of an LTFT form as an admin, the form must be associated with the admin's
   * local office.
   *
   * @param formId The ID of the form to update the status of.
   * @param state  The new state.
   * @param detail A detailed reason for the change, may be null.
   * @return The updated LTFT application, empty if the form did not exist or did not belong to the
   *     admin's local office.
   * @throws MethodArgumentNotValidException If the state transition is not allowed.
   */
  public Optional<LtftFormDto> updateStatusAsAdmin(UUID formId, LifecycleState state,
      LftfStatusInfoDetailDto detail) throws MethodArgumentNotValidException {
    log.info("Updating LTFT form {} as admin [{}]: New state = {}", formId,
        adminIdentity.getEmail(), state);

    Set<String> dbcs = adminIdentity.getGroups();
    Optional<LtftForm> form =
        ltftFormRepository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(formId,
            dbcs);

    if (form.isPresent()) {
      LtftForm updatedForm = updateStatus(form.get(), state, adminIdentity, detail);
      return Optional.of(mapper.toDto(updatedForm));
    } else {
      log.warn("Could not update form {} since no form exists with this ID for DBCs [{}]",
          formId, dbcs);
      return Optional.empty();
    }
  }

  /**
   * Update the status of the LTFT, the current status and history will both be updated.
   *
   * @param form        The form to update the status of.
   * @param targetState The state to change to.
   * @param identity    Who is performing the status change.
   * @param detail      A detailed reason for the change, may be null.
   * @return The updated LTFT application.
   * @throws MethodArgumentNotValidException If the state transition is not allowed.
   */
  private LtftForm updateStatus(LtftForm form, LifecycleState targetState,
      UserIdentity identity, @Nullable LftfStatusInfoDetailDto detail)
      throws MethodArgumentNotValidException {

    if (!LifecycleState.canTransitionTo(form, targetState)) {
      log.warn(
          "Could not update form {}, invalid lifecycle transition from {} to {} for form type '{}'",
          form.getId(), form.getStatus().current().state(), targetState, form.getFormType());

      BeanPropertyBindingResult result = new BeanPropertyBindingResult(form, "form");
      result.addError(new FieldError("LtftForm", FORM_ATTRIBUTE_FORM_STATUS,
          "can not be transitioned to %s".formatted(targetState)));

      try {
        MethodParameter parameter = new MethodParameter(this.getClass()
            .getDeclaredMethod("updateStatus", LtftForm.class, LifecycleState.class,
                UserIdentity.class, LftfStatusInfoDetailDto.class), 1);
        throw new MethodArgumentNotValidException(parameter, result);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("Unabled to reflect updateStatus method.", e);
      }
    }

    if (targetState.isRequiresDetails() && (detail == null || detail.reason() == null)) {
      log.warn("Form {} requires a reason to change to state [{}]", form.getId(), targetState);

      BeanPropertyBindingResult result = new BeanPropertyBindingResult(detail, "detail");
      String field = detail == null ? "detail" : "detail.reason";
      result.addError(new FieldError("StatusInfo", field,
          "must not be null when transitioning to %s".formatted(targetState)));

      try {
        MethodParameter parameter = new MethodParameter(this.getClass()
            .getDeclaredMethod("updateStatus", LtftForm.class, LifecycleState.class,
                UserIdentity.class, LftfStatusInfoDetailDto.class), 3);
        throw new MethodArgumentNotValidException(parameter, result);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("Unabled to reflect updateStatus method.", e);
      }
    }

    if (targetState.isIncrementsRevision()) {
      form.setRevision(form.getRevision() + 1);
    }

    StatusDetail detailEntity = mapper.toStatusDetail(detail);

    Person modifiedBy = Person.builder()
        .name(identity.getName())
        .email(identity.getEmail())
        .role(identity.getRole())
        .build();
    form.setLifecycleState(targetState, detailEntity, modifiedBy, form.getRevision());

    // Generate a form reference when submitting for the first time.
    if (form.getFormRef() == null && targetState == SUBMITTED) {
      String traineeId = form.getTraineeTisId();
      int previousFormCount = ltftFormRepository
          .countByTraineeTisIdAndStatus_SubmittedIsNotNull(traineeId);
      String formRef = "ltft_%s_%03d".formatted(form.getTraineeTisId(), previousFormCount + 1);
      log.info("Assigning form reference {} to LTFT {}", formRef, form.getId());
      form.setFormRef(formRef);
    }

    LtftForm savedForm = ltftFormRepository.save(form);
    if (targetState == SUBMITTED) {
      ltftSubmissionHistoryService.takeSnapshot(savedForm);
    }

    publishUpdateNotification(savedForm, FORM_ATTRIBUTE_FORM_STATUS, ltftStatusUpdateTopic);

    return savedForm;
  }

  /**
   * Build a filtered query for admin users, which excludes DRAFT results and applies DBC filters.
   *
   * @param filterParams The user-supplied filters to apply, unsupported fields will be dropped.
   * @param pageable     The paging and sorting to apply to the query.
   * @return The build query.
   */
  private Query buildAdminFilteredQuery(Map<String, String> filterParams, Pageable pageable) {
    // Translate sort field(s).
    Sort sort = Sort.by(pageable.getSort().stream()
        .map(order -> {
          String property = switch (order.getProperty()) {
            case "formRef" -> order.getProperty();
            case "daysToStart", "proposedStartDate" -> "content.change.startDate";
            case "submissionDate" -> "status.submitted";
            default -> null;
          };

          return property == null ? null : order.withProperty(property);
        })
        .filter(Objects::nonNull)
        .toList());

    Query query;

    if (pageable.isUnpaged()) {
      query = new Query().with(sort);
    } else {
      // Add ID sort to ensure consistent paged ordering when the sort field has duplicated values.
      sort = sort.and(Sort.by("id"));
      pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
      query = new Query().with(pageable);
    }

    // Restrict results to the user's DBCs.
    query.addCriteria(
        Criteria.where("content.programmeMembership.designatedBodyCode")
            .in(adminIdentity.getGroups()));

    // Remove DRAFT applications from the result using the submitted timestamp.
    query.addCriteria(Criteria.where("status.submitted").ne(null));

    // Translate user-supplied filter fields and add them to the query
    filterParams.entrySet().stream()
        .filter(e -> e.getValue() != null && !e.getValue().isBlank())
        .map(e -> {
          String property = switch (e.getKey()) {
            case "formRef" -> e.getKey();
            case "assignedAdmin.name" -> "status.current.assignedAdmin.name";
            case "assignedAdmin.email" -> "status.current.assignedAdmin.email";
            case "personalDetails.forenames" -> "content.personalDetails.forenames";
            case "personalDetails.gdcNumber" -> "content.personalDetails.gdcNumber";
            case "personalDetails.gmcNumber" -> "content.personalDetails.gmcNumber";
            case "personalDetails.surname" -> "content.personalDetails.surname";
            case "programmeName" -> "content.programmeMembership.name";
            case "status" -> FORM_ATTRIBUTE_FORM_STATUS;
            case "traineeId" -> "traineeTisId";
            default -> null;
          };
          return property == null ? null : new SimpleEntry<>(property, e.getValue());
        })
        .filter(Objects::nonNull)
        .forEach(e -> {
          String key = e.getKey();
          String value = e.getValue();

          if (value.contains(",")) {
            String[] values = value.split(",");
            query.addCriteria(Criteria.where(key).in((Object[]) values));
          } else {
            query.addCriteria(Criteria.where(key).is(value));
          }
        });
    return query;
  }

  /**
   * Publish LTFT update notification.
   *
   * @param form             The updated LTFT form.
   * @param messageAttribute The message attribute to include in the notification.
   * @param snsTopic         The SNS topic to publish the notification to.
   */
  public void publishUpdateNotification(LtftForm form, String messageAttribute, String snsTopic) {
    log.info("Published update notification for LTFT form {} to SNS topic {}",
        form.getId(), snsTopic);
    LtftFormDto dto = mapper.toDto(form);
    eventBroadcastService.publishLtftFormUpdateEvent(dto, messageAttribute, snsTopic);
  }

  /**
   * Move all LTFT forms from one trainee to another. Assumes that fromTraineeId and toTraineeId are
   * valid. The updated LTFTs are broadcast as events. Also moves LTFT submission history.
   *
   * @param fromTraineeId The trainee ID to move LTFTs from.
   * @param toTraineeId   The trainee ID to move LTFTs to.
   * @return A map of the number of LTFT forms and submission histories moved.
   */
  public Map<String, Integer> moveLtftForms(String fromTraineeId, String toTraineeId) {
    if (fromTraineeId == null || toTraineeId == null || fromTraineeId.equals(toTraineeId)) {
      log.warn("Not moving LTFT forms, fromTraineeId or toTraineeId is null or unchanged: {} -> {}",
          fromTraineeId, toTraineeId);
      return Map.of(
          "ltft", 0,
          "ltft-submission", 0
      );
    }
    List<LtftForm> ltfts = ltftFormRepository
        .findByTraineeTisIdOrderByLastModified(fromTraineeId);

    AtomicReference<Integer> movedForms = new AtomicReference<>(0);
    ltfts.forEach(form -> {
      log.debug("Moving LTFT form [{}] from trainee [{}] to trainee [{}]",
          form.getId(), fromTraineeId, toTraineeId);
      // note no form content changes, just the trainee ID
      form.setTraineeTisId(toTraineeId);
      form = ltftFormRepository.save(form);
      // note: ltftAssignmentUpdateTopic is used here to publish an update to NDW (as the form
      // content has not changed). Don't use ltftStatusUpdateTopic, which would generate emails
      // to TPD and trainee.
      publishUpdateNotification(form, null, ltftAssignmentUpdateTopic);
      movedForms.getAndSet(movedForms.get() + 1);
    });

    Integer movedHistory
        = ltftSubmissionHistoryService.moveLtftSubmissions(fromTraineeId, toTraineeId);
    log.info("Moved {} LTFT forms and {} submission histories from trainee [{}] to trainee [{}]",
        movedForms.get(), movedHistory, fromTraineeId, toTraineeId);
    return Map.of(
        "ltft", movedForms.get(),
        "ltft-submission", movedHistory
    );
  }

  /**
   * Identifies if a programme membership is LTFT-enabled.
   *
   * @param programmeMembershipId The programme membership ID.
   * @return True if the programme membership is LTFT-enabled, otherwise false.
   */
  protected boolean isProgrammeMembershipValidForLtft(UUID programmeMembershipId) {
    if (traineeIdentity.getFeatures() == null) {
      log.info("Trainee {} does not have features set.", traineeIdentity.getTraineeId());
      return false;
    }

    LtftFeatures ltftFeatures = traineeIdentity.getFeatures().forms().ltft();

    if (!ltftFeatures.enabled()) {
      log.info("Trainee {} is not LTFT-enabled.", traineeIdentity.getTraineeId());
      return false;
    }
    if (programmeMembershipId == null
        || ltftFeatures.qualifyingProgrammes() == null
        || !ltftFeatures.qualifyingProgrammes().contains(programmeMembershipId.toString())) {
      log.info("Trainee {} programme membership {} is null or not LTFT-enabled.",
          traineeIdentity.getTraineeId(), programmeMembershipId);
      return false;
    }
    return true;
  }
}
