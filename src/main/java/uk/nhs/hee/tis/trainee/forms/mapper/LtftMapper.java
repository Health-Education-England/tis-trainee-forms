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
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonalDetailsDto;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;

/**
 * A mapper to convert between LTFT related entities and DTOs.
 */
@Mapper(componentModel = SPRING, uses = TemporalMapper.class, injectionStrategy = CONSTRUCTOR)
public abstract class LtftMapper {

  private static final int NOTICE_PERIOD_DAYS = 112; // 16 weeks.

  @Getter
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
  @Mapping(target = "submissionDate", source = "submitted")
  @Mapping(target = "reason", source = "content.reasons.selected",
      qualifiedByName = "JoinWithComma")
  @Mapping(target = "daysToStart", source = "content.change.startDate",
      qualifiedByName = "DaysUntil")
  @Mapping(target = "shortNotice", source = "entity", qualifiedByName = "IsShortNotice")
  @Mapping(target = "tpd.email", source = "content.discussions.tpdEmail")
  @Mapping(target = "tpd.emailStatus", constant = "UNKNOWN") // TODO: not yet available (TIS21-7022)
  @Mapping(target = "status", source = "status.current.state")
  @Mapping(target = "assignedAdmin.name", source = "content.assignedAdmin.name")
  @Mapping(target = "assignedAdmin.email", source = "content.assignedAdmin.email")
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
  @Mapping(target = "assignedAdmin", source = "content.assignedAdmin")
  public abstract LtftFormDto toDto(LtftForm entity);

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
   * Convert a {@link LtftFormDto.StatusDto.LftfStatusInfoDetailDto} to a
   * {@link AbstractAuditedForm.Status.StatusDetail}.
   *
   * @param dto The DTO to convert.
   * @return The equivalent status detail.
   */
  public abstract AbstractAuditedForm.Status.StatusDetail toStatusDetail(
      LtftFormDto.StatusDto.LftfStatusInfoDetailDto dto);

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
   * Calculate whether the LTFT application is short notice.
   *
   * @param entity The LTFT application.
   * @return Whether the application is short notice, or null if one of the required dates was null.
   */
  @Named("IsShortNotice")
  @Nullable
  Boolean isShortNotice(LtftForm entity) {
    Instant submitted = entity.getSubmitted();

    if (submitted == null) {
      return null;
    }

    // If the form is unsubmitted, use the current date instead of the last submission date.
    Instant referenceInstant =
        entity.getStatus().current().state() == UNSUBMITTED ? Instant.now() : submitted;
    LocalDate referenceDate = getTemporalMapper().toLocalDate(referenceInstant);

    return Optional.ofNullable(entity.getContent())
        .map(LtftContent::change)
        .map(CctChange::startDate)
        .map(startDate -> ChronoUnit.DAYS.between(referenceDate, startDate) < NOTICE_PERIOD_DAYS)
        .orElse(null);
  }
}
