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

package uk.nhs.hee.tis.trainee.forms.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctAssumedFullTimePeriodDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctCalculatedChangeDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctCalculationDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctResultDto;

class CctSnapshotRowTest {

  @Test
  void shouldOrderChangesAndAssumedPeriodsByStartDate() {
    CctCalculationDto calculation = CctCalculationDto.builder()
        .changes(List.of(
            CctCalculatedChangeDto.builder()
                .type("OOPE")
                .startDate(LocalDate.parse("2025-03-01"))
                .build(),
            CctCalculatedChangeDto.builder()
                .type("LTFT")
                .startDate(LocalDate.parse("2024-02-01"))
                .build()))
        .result(CctResultDto.builder()
            .assumedFullTimePeriods(List.of(
                CctAssumedFullTimePeriodDto.builder()
                    .startDate(LocalDate.parse("2022-08-03"))
                    .build(),
                CctAssumedFullTimePeriodDto.builder()
                    .startDate(LocalDate.parse("2025-02-01"))
                    .build()))
            .build())
        .build();

    List<CctSnapshotRow> rows = CctSnapshotRow.of(calculation);

    List<LocalDate> startDates = rows.stream().map(CctSnapshotRow::startDate).toList();
    assertThat("Unexpected row order.", startDates, contains(
        LocalDate.parse("2022-08-03"),
        LocalDate.parse("2024-02-01"),
        LocalDate.parse("2025-02-01"),
        LocalDate.parse("2025-03-01")));

    List<Boolean> assumed = rows.stream().map(CctSnapshotRow::assumed).toList();
    assertThat("Unexpected assumed rows.", assumed, contains(true, false, true, false));
  }

  @Test
  void shouldMapChangeToRow() {
    CctCalculationDto calculation = CctCalculationDto.builder()
        .changes(List.of(CctCalculatedChangeDto.builder()
            .type("OOPE")
            .startDate(LocalDate.parse("2024-02-01"))
            .endDate(LocalDate.parse("2025-01-31"))
            .wte(0.6)
            .countsTowardsCompletion(true)
            .calendarMonths(12.0)
            .wteMonths(7.5)
            .notes("Research post")
            .build()))
        .build();

    List<CctSnapshotRow> rows = CctSnapshotRow.of(calculation);

    CctSnapshotRow row = rows.get(0);
    assertThat("Unexpected assumed flag.", row.assumed(), is(false));
    assertThat("Unexpected type.", row.type(), is("OOPE"));
    assertThat("Unexpected counts towards completion.", row.countsTowardsCompletion(), is(true));
    assertThat("Unexpected start date.", row.startDate(), is(LocalDate.parse("2024-02-01")));
    assertThat("Unexpected end date.", row.endDate(), is(LocalDate.parse("2025-01-31")));
    assertThat("Unexpected WTE.", row.wte(), is(0.6));
    assertThat("Unexpected calendar months.", row.calendarMonths(), is(12.0));
    assertThat("Unexpected WTE months.", row.wteMonths(), is(7.5));
    assertThat("Unexpected notes.", row.notes(), is("Research post"));
  }

  @Test
  void shouldMapChangeWithMissingValuesToRow() {
    CctCalculationDto calculation = CctCalculationDto.builder()
        .changes(List.of(CctCalculatedChangeDto.builder()
            .type("PARENTAL")
            .startDate(LocalDate.parse("2025-03-01"))
            .build()))
        .build();

    List<CctSnapshotRow> rows = CctSnapshotRow.of(calculation);

    CctSnapshotRow row = rows.get(0);
    assertThat("Unexpected counts towards completion.", row.countsTowardsCompletion(), is(false));
    assertThat("Unexpected end date.", row.endDate(), nullValue());
    assertThat("Unexpected WTE.", row.wte(), nullValue());
    assertThat("Unexpected calendar months.", row.calendarMonths(), nullValue());
    assertThat("Unexpected WTE months.", row.wteMonths(), nullValue());
    assertThat("Unexpected notes.", row.notes(), nullValue());
  }

  @Test
  void shouldMapAssumedPeriodToFullTimeRow() {
    CctCalculationDto calculation = CctCalculationDto.builder()
        .result(CctResultDto.builder()
            .assumedFullTimePeriods(List.of(CctAssumedFullTimePeriodDto.builder()
                .startDate(LocalDate.parse("2022-08-03"))
                .endDate(LocalDate.parse("2024-01-31"))
                .months(18.0)
                .build()))
            .build())
        .build();

    List<CctSnapshotRow> rows = CctSnapshotRow.of(calculation);

    CctSnapshotRow row = rows.get(0);
    assertThat("Unexpected assumed flag.", row.assumed(), is(true));
    assertThat("Unexpected type.", row.type(), nullValue());
    assertThat("Unexpected counts towards completion.", row.countsTowardsCompletion(), is(true));
    assertThat("Unexpected start date.", row.startDate(), is(LocalDate.parse("2022-08-03")));
    assertThat("Unexpected end date.", row.endDate(), is(LocalDate.parse("2024-01-31")));
    assertThat("Unexpected WTE.", row.wte(), is(1.0));
    assertThat("Unexpected calendar months.", row.calendarMonths(), is(18.0));
    assertThat("Unexpected WTE months.", row.wteMonths(), is(18.0));
    assertThat("Unexpected notes.", row.notes(), nullValue());
  }

  @Test
  void shouldBuildNoRowsWhenNoChangesOrResult() {
    List<CctSnapshotRow> rows = CctSnapshotRow.of(CctCalculationDto.builder().build());

    assertThat("Unexpected rows.", rows, empty());
  }
}
