/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

class FormRPartBMapperTest {

  private FormRPartBMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new FormRPartBMapperImpl();
  }

  @Test
  void shouldReturnNullWhenEntityNull() {
    FormRPartSimpleDto dto = mapper.toSimpleDto(null);
    assertThat("Expected null DTO when entity is null.", dto, nullValue());
  }

  @Test
  void shouldMapFieldsCorrectly() {
    FormRPartB entity = new FormRPartB();
    UUID id = UUID.randomUUID();
    entity.setId(id);
    entity.setTraineeTisId("12345");
    LocalDate startDate = LocalDate.of(2022, 1, 15);
    entity.setProgrammeSpecialty("General Practice");
    entity.setIsArcp(true);
    entity.setLifecycleState(SUBMITTED);
    UUID programmeMembershipId = UUID.randomUUID();
    entity.setProgrammeMembershipId(programmeMembershipId);
    LocalDateTime submissionDateTime = LocalDateTime.of(2023, 6, 30, 10, 45);
    entity.setSubmissionDate(submissionDateTime);

    FormRPartSimpleDto dto = mapper.toSimpleDto(entity);

    assertThat("Unexpected id.", dto.getId(), is(id.toString()));
    assertThat("Unexpected traineeTisId.", dto.getTraineeTisId(), is("12345"));
    assertThat("Unexpected programmeStartDate.", dto.getProgrammeStartDate(), nullValue());
    assertThat("Unexpected programmeName.", dto.getProgrammeName(), is("General Practice"));
    assertThat("Unexpected isArcp.", dto.getIsArcp(), is(true));
    assertThat("Unexpected submissionDate.", dto.getSubmissionDate(), is(submissionDateTime));
    assertThat("Unexpected lifecycleState.", dto.getLifecycleState(), is(SUBMITTED));
    assertThat("Unexpected programmeMembershipId.", dto.getProgrammeMembershipId(),
        is(programmeMembershipId));
  }

  @Test
  void shouldHandleCustomMappedNullFieldsGracefully() {
    FormRPartB entity = new FormRPartB();
    // All fields left null

    FormRPartSimpleDto dto = mapper.toSimpleDto(entity);

    assertThat("Expected null programmeStartDate.", dto.getProgrammeStartDate(), nullValue());
    assertThat("Expected null programmeName.", dto.getProgrammeName(), nullValue());
  }
}
