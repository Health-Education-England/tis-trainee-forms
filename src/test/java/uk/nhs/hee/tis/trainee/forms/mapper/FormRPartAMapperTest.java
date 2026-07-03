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

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.content.FormrPartaContent;

class FormRPartAMapperTest {

  private FormRPartAMapper mapper;

  @BeforeEach
  void setUp() {
    TemporalMapper temporalMapper = new TemporalMapper(ZoneId.of("Etc/UTC"));
    mapper = new FormRPartAMapperImpl(temporalMapper);
  }

  @Test
  void shouldReturnNullWhenEntityNull() {
    FormRPartSimpleDto dto = mapper.toSimpleDto(null);
    assertThat("Expected null DTO when entity is null.", dto, nullValue());
  }

  @Test
  void shouldMapFieldsCorrectly() {
    FormRPartA entity = new FormRPartA();
    UUID id = UUID.randomUUID();
    entity.setId(id);
    entity.setTraineeTisId("12345");

    LocalDate startDate = LocalDate.of(2022, Month.JANUARY, 15);
    UUID programmeMembershipId = UUID.randomUUID();
    entity.setContent(FormrPartaContent.builder()
        .startDate(startDate)
        .programmeSpecialty("General Practice")
        .isArcp(true)
        .programmeMembershipId(programmeMembershipId)
        .build());

    Instant submitted = Instant.parse("2023-06-30T10:45:00Z");
    entity.setStatus(Status.builder()
        .current(StatusInfo.builder().state(SUBMITTED).build())
        .submitted(submitted)
        .build());

    FormRPartSimpleDto dto = mapper.toSimpleDto(entity);

    assertThat("Unexpected id.", dto.getId(), is(id.toString()));
    assertThat("Unexpected traineeTisId.", dto.getTraineeTisId(), is("12345"));
    assertThat("Unexpected programmeStartDate.", dto.getProgrammeStartDate(), is(startDate));
    assertThat("Unexpected programmeName.", dto.getProgrammeName(), is("General Practice"));
    assertThat("Unexpected isArcp.", dto.getIsArcp(), is(true));
    assertThat("Unexpected submissionDate.", dto.getSubmissionDate(),
        is(LocalDateTime.ofInstant(submitted, UTC)));
    assertThat("Unexpected lifecycleState.", dto.getLifecycleState(), is(SUBMITTED));
    assertThat("Unexpected programmeMembershipId.", dto.getProgrammeMembershipId(),
        is(programmeMembershipId));
    assertThat("Unexpected formType.", dto.getFormType(), is("formr-parta"));
  }

  @Test
  void shouldHandleCustomMappedNullFieldsGracefully() {
    FormRPartA entity = new FormRPartA();
    // All fields left null

    FormRPartSimpleDto dto = mapper.toSimpleDto(entity);

    assertThat("Expected null programmeStartDate.", dto.getProgrammeStartDate(), nullValue());
    assertThat("Expected null programmeName.", dto.getProgrammeName(), nullValue());
    assertThat("Unexpected formType.", dto.getFormType(), is("formr-parta"));
  }

  @Test
  void shouldMapProgrammeNameFromProgrammeSpecialtyInToDto() {
    FormRPartA entity = new FormRPartA();
    entity.setContent(FormrPartaContent.builder()
        .programmeSpecialty("Internal Medicine")
        .build());

    var dto = mapper.toDto(entity);

    assertThat("Expected programmeName to be mapped from programmeSpecialty.",
        dto.getContent().getProgrammeName(), is("Internal Medicine"));
  }
}
