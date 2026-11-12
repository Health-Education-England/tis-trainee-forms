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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctAssumedFullTimePeriodDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctCalculatedChangeDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctCalculationDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctProjectionDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctSnapshotDto.CctResultDto;

/**
 * The changes table of an attached CCT calculation, as shown in the LTFT PDF.
 *
 * @param rows             The changes and assumed full-time periods, ordered by start date.
 * @param totals           The month totals of the rows.
 * @param completionTotals The month totals of the rows and the projection to completion.
 * @param openLtftChange   Whether any row is an LTFT change with no end date.
 */
public record CctSnapshotTable(
    List<Row> rows,
    Totals totals,
    Totals completionTotals,
    boolean openLtftChange) {

  private static final Map<String, String> TYPE_LABELS = Map.of(
      "PARENTAL", "Parental leave",
      "PHASED", "Phased return",
      "SHIELDING", "Shielding",
      "SICKNESS", "Sickness",
      "ACCRUED_LEAVE", "Accrued leave");

  /**
   * Build the changes table for a CCT calculation.
   *
   * @param calculation The CCT calculation.
   * @return The changes table.
   */
  public static CctSnapshotTable of(CctCalculationDto calculation) {
    Optional<CctResultDto> result = Optional.ofNullable(calculation.result());

    List<Row> rows = new ArrayList<>();
    Optional.ofNullable(calculation.changes()).orElse(List.of())
        .forEach(change -> rows.add(changeRow(change)));
    result.map(CctResultDto::assumedFullTimePeriods).orElse(List.of())
        .forEach(period -> rows.add(assumedPeriodRow(period)));
    rows.sort(Comparator.comparing(Row::startDate,
        Comparator.nullsLast(Comparator.naturalOrder())));

    Totals totals = new Totals(
        sum(rows.stream().map(Row::calendarMonths)),
        sum(rows.stream().map(Row::wteMonths)));

    Optional<CctProjectionDto> projection = result.map(CctResultDto::projection);
    Totals completionTotals = new Totals(
        sum(Stream.of(totals.calendarMonths(),
            projection.map(CctProjectionDto::calendarMonths).orElse(null))),
        sum(Stream.of(totals.wteMonths(),
            projection.map(CctProjectionDto::wteMonths).orElse(null))));

    boolean openLtftChange = rows.stream().anyMatch(Row::open);

    return new CctSnapshotTable(rows, totals, completionTotals, openLtftChange);
  }

  private static Row changeRow(CctCalculatedChangeDto change) {
    String type = change.type();

    return new Row(
        false,
        type == null ? null : TYPE_LABELS.getOrDefault(type, type),
        change.countsTowardsCompletion(),
        "LTFT".equals(type) && change.endDate() == null,
        change.startDate(),
        change.endDate(),
        change.wte(),
        change.calendarMonths(),
        change.wteMonths(),
        change.notes());
  }

  private static Row assumedPeriodRow(CctAssumedFullTimePeriodDto period) {
    return new Row(
        true,
        null,
        true,
        false,
        period.startDate(),
        period.endDate(),
        1.0,
        period.months(),
        period.months(),
        null);
  }

  private static double sum(Stream<Double> months) {
    return months.filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
  }

  /**
   * A row of the changes table, either a change or an assumed full-time period.
   *
   * @param assumed                 Whether the row is an assumed full-time period.
   * @param label                   The label of the change type, null for an assumed period.
   * @param countsTowardsCompletion Whether the row counts towards completion of training.
   * @param open                    Whether the row is an LTFT change with no end date.
   * @param startDate               The start date.
   * @param endDate                 The end date.
   * @param wte                     The whole time equivalent, as a fraction.
   * @param calendarMonths          The calendar months.
   * @param wteMonths               The whole time equivalent months.
   * @param notes                   The trainee's notes.
   */
  public record Row(
      boolean assumed,
      String label,
      boolean countsTowardsCompletion,
      boolean open,
      LocalDate startDate,
      LocalDate endDate,
      Double wte,
      Double calendarMonths,
      Double wteMonths,
      String notes) {

  }

  /**
   * Month totals of the changes table.
   *
   * @param calendarMonths The total calendar months.
   * @param wteMonths      The total whole time equivalent months.
   */
  public record Totals(double calendarMonths, double wteMonths) {

  }
}
