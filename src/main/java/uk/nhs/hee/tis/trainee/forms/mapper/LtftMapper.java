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

package uk.nhs.hee.tis.trainee.forms.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;
import static org.mapstruct.MappingConstants.ComponentModel.SPRING;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.INVALID;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.UNKNOWN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.VALID;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto.LtftAdminPersonalDetailsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonalDetailsDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusDetail;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;

/**
 * A mapper to convert between LTFT related entities and DTOs.
 */
@Getter
@Mapper(componentModel = SPRING, uses = TemporalMapper.class, injectionStrategy = CONSTRUCTOR)
public abstract class LtftMapper {

  private static final int NOTICE_PERIOD_DAYS = 112; // 16 weeks.

  @Setter(onMethod_ = @Autowired)
  TemporalMapper temporalMapper;

  /**
   * Convert a {@link LtftForm} entity to a {@link LtftAdminSummaryDto} DTO.
   *
   * @param entity The entity to convert to a DTO.
   * @return The equivalent admin summary DTO.
   */
  @Mapping(target = "personalDetails", source = "entity")
  @Mapping(target = "programmeName", source = "content.programmeMembership.name")
  @Mapping(target = "proposedStartDate", source = "content.change.startDate")
  @Mapping(target = "submissionDate", source = "status.submitted")
  @Mapping(target = "reason", source = "content.reasons.selected",
      qualifiedByName = "JoinWithComma")
  @Mapping(target = "daysToStart", source = "content.change.startDate",
      qualifiedByName = "DaysUntil")
  @Mapping(target = "shortNotice", source = "entity", qualifiedByName = "IsShortNoticeAdmin")
  @Mapping(target = "tpd.email", source = "content.discussions.tpdEmail")
  @Mapping(target = "tpd.emailStatus", source = "content.tpdEmailValidity")
  @Mapping(target = "status", source = "status.current.state")
  @Mapping(target = "assignedAdmin.name", source = "status.current.assignedAdmin.name")
  @Mapping(target = "assignedAdmin.email", source = "status.current.assignedAdmin.email")
  @Mapping(target = "assignedAdmin.role", ignore = true)
  public abstract LtftAdminSummaryDto toAdminSummaryDto(LtftForm entity);

  /**
   * Build a {@link LtftAdminPersonalDetailsDto} from an {@link LtftForm}.
   *
   * @param entity The entity to build personal details from.
   * @return The built personal details.
   */
  @Mapping(target = "id", source = "traineeTisId")
  @Mapping(target = ".", source = "content.personalDetails")
  abstract LtftAdminPersonalDetailsDto buildAdminPersonalDetails(LtftForm entity);

  /**
   * Build a {@link PersonalDetailsDto} from an {@link LtftForm}.
   *
   * @param entity The entity to build personal details from.
   * @return The built personal details.
   */
  @Mapping(target = "id", source = "traineeTisId")
  @Mapping(target = ".", source = "content.personalDetails")
  abstract PersonalDetailsDto buildPersonalDetails(LtftForm entity);

  /**
   * Convert a {@link LtftForm} entity to a {@link LtftSummaryDto} DTO.
   *
   * @param entity The entity to convert to a DTO.
   * @return The equivalent summary DTO.
   */
  @Mapping(target = "name", source = "content.name")
  @Mapping(target = "programmeMembershipId", source = "content.programmeMembership.id")
  @Mapping(target = "status", source = "status.current.state")
  @Mapping(target = "statusReason", source = "status.current.detail.reason")
  @Mapping(target = "statusMessage", source = "status.current.detail.message")
  @Mapping(target = "modifiedByRole", source = "status.current.modifiedBy.role")
  public abstract LtftSummaryDto toSummaryDto(LtftForm entity);

  /**
   * Convert a list of {@link LtftForm} to {@link LtftSummaryDto} DTOs.
   *
   * @param entities The entities to convert to DTOs.
   * @return The equivalent summary DTOs.
   */
  public abstract List<LtftSummaryDto> toSummaryDtos(List<LtftForm> entities);

  /**
   * Convert a {@link LtftForm} to {@link LtftFormDto} DTO.
   *
   * @param entity The form to convert.
   * @return The equivalent DTO.
   */
  @Mapping(target = "id", source = "id")
  @Mapping(target = "formRef", source = "formRef")
  @Mapping(target = "name", source = "content.name")
  @Mapping(target = "personalDetails", source = ".")
  @Mapping(target = "programmeMembership", source = "content.programmeMembership")
  @Mapping(target = "declarations", source = "content.declarations")
  @Mapping(target = "discussions", source = "content.discussions")
  @Mapping(target = "change", source = "content.change")
  @Mapping(target = "reasons", source = "content.reasons")
  @Mapping(target = "tpdEmailStatus", source = "content.tpdEmailValidity")
  @Mapping(target = "shortNotice", source = "entity", qualifiedByName = "IsShortNoticeTrainee")
  public abstract LtftFormDto toDto(LtftForm entity);

