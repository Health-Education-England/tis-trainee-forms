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

import java.util.List;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.MapMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

@Mapper(componentModel = "spring", uses = {CovidDeclarationMapper.class})
public interface FormRPartBMapper {

  FormRPartBDto toDto(FormRPartB formRPartB);

  @InheritInverseConfiguration
  FormRPartB toEntity(FormRPartBDto formRPartBDto);

  List<FormRPartBDto> toDtos(List<FormRPartB> formRPartBs);

  List<FormRPartB> toEntities(List<FormRPartBDto> formRPartBDtos);

  FormRPartSimpleDto toSimpleDto(FormRPartB formRPartB);

  List<FormRPartSimpleDto> toSimpleDtos(List<FormRPartB> formRPartBs);
}
