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

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctCalculationDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctResultDto;

/**
 * A row of the changes table of an attached CCT calculation, as shown in the LTFT PDF.
 *
 * @param assumed                 Whether the row is an assumed full-time period.
 * @param type                    The type of change, null for an assumed period.
 * @param countsTowardsCompletion Whether the row counts towards completion of training.
 * @param startDate               The start date.
 * @param endDate                 The end date.
 * @param wte                     The whole time equivalent, as a fraction.
 * @param calendarMonths          The calendar months.
 * @param wteMonths               The whole time equivalent months.
 * @param notes                   The trainee's notes.
 */
public record CctSnapshotRow(
    boolean assumed,
    String type,
    boolean countsTowardsCompletion,
    LocalDate startDate,
    LocalDate endDate,
    Double wte,
    Double calendarMonths,
    Double wteMonths,
    String notes) {

  /**
   * Build the rows of the changes table for a CCT calculation.
   *
   * @param calculation The CCT calculation.
   * @return The changes and assumed full-time periods, ordered by start date.
   */
  public static List<CctSnapshotRow> of(CctCalculationDto calculation) {
    Stream<CctSnapshotRow> changes = Optional.ofNullable(calculation.changes())
        .orElse(List.of()).stream()
        .map(change -> new CctSnapshotRow(false, change.type(), change.countsTowardsCompletion(),
            change.startDate(), change.endDate(), change.wte(), change.calendarMonths(),
            change.wteMonths(), change.notes()));

    Stream<CctSnapshotRow> assumedPeriods = Optional.ofNullable(calculation.result())
        .map(CctResultDto::assumedFullTimePeriods)
        .orElse(List.of()).stream()
        .map(period -> new CctSnapshotRow(true, null, true, period.startDate(), period.endDate(),
            1.0, period.months(), period.months(), null));

    return Stream.concat(changes, assumedPeriods)
        .sorted(Comparator.comparing(CctSnapshotRow::startDate,
            Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }
}
