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

package uk.nhs.hee.tis.trainee.forms.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.mongodb.client.result.UpdateResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.migration.MigrateLtftStartDates.StartDates;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;

class MigrateLtftStartDatesTest {

  private static final String FORM_COLLECTION = "LtftForm";
  private static final String HISTORY_COLLECTION = "LtftSubmissionHistory";

  private static final String CHANGE_START_DATE = "content.change.startDate";
  private static final String ALT_START_DATE = "content.change.altStartDate";
  private static final String EXCEPTIONAL_START_DATE = "content.exceptionalReasons.startDate";
  private static final String EXCEPTIONAL = "content.exceptionalReasons.exceptional";

  private static final LocalDate SUBMITTED_DATE = LocalDate.of(2026, 1, 1);
  private static final LocalDate CUT_OFF = LocalDate.of(2026, 4, 23);
  private static final LocalDate LATE = LocalDate.of(2026, 3, 1);
  private static final LocalDate COMPLIANT = LocalDate.of(2026, 7, 1);
  private static final LocalDate EXISTING_EXCEPTIONAL = LocalDate.of(2026, 2, 1);

  private MigrateLtftStartDates migration;
  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    when(template.getCollectionName(LtftForm.class)).thenReturn(FORM_COLLECTION);
    when(template.getCollectionName(LtftSubmissionHistory.class)).thenReturn(HISTORY_COLLECTION);
    when(template.find(any(Query.class), eq(Document.class), anyString())).thenReturn(List.of());
    when(template.updateFirst(any(Query.class), any(UpdateDefinition.class), anyString()))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    migration = new MigrateLtftStartDates(template);
  }

  /**
   * Provide the rule scenarios from the ticket, as state, start date, alternative start date,
   * existing exceptional start date, submitted date, expected compliant date and expected
   * exceptional date.
   *
   * @return The rule scenario arguments.
   */
  static Stream<Arguments> ruleScenarios() {
    return Stream.of(
        Arguments.of(APPROVED, LATE, COMPLIANT, null, SUBMITTED_DATE, LATE, LATE),
        Arguments.of(APPROVED, null, null, null, SUBMITTED_DATE, null, null),
        Arguments.of(SUBMITTED, LATE, COMPLIANT, null, SUBMITTED_DATE, COMPLIANT, LATE),
        Arguments.of(SUBMITTED, COMPLIANT, LATE, null, SUBMITTED_DATE, COMPLIANT, LATE),
        Arguments.of(SUBMITTED, CUT_OFF, null, null, SUBMITTED_DATE, CUT_OFF, null),
        Arguments.of(SUBMITTED, CUT_OFF.minusDays(1), null, null, SUBMITTED_DATE, CUT_OFF,
            CUT_OFF.minusDays(1)),
        Arguments.of(SUBMITTED, LATE, COMPLIANT, EXISTING_EXCEPTIONAL, SUBMITTED_DATE, COMPLIANT,
            EXISTING_EXCEPTIONAL),
        Arguments.of(DRAFT, null, null, null, null, null, null),
        Arguments.of(DRAFT, LATE, null, null, null, null, LATE),
        Arguments.of(DRAFT, LATE, COMPLIANT, null, null, COMPLIANT, LATE)
    );
  }

  @ParameterizedTest
  @MethodSource("ruleScenarios")
  void shouldResolveStartDatesUsingFirstMatchingRule(LifecycleState state, LocalDate startDate,
      LocalDate altStartDate, LocalDate exceptionalStartDate, LocalDate submittedDate,
      LocalDate expectedCompliant, LocalDate expectedExceptional) {
    StartDates startDates = MigrateLtftStartDates.resolve(state, startDate, altStartDate,
        exceptionalStartDate, submittedDate);

    assertThat("Unexpected compliant start date.", startDates.compliantStartDate(),
        is(expectedCompliant));
    assertThat("Unexpected exceptional start date.", startDates.exceptionalStartDate(),
        is(expectedExceptional));
    assertThat("Unexpected exceptional flag.", startDates.isExceptional(),
        is(expectedExceptional != null));
  }

  @Test
  void shouldConvertSubmittedTimestampToLondonDate() {
    Date submitted = Date.from(Instant.parse("2026-06-30T23:30:00Z"));

    LocalDate submittedDate = MigrateLtftStartDates.toSubmittedDate(submitted);

    assertThat("Unexpected submitted date.", submittedDate, is(LocalDate.of(2026, 7, 1)));
  }

  @ParameterizedTest
  @ValueSource(strings = {FORM_COLLECTION, HISTORY_COLLECTION})
  void shouldSetResolvedDatesAndUnsetAltStartDate(String collectionName) {
    Document document = buildDocument(SUBMITTED, LATE, COMPLIANT, SUBMITTED_DATE);
    when(template.find(any(Query.class), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(document));

    migration.migrate();

    Document updateObject = captureUpdate(collectionName).getUpdateObject();
    Document set = updateObject.get("$set", Document.class);
    assertThat("Unexpected compliant start date.", set.get(CHANGE_START_DATE), is(COMPLIANT));
    assertThat("Unexpected exceptional start date.", set.get(EXCEPTIONAL_START_DATE), is(LATE));
    assertThat("Unexpected exceptional flag.", set.get(EXCEPTIONAL), is(true));

    Document unset = updateObject.get("$unset", Document.class);
    assertThat("Unexpected unset fields.", unset.keySet(), containsInAnyOrder(ALT_START_DATE));
  }

  @Test
  void shouldUnsetDatesAndNotSetExceptionalForDraftWithoutDates() {
    Document document = buildDocument(DRAFT, null, null, null);
    when(template.find(any(Query.class), eq(Document.class), eq(FORM_COLLECTION)))
        .thenReturn(List.of(document));

    migration.migrate();

    Document updateObject = captureUpdate(FORM_COLLECTION).getUpdateObject();
    assertThat("Unexpected set fields.", updateObject, not(hasKey("$set")));

    Document unset = updateObject.get("$unset", Document.class);
    assertThat("Unexpected unset fields.", unset.keySet(),
        containsInAnyOrder(CHANGE_START_DATE, EXCEPTIONAL_START_DATE, ALT_START_DATE));
  }

  @Test
  void shouldThrowWithDocumentIdWhenNoCompliantStartDateCanBeResolved() {
    Document document = buildDocument(SUBMITTED, LATE, null, null);
    when(template.find(any(Query.class), eq(Document.class), eq(FORM_COLLECTION)))
        .thenReturn(List.of(document));

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> migration.migrate());

    assertThat("Unexpected message.", exception.getMessage(),
        containsString(document.get("_id").toString()));
    verify(template, never()).updateFirst(any(Query.class), any(UpdateDefinition.class),
        anyString());
  }

  /**
   * Build a raw LTFT document, as stored in the database.
   *
   * @param state         The current lifecycle state.
   * @param startDate     The change start date, may be null.
   * @param altStartDate  The alternative start date, may be null.
   * @param submittedDate The submitted date, may be null.
   * @return The built document.
   */
  private static Document buildDocument(LifecycleState state, LocalDate startDate,
      LocalDate altStartDate, LocalDate submittedDate) {
    Document change = new Document();
    change.put("startDate", toStoredDate(startDate));
    change.put("altStartDate", toStoredDate(altStartDate));

    Document status = new Document("current", new Document("state", state.name()));
    if (submittedDate != null) {
      status.put("submitted",
          Date.from(submittedDate.atStartOfDay(MigrateLtftStartDates.TIMEZONE).toInstant()));
    }

    return new Document("_id", UUID.randomUUID())
        .append("content", new Document("change", change))
        .append("status", status);
  }

  /**
   * Convert a local date to a stored date, matching how a {@link LocalDate} field is written.
   *
   * @param localDate The local date to convert, may be null.
   * @return The stored date, or null.
   */
  private static Date toStoredDate(LocalDate localDate) {
    return localDate == null ? null
        : Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  /**
   * Capture the update made to the given collection.
   *
   * @param collectionName The collection name.
   * @return The captured update.
   */
  private Update captureUpdate(String collectionName) {
    ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
    verify(template).updateFirst(any(Query.class), captor.capture(), eq(collectionName));
    return captor.getValue();
  }
}
