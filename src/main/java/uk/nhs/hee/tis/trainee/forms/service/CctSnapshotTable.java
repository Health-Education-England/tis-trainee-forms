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
import java.util.stream.Stream;

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
  public static CctSnapshotTable of(Map<String, Object> calculation) {
    Map<?, ?> result = asMap(calculation.get("result"));

    List<Row> rows = new ArrayList<>();
    asList(calculation.get("changes")).forEach(change -> rows.add(changeRow(asMap(change))));
    asList(result.get("assumedFullTimePeriods"))
        .forEach(period -> rows.add(assumedPeriodRow(asMap(period))));
    rows.sort(Comparator.comparing(Row::startDate,
        Comparator.nullsLast(Comparator.naturalOrder())));

    Totals totals = new Totals(
        sum(rows.stream().map(Row::calendarMonths)),
        sum(rows.stream().map(Row::wteMonths)));

    Map<?, ?> projection = asMap(result.get("projection"));
    Totals completionTotals = new Totals(
        sum(Stream.of(totals.calendarMonths(), asDouble(projection.get("calendarMonths")))),
        sum(Stream.of(totals.wteMonths(), asDouble(projection.get("wteMonths")))));

    boolean openLtftChange = rows.stream().anyMatch(Row::open);

    return new CctSnapshotTable(rows, totals, completionTotals, openLtftChange);
  }

  private static Row changeRow(Map<?, ?> change) {
    String type = asString(change.get("type"));
    LocalDate endDate = asDate(change.get("endDate"));

    return new Row(
        false,
        type == null ? null : TYPE_LABELS.getOrDefault(type, type),
        Boolean.TRUE.equals(change.get("countsTowardsCompletion")),
        "LTFT".equals(type) && endDate == null,
        asDate(change.get("startDate")),
        endDate,
        asDouble(change.get("wte")),
        asDouble(change.get("calendarMonths")),
        asDouble(change.get("wteMonths")),
        asString(change.get("notes")));
  }

  private static Row assumedPeriodRow(Map<?, ?> period) {
    Double months = asDouble(period.get("months"));

    return new Row(
        true,
        null,
        true,
        false,
        asDate(period.get("startDate")),
        asDate(period.get("endDate")),
        1.0,
        months,
        months,
        null);
  }

  private static double sum(Stream<Double> months) {
    return months.filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
  }

  private static Map<?, ?> asMap(Object value) {
    return value instanceof Map<?, ?> map ? map : Map.of();
  }

  private static List<?> asList(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }

  private static LocalDate asDate(Object value) {
    return value instanceof String date ? LocalDate.parse(date) : null;
  }

  private static Double asDouble(Object value) {
    return value instanceof Number number ? number.doubleValue() : null;
  }

  private static String asString(Object value) {
    return value instanceof String string ? string : null;
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
