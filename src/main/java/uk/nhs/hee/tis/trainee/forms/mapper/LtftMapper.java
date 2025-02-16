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

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import java.util.List;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

/**
 * A mapper to convert between LTFT related entities and DTOs.
 */
@Mapper(componentModel = SPRING)
public interface LtftMapper {

  /**
   * Convert a {@link LtftForm} entity to a {@link LtftSummaryDto} DTO.
   *
   * @param entity The entity to convert to a DTO.
   * @return The equivalent summary DTO.
   */
  @Mapping(target = "name", source = "content.name")
  @Mapping(target = "programmeMembershipId", source = "content.programmeMembership.id")
  @Mapping(target = "status", source = "status.current.state")
  LtftSummaryDto toSummaryDto(LtftForm entity);

  /**
   * Convert a list of {@link LtftForm} to {@link LtftSummaryDto} DTOs.
   *
   * @param entities The entities to convert to DTOs.
   * @return The equivalent summary DTOs.
   */
  List<LtftSummaryDto> toSummaryDtos(List<LtftForm> entities);

  /**
   * Convert a {@link LtftForm} to {@link LtftFormDto} DTO.
   *
   * @param entity The form to convert.
   * @return The equivalent DTO.
   */
  @Mapping(target = "name", source = "content.name")
  @Mapping(target = "discussions", source = "content.discussions")
  @Mapping(target = "programmeMembership", source = "content.programmeMembership")
  @Mapping(target = "status.current", source = "status.current.state")
  LtftFormDto toDto(LtftForm entity);

  /**
   * Convert a {@link LtftFormDto} DTO to a {@link LtftForm}.
   *
   * @param dto The DTO to convert.
   * @return The equivalent LTFT Form.
   */
  @InheritInverseConfiguration
  LtftForm toEntity(LtftFormDto dto);
}