  /**
   * Convert a {@link LtftForm} to a {@link LtftSubmissionHistory} entity.
   *
   * @param entity The form to convert.
   * @return The equivalent submission history.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "created", ignore = true)
  @Mapping(target = "lastModified", ignore = true)
  public abstract LtftSubmissionHistory toSubmissionHistory(LtftForm entity);

  /**
   * Convert a {@link LtftFormDto} DTO to a {@link LtftForm}.
   *
   * @param dto The DTO to convert.
   * @return The equivalent LTFT Form.
   */
  @InheritInverseConfiguration
  @Mapping(target = "content", source = "dto")
  public abstract LtftForm toEntity(LtftFormDto dto);

  /**
   * Convert a {@link PersonDto} DTO to a {@link Person}.
   *
   * @param dto The DTO to convert.
   * @return The equivalent Person entity.
   */
  public abstract Person toEntity(PersonDto dto);

  /**
   * Convert a {@link LftfStatusInfoDetailDto} to a {@link StatusDetail}.
   *
   * @param dto The DTO to convert.
   * @return The equivalent status detail.
   */
  public abstract StatusDetail toStatusDetail(LftfStatusInfoDetailDto dto);

  /**
   * Convert a {@link StatusDetail} to a {@link LftfStatusInfoDetailDto}.
   *
   * @param detail The status detail to convert.
   * @return The equivalent status detail DTO, or an empty DTO if input is null.
   */
  public LftfStatusInfoDetailDto toStatusDetailDto(StatusDetail detail) {
    if (detail == null) {
      return LftfStatusInfoDetailDto.builder().build();
    }
    return LftfStatusInfoDetailDto.builder()
        .reason(detail.reason())
        .message(detail.message())
        .build();
  }

  /**
   * Joins a list of strings with a comma, sorted alphabetically for consistency.
   *
   * @param list The list of strings to join.
   * @return The joined string.
   */
  @Named("JoinWithComma")
  String joinListWithComma(List<String> list) {
    if (list == null) {
      return null;
    }

    List<String> sorted = list.stream().sorted().toList();
    return String.join(", ", sorted);
  }

  /**
   * Calculate whether the LTFT application is short notice for TIS Admins.
   *
   * @param entity The LTFT application.
   * @return Whether the application is short notice, or null if one of the required dates was null.
   */
  @Named("IsShortNoticeAdmin")
  @Nullable
  Boolean isShortNoticeAdmin(LtftForm entity) {
    if (entity.getStatus() == null || entity.getStatus().submitted() == null) {
      return null;
    }

    Instant submitted = entity.getStatus().submitted();

    // If the form is unsubmitted, use the current date instead of the last submission date.
    Instant referenceInstant =
        entity.getStatus().current().state() == UNSUBMITTED ? Instant.now() : submitted;
    return isShortNotice(entity, referenceInstant);
  }

  /**
   * Calculate whether the LTFT application is short notice TSS Trainee (for all states).
   *
   * @param entity The LTFT application.
   * @return Whether the application is short notice, or null if application status was null.
   */
  @Named("IsShortNoticeTrainee")
  @Nullable
  Boolean isShortNoticeTrainee(LtftForm entity) {
    if (entity.getStatus() == null) {
      return null;
    }

    Instant submitted = entity.getStatus().submitted();
    LifecycleState state = entity.getStatus().current().state();

    // If the form is UNSUBMITTED/DRAFT, use the current date instead of the last submission date.
    Instant referenceInstant =
        (state == UNSUBMITTED || state == DRAFT || submitted == null) ? Instant.now() : submitted;
    return isShortNotice(entity, referenceInstant);
  }

  /**
   * Convert an email status string to an {@link EmailValidityType}.
   *
   * @param emailStatus The email status string to convert.
   * @return The corresponding {@link EmailValidityType}, or null if the input is null.
   */
  public EmailValidityType toEmailValidity(@Nullable String emailStatus) {
    if (emailStatus == null) {
      return null;
    }
    return switch (emailStatus) {
      case "SENT" -> VALID;
      case "PENDING" -> UNKNOWN;
      default -> INVALID;
    };
  }

  /**
   * Calculate whether the LTFT application is short notice.
   *
   * @param entity The LTFT application.
   * @param referenceInstant The reference date for calculating short notice .
   * @return Whether the application is short notice.
   */
  private Boolean isShortNotice(LtftForm entity, Instant referenceInstant) {
    if (referenceInstant == null) {
      return false;
    }
    LocalDate referenceDate = getTemporalMapper().toLocalDate(referenceInstant);

    return Optional.ofNullable(entity.getContent())
        .map(LtftContent::change)
        .map(CctChange::startDate)
        .map(startDate -> ChronoUnit.DAYS.between(referenceDate, startDate) < NOTICE_PERIOD_DAYS)
        .orElse(null);
  }
}
