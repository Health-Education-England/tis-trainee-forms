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
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.WITHDRAWN;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsTemplate;
import jakarta.annotation.Nullable;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
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

  private final AdminIdentity adminIdentity;
  private final TraineeIdentity traineeIdentity;

  private final LtftFormRepository ltftFormRepository;
  private final MongoTemplate mongoTemplate;

  private final LtftMapper mapper;

  private final SnsTemplate snsTemplate;
  private final String ltftStatusUpdateTopic;

  /**
   * Instantiate the LTFT form service.
   *
   * @param adminIdentity      The logged-in admin, for admin features.
   * @param traineeIdentity    The logged-in trainee, for trainee features.
   * @param ltftFormRepository The LTFT repository.
   * @param mongoTemplate      The Mongo template.
   * @param mapper             The LTFT mapper.
   */
  public LtftService(AdminIdentity adminIdentity, TraineeIdentity traineeIdentity,
      LtftFormRepository ltftFormRepository, MongoTemplate mongoTemplate, LtftMapper mapper,
      SnsTemplate snsTemplate,
      @Value("${application.aws.sns.ltft-status-updated}") String ltftStatusUpdateTopic) {
    this.adminIdentity = adminIdentity;
    this.traineeIdentity = traineeIdentity;
    this.ltftFormRepository = ltftFormRepository;
    this.mongoTemplate = mongoTemplate;
    this.mapper = mapper;
    this.snsTemplate = snsTemplate;
    this.ltftStatusUpdateTopic = ltftStatusUpdateTopic;
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
    Set<String> groups = adminIdentity.getGroups();
    log.info("Getting LTFT form {} for admin {} with DBCs [{}]", formId, adminIdentity.getEmail(),
        groups);
    Optional<LtftForm> form = ltftFormRepository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            formId, Set.of(DRAFT), adminIdentity.getGroups());
    return form.map(mapper::toDto);
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

    // Merge the new content in to the existing form.
    Person assignedAdmin =
        existingForm.getContent() != null ? existingForm.getContent().assignedAdmin() : null;
    LtftContent updatedContent = form.getContent().withAssignedAdmin(assignedAdmin);
    existingForm.setContent(updatedContent);

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
      result.addError(new FieldError("LtftForm", "status.current.state",
          "can not be transitioned to %s".formatted(targetState)));
      throw new MethodArgumentNotValidException(null, result);
    }

    if (targetState.isRequiresDetails() && (detail == null || detail.reason() == null)) {
      log.warn("Form {} requires a reason to change to state [{}]", form.getId(), targetState);

      BeanPropertyBindingResult result = new BeanPropertyBindingResult(detail, "detail");
      String field = detail == null ? "detail" : "detail.reason";
      result.addError(new FieldError("StatusInfo", field,
          "must not be null when transitioning to %s".formatted(targetState)));

      throw new MethodArgumentNotValidException(null, result);
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
    publishStatusUpdateNotification(savedForm);

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
            case "assignedAdmin.name" -> "content.assignedAdmin.name";
            case "assignedAdmin.email" -> "content.assignedAdmin.email";
            case "personalDetails.forenames" -> "content.personalDetails.forenames";
            case "personalDetails.gdcNumber" -> "content.personalDetails.gdcNumber";
            case "personalDetails.gmcNumber" -> "content.personalDetails.gmcNumber";
            case "personalDetails.surname" -> "content.personalDetails.surname";
            case "programmeName" -> "content.programmeMembership.name";
            case "status" -> "status.current.state";
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
   * Publish LTFT status update notification.
   *
   * @param form The updated LTFT form
   */
  private void publishStatusUpdateNotification(LtftForm form) {
    log.info("Published status update notification for LTFT form {}", form.getId());
    String groupId = form.getId() == null ? UUID.randomUUID().toString() : form.getId().toString();
    SnsNotification<LtftForm> notification = SnsNotification.builder(form)
        .groupId(groupId)
        .build();

    snsTemplate.sendNotification(ltftStatusUpdateTopic, notification);
  }
}
