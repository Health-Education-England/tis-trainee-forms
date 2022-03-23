/*
 * The MIT License (MIT)
 *
 * Copyright 2022 Crown Copyright (Health Education England)
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

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

class RecalculateTotalLeaveTest {

  private RecalculateTotalLeave migration;

  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    migration = new RecalculateTotalLeave(template);
  }

  @Test
  void shouldOnlyRecalculateDocumentsWithZeroTotalLeave() {
    migration.migrate();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(template).updateMulti(queryCaptor.capture(), any(), eq(FormRPartB.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    int queryFilter = queryObject.getInteger("totalLeave");
    assertThat("Unexpected query filter.", queryFilter, is(0));
  }

  @Test
  void shouldAddAllLeaveFields() {
    migration.migrate();

    ArgumentCaptor<UpdateDefinition> updateCaptor = ArgumentCaptor.forClass(UpdateDefinition.class);
    verify(template).updateMulti(any(), updateCaptor.capture(), eq(FormRPartB.class));

    UpdateDefinition updateDefinition = updateCaptor.getValue();
    Document root = updateDefinition.getUpdateObject();
    List<Document> updates = root.getList("", Document.class);
    assertThat("Unexpected number of updates.", updates.size(), is(1));

    Document update = updates.get(0);
    List<String> addedFields = update.getEmbedded(
        List.of("$set", "totalLeave", "$add"), List.class);
    assertThat("Unexpected field count.", addedFields.size(), is(6));
    assertThat("Unexpected fields.", addedFields, hasItems(
        "$sicknessAbsence",
        "$parentalLeave",
        "$careerBreaks",
        "$paidLeave",
        "$unauthorisedLeave",
        "$otherLeave"
    ));
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
