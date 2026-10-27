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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition.ArrayFilter;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

class PopulateLtftResponsibleRoleTest {

  private static final String CURRENT_ROLE = "status.current.responsibleRole";
  private static final String HISTORY_ROLE = "status.history.$[entry].responsibleRole";

  private PopulateLtftResponsibleRole migration;
  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    when(template.updateMulti(any(Query.class), any(Update.class), eq(LtftForm.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    migration = new PopulateLtftResponsibleRole(template);
  }

  @Test
  void shouldUseFixedStateToRoleRule() {
    assertThat("Unexpected roles.", PopulateLtftResponsibleRole.ROLE_STATES.keySet(),
        is(Set.of(ResponsibleRole.ADMIN, ResponsibleRole.TRAINEE)));
    assertThat("Unexpected admin states.",
        PopulateLtftResponsibleRole.ROLE_STATES.get(ResponsibleRole.ADMIN),
        containsInAnyOrder("SUBMITTED", "UNDER_REVIEW"));
    assertThat("Unexpected trainee states.",
        PopulateLtftResponsibleRole.ROLE_STATES.get(ResponsibleRole.TRAINEE),
        containsInAnyOrder("UNSUBMITTED"));
  }

  @Test
  void shouldPerformCurrentAndHistoryUpdateForEachRole() {
    migration.migrate();

    verify(template, times(4)).updateMulti(any(Query.class), any(Update.class),
        eq(LtftForm.class));
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
      ADMIN, 'SUBMITTED,UNDER_REVIEW'
      TRAINEE, UNSUBMITTED
      """)
  void shouldUpdateCurrentStatusWithoutRole(ResponsibleRole role, String states) {
    migration.migrate();

    Invocation invocation = findInvocation(CURRENT_ROLE, role);

    Document queryObject = invocation.query().getQueryObject();
    assertThat("Unexpected query keys.", queryObject.keySet(),
        is(Set.of("status.current.state", CURRENT_ROLE)));
    assertThat("Unexpected state filter.",
        queryObject.get("status.current.state", Document.class).getList("$in", String.class),
        containsInAnyOrder(states.split(",")));
    assertThat("Unexpected role filter.",
        queryObject.get(CURRENT_ROLE, Document.class).get("$exists"), is(false));

    assertThat("Unexpected array filters.", invocation.update().getArrayFilters(), hasSize(0));
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
      ADMIN, 'SUBMITTED,UNDER_REVIEW'
      TRAINEE, UNSUBMITTED
      """)
  void shouldUpdateHistoryEntriesWithoutRole(ResponsibleRole role, String states) {
    migration.migrate();

    Invocation invocation = findInvocation(HISTORY_ROLE, role);

    Document queryObject = invocation.query().getQueryObject();
    assertThat("Unexpected query keys.", queryObject.keySet(), is(Set.of("status.history")));
    Document elemMatch = queryObject.get("status.history", Document.class)
        .get("$elemMatch", Document.class);
    assertThat("Unexpected element state filter.",
        elemMatch.get("state", Document.class).getList("$in", String.class),
        containsInAnyOrder(states.split(",")));
    assertThat("Unexpected element role filter.",
        elemMatch.get("responsibleRole", Document.class).get("$exists"), is(false));

    List<ArrayFilter> arrayFilters = invocation.update().getArrayFilters();
    assertThat("Unexpected array filter count.", arrayFilters, hasSize(1));
    Document arrayFilter = arrayFilters.get(0).asDocument();
    assertThat("Unexpected array filter state.",
        arrayFilter.get("entry.state", Document.class).getList("$in", String.class),
        containsInAnyOrder(states.split(",")));
    assertThat("Unexpected array filter role.",
        arrayFilter.get("entry.responsibleRole", Document.class).get("$exists"), is(false));
  }

  @Test
  void shouldNotInteractWithMongoOnRollback() {
    migration.rollback();

    verifyNoInteractions(template);
  }

  /**
   * Find the captured update invocation which sets the given field to the given role.
   *
   * @param field The field being set.
   * @param role  The role being set.
   * @return The matching invocation.
   */
  private Invocation findInvocation(String field, ResponsibleRole role) {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.captor();
    verify(template, times(4)).updateMulti(queryCaptor.capture(), updateCaptor.capture(),
        eq(LtftForm.class));

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

