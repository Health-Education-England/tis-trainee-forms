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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.data.mongodb.core.query.UpdateDefinition.ArrayFilter;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftSubmissionHistory;

class PopulateLtftResponsibleRoleTest {

  private static final String CURRENT_ROLE = "status.current.responsibleRole";
  private static final String HISTORY_ROLE = "status.history.$[entry].responsibleRole";

  private PopulateLtftResponsibleRole migration;
  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    when(template.updateMulti(any(Query.class), any(Update.class), any(Class.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    migration = new PopulateLtftResponsibleRole(template);
  }

  /**
   * Provide every combination of entity type and responsible role.
   *
   * @return The entity type and role arguments.
   */
  static Stream<Arguments> entityTypesAndRoles() {
    return Stream.of(LtftForm.class, LtftSubmissionHistory.class)
        .flatMap(type -> Arrays.stream(ResponsibleRole.values())
            .map(role -> Arguments.of(type, role)));
  }

  @Test
  void shouldPopulateFixedRoles() {
    assertThat("Unexpected roles.", PopulateLtftResponsibleRole.ROLES,
        containsInAnyOrder(ResponsibleRole.values()));
  }

  @Test
  void shouldPopulateFormsAndSubmissionHistory() {
    assertThat("Unexpected entity types.", PopulateLtftResponsibleRole.ENTITY_TYPES,
        containsInAnyOrder(LtftForm.class, LtftSubmissionHistory.class));
  }

  @ParameterizedTest
  @ValueSource(classes = {LtftForm.class, LtftSubmissionHistory.class})
  void shouldPerformCurrentAndHistoryUpdateForEachRole(Class<?> entityType) {
    migration.migrate();

    verify(template, times(6)).updateMulti(any(Query.class), any(Update.class),
        eq(entityType));
  }

  @ParameterizedTest
  @MethodSource("entityTypesAndRoles")
  void shouldUpdateCurrentStatusWithoutRole(Class<?> entityType, ResponsibleRole role) {
    migration.migrate();

    Invocation invocation = findInvocation(entityType, CURRENT_ROLE, role);

    assertThat("Unexpected query.", invocation.query().getQueryObject(),
        is(expectedEntryCriteria(role, "status.current.")));
    assertThat("Unexpected array filters.", invocation.update().getArrayFilters(), hasSize(0));
  }

  @ParameterizedTest
  @MethodSource("entityTypesAndRoles")
  void shouldUpdateHistoryEntriesWithoutRole(Class<?> entityType, ResponsibleRole role) {
    migration.migrate();

    Invocation invocation = findInvocation(entityType, HISTORY_ROLE, role);

    Document queryObject = invocation.query().getQueryObject();
    assertThat("Unexpected query keys.", queryObject.keySet(), is(Set.of("status.history")));
    assertThat("Unexpected element match.",
        queryObject.get("status.history", Document.class).get("$elemMatch", Document.class),
        is(expectedEntryCriteria(role, "")));

    List<ArrayFilter> arrayFilters = invocation.update().getArrayFilters();
    assertThat("Unexpected array filter count.", arrayFilters, hasSize(1));
    assertThat("Unexpected array filter.", arrayFilters.get(0).asDocument(),
        is(expectedEntryCriteria(role, "entry.")));
  }

  @Test
  void shouldNotInteractWithMongoOnRollback() {
    migration.rollback();

    verifyNoInteractions(template);
  }

  /**
   * Build the expected criteria for a status entry to be populated with the given role.
   *
   * @param role   The responsible role.
   * @param prefix The status entry field prefix.
   * @return The expected criteria document.
   */
  private static Document expectedEntryCriteria(ResponsibleRole role, String prefix) {
    Document criteria = new Document(prefix + "responsibleRole", new Document("$exists", false));
    Document reviewableStates = new Document("$in", List.of("SUBMITTED", "UNDER_REVIEW"));

    return switch (role) {
      case TRAINEE -> criteria.append(prefix + "state", "UNSUBMITTED");
      case REVIEWER -> criteria.append(prefix + "state", reviewableStates)
          .append(prefix + "assignedReviewer", new Document("$ne", null))
          .append(prefix + "reviewOutcome", null);
      case ADMIN -> criteria.append(prefix + "state", reviewableStates)
          .append("$or", List.of(
              new Document(prefix + "assignedReviewer", null),
              new Document(prefix + "reviewOutcome", new Document("$ne", null))));
    };
  }

  /**
   * Find the captured update invocation which sets the given field to the given role.
   *
   * @param entityType The entity type the update was applied to.
   * @param field      The field being set.
   * @param role       The role being set.
   * @return The matching invocation.
   */
  private Invocation findInvocation(Class<?> entityType, String field, ResponsibleRole role) {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();
    verify(template, times(6)).updateMulti(queryCaptor.capture(), updateCaptor.capture(),
        eq(entityType));

    List<Query> queries = queryCaptor.getAllValues();
    List<Update> updates = updateCaptor.getAllValues();

    for (int i = 0; i < updates.size(); i++) {
      Document set = updates.get(i).getUpdateObject().get("$set", Document.class);

      if (set.equals(new Document(Map.of(field, role.name())))) {
        return new Invocation(queries.get(i), updates.get(i));
      }
    }

    throw new AssertionError("No update found setting %s to %s.".formatted(field, role));
  }

  /**
   * A captured update invocation.
   *
   * @param query  The update query.
   * @param update The update definition.
   */
  private record Invocation(Query query, Update update) {

  }
}