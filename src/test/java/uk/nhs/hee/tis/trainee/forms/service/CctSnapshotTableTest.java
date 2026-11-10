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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.nhs.hee.tis.trainee.forms.service.CctSnapshotTable.Row;

class CctSnapshotTableTest {

  @Test
  void shouldOrderChangesAndAssumedPeriodsByStartDate() {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(
            Map.of("type", "OOPE", "startDate", "2025-03-01"),
            Map.of("type", "LTFT", "startDate", "2024-02-01")),
        "result", Map.of(
            "assumedFullTimePeriods", List.of(
                Map.of("startDate", "2022-08-03"),
                Map.of("startDate", "2025-02-01"))));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    List<LocalDate> startDates = table.rows().stream().map(Row::startDate).toList();
    assertThat("Unexpected row order.", startDates, contains(
        LocalDate.parse("2022-08-03"),
        LocalDate.parse("2024-02-01"),
        LocalDate.parse("2025-02-01"),
        LocalDate.parse("2025-03-01")));

    List<Boolean> assumed = table.rows().stream().map(Row::assumed).toList();
    assertThat("Unexpected assumed rows.", assumed, contains(true, false, true, false));
  }

  @Test
  void shouldMapChangeToRow() {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(Map.of(
            "type", "OOPE",
            "startDate", "2024-02-01",
            "endDate", "2025-01-31",
            "wte", 0.6,
            "countsTowardsCompletion", true,
            "calendarMonths", 12,
            "wteMonths", 7.5,
            "notes", "Research post")));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    Row row = table.rows().get(0);
    assertThat("Unexpected assumed flag.", row.assumed(), is(false));
    assertThat("Unexpected label.", row.label(), is("OOPE"));
    assertThat("Unexpected counts towards completion.", row.countsTowardsCompletion(), is(true));
    assertThat("Unexpected open flag.", row.open(), is(false));
    assertThat("Unexpected start date.", row.startDate(), is(LocalDate.parse("2024-02-01")));
    assertThat("Unexpected end date.", row.endDate(), is(LocalDate.parse("2025-01-31")));
    assertThat("Unexpected WTE.", row.wte(), is(0.6));
    assertThat("Unexpected calendar months.", row.calendarMonths(), is(12.0));
    assertThat("Unexpected WTE months.", row.wteMonths(), is(7.5));
    assertThat("Unexpected notes.", row.notes(), is("Research post"));
  }

  @Test
  void shouldMapChangeWithMissingValuesToRow() {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(Map.of("type", "PARENTAL", "startDate", "2025-03-01")));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    Row row = table.rows().get(0);
    assertThat("Unexpected counts towards completion.", row.countsTowardsCompletion(), is(false));
    assertThat("Unexpected end date.", row.endDate(), nullValue());
    assertThat("Unexpected WTE.", row.wte(), nullValue());
    assertThat("Unexpected calendar months.", row.calendarMonths(), nullValue());
    assertThat("Unexpected WTE months.", row.wteMonths(), nullValue());
    assertThat("Unexpected notes.", row.notes(), nullValue());
  }

  @ParameterizedTest
  @CsvSource({
      "LTFT,LTFT",
      "OOPC,OOPC",
      "OOPE,OOPE",
      "OOPP,OOPP",
      "OOPR,OOPR",
      "OOPT,OOPT",
      "PARENTAL,Parental leave",
      "PHASED,Phased return",
      "SHIELDING,Shielding",
      "SICKNESS,Sickness",
      "ACCRUED_LEAVE,Accrued leave"
  })
  void shouldLabelChangeByType(String type, String label) {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(Map.of("type", type, "startDate", "2024-02-01")));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    assertThat("Unexpected label.", table.rows().get(0).label(), is(label));
  }

  @Test
  void shouldMarkLtftChangeWithNoEndDateAsOpen() {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(Map.of("type", "LTFT", "startDate", "2027-02-01")));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    assertThat("Unexpected open flag.", table.rows().get(0).open(), is(true));
    assertThat("Unexpected open LTFT change flag.", table.openLtftChange(), is(true));
  }

  @Test
  void shouldNotMarkLtftChangeWithEndDateAsOpen() {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(
            Map.of("type", "LTFT", "startDate", "2027-02-01", "endDate", "2028-01-31")));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    assertThat("Unexpected open flag.", table.rows().get(0).open(), is(false));
    assertThat("Unexpected open LTFT change flag.", table.openLtftChange(), is(false));
  }

  @Test
  void shouldNotMarkOtherChangeWithNoEndDateAsOpen() {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(Map.of("type", "OOPE", "startDate", "2027-02-01")));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    assertThat("Unexpected open flag.", table.rows().get(0).open(), is(false));
    assertThat("Unexpected open LTFT change flag.", table.openLtftChange(), is(false));
  }

  @Test
  void shouldMapAssumedPeriodToFullTimeRow() {
    Map<String, Object> calculation = Map.of(
        "result", Map.of(
            "assumedFullTimePeriods", List.of(Map.of(
                "startDate", "2022-08-03",
                "endDate", "2024-01-31",
                "months", 18))));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    Row row = table.rows().get(0);
    assertThat("Unexpected assumed flag.", row.assumed(), is(true));
    assertThat("Unexpected label.", row.label(), nullValue());
    assertThat("Unexpected counts towards completion.", row.countsTowardsCompletion(), is(true));
    assertThat("Unexpected open flag.", row.open(), is(false));
    assertThat("Unexpected start date.", row.startDate(), is(LocalDate.parse("2022-08-03")));
    assertThat("Unexpected end date.", row.endDate(), is(LocalDate.parse("2024-01-31")));
    assertThat("Unexpected WTE.", row.wte(), is(1.0));
    assertThat("Unexpected calendar months.", row.calendarMonths(), is(18.0));
    assertThat("Unexpected WTE months.", row.wteMonths(), is(18.0));
    assertThat("Unexpected notes.", row.notes(), nullValue());
  }

  @Test
  void shouldSumRowMonthsSkippingMissingValues() {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(
            Map.of("type", "LTFT", "startDate", "2024-02-01", "endDate", "2025-01-31",
                "calendarMonths", 12, "wteMonths", 7.5),
            Map.of("type", "LTFT", "startDate", "2027-02-01")),
        "result", Map.of(
            "assumedFullTimePeriods", List.of(Map.of("startDate", "2022-08-03", "months", 18))));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    assertThat("Unexpected total calendar months.", table.totals().calendarMonths(), is(30.0));
    assertThat("Unexpected total WTE months.", table.totals().wteMonths(), is(25.5));
  }

  @Test
  void shouldAddProjectionToCompletionTotals() {
    Map<String, Object> calculation = Map.of(
        "changes", List.of(
            Map.of("type", "LTFT", "startDate", "2024-02-01", "endDate", "2025-01-31",
                "calendarMonths", 12, "wteMonths", 7.5)),
        "result", Map.of(
            "projection", Map.of("calendarMonths", 40.25, "wteMonths", 24)));

    CctSnapshotTable table = CctSnapshotTable.of(calculation);

    assertThat("Unexpected completion calendar months.",
        table.completionTotals().calendarMonths(), is(52.25));
    assertThat("Unexpected completion WTE months.",
        table.completionTotals().wteMonths(), is(31.5));
  }

  @Test
  void shouldBuildEmptyTableWhenNoChangesOrResult() {
    CctSnapshotTable table = CctSnapshotTable.of(Map.of());

    assertThat("Unexpected rows.", table.rows(), empty());
    assertThat("Unexpected total calendar months.", table.totals().calendarMonths(), is(0.0));
    assertThat("Unexpected total WTE months.", table.totals().wteMonths(), is(0.0));
    assertThat("Unexpected completion calendar months.",
        table.completionTotals().calendarMonths(), is(0.0));
    assertThat("Unexpected completion WTE months.",
        table.completionTotals().wteMonths(), is(0.0));
    assertThat("Unexpected open LTFT change flag.", table.openLtftChange(), is(false));
  }
}
