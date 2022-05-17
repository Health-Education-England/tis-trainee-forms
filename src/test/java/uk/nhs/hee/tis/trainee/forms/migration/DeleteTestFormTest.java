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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

class DeleteTestFormTest {

  private DeleteTestForm migration;

  private MongoTemplate template;

  private ArgumentCaptor<Query> queryCaptor;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    queryCaptor = ArgumentCaptor.forClass(Query.class);
    migration = new DeleteTestForm(template);
  }

  @Test
  void shouldInvokeTemplate() {
    FormRPartB form = mock(FormRPartB.class);
    when(template.findAndRemove(queryCaptor.capture(), eq(FormRPartB.class), eq("FormRPartB")))
        .thenReturn(form);

    migration.migrate();

    verify(form).getId();
    verify(form).getTraineeTisId();
    verifyExpectedQuery();
  }

  @Test
  void shouldNotFailForNoResult() {
    migration.migrate();

    verify(template).findAndRemove(queryCaptor.capture(), eq(FormRPartB.class), eq("FormRPartB"));
    verifyExpectedQuery();
  }

  private void verifyExpectedQuery() {
    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unenxpected query \"_id\"",
        queryObject.getString("_id").equals("f874c846-623d-478c-8937-7595afbc969a"));

  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
