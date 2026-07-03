/*
 * The MIT License (MIT)
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

package uk.nhs.hee.tis.trainee.forms.mapper;

import static org.mapstruct.InjectionStrategy.CONSTRUCTOR;
import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

/**
 * Mappers between FormR Part B representations.
 */
@Mapper(componentModel = SPRING, uses = {CovidDeclarationMapper.class,
    TemporalMapper.class}, injectionStrategy = CONSTRUCTOR)
public interface FormRPartBMapper {

  @Mapping(target = "id", source = "id")
  @Mapping(target = "traineeTisId", source = "traineeTisId")
  @Mapping(target = "content", source = "content")
  @Mapping(target = "content.programmeName", source = "content.programmeSpecialty")
  @Mapping(target = "lifecycleState", source = "status.current.state")
  @Mapping(target = "submissionDate", source = "status.submitted")
  @Mapping(target = "lastModifiedDate", source = "lastModified")
  FormRPartBDto toDto(FormRPartB formRPartB);

  @Mapping(target = "id", source = "existingEntity.id")
  @Mapping(target = "traineeTisId", source = "dto.traineeTisId")
  @Mapping(target = "formRef", source = "existingEntity.formRef")
  @Mapping(target = "revision", source = "existingEntity.revision")
  @Mapping(target = "content", source = "dto.content")
  @Mapping(target = "status", source = "existingEntity.status")
  @Mapping(target = "lifecycleState", ignore = true)
  FormRPartB toEntity(FormRPartBDto dto, FormRPartB existingEntity);

  //programmeStartDate is unmapped as FormRPartB does not have a startDate field
  @Mapping(target = "id", source = "id")
  @Mapping(target = "traineeTisId", source = "traineeTisId")
  @Mapping(target = "isArcp", source = "content.isArcp")
  @Mapping(target = "programmeMembershipId", source = "content.programmeMembershipId")
  @Mapping(target = "submissionDate", source = "status.submitted")
  @Mapping(target = "lifecycleState", source = "status.current.state")
  @Mapping(target = "programmeName", source = "content.programmeSpecialty")
  @Mapping(target = "formType", constant = "formr-partb")
  FormRPartSimpleDto toSimpleDto(FormRPartB formRPartB);

  List<FormRPartSimpleDto> toSimpleDtos(List<FormRPartB> formRPartBs);
}
