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

import com.amazonaws.services.s3.AmazonS3;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;

class DeleteTrainee160419FormTest {

  private static final String BUCKET_NAME = "test-bucket";
  private DeleteTrainee160419Form migration;
  private MongoTemplate template;
  private ArgumentCaptor<Query> queryCaptor;
  private AmazonS3 s3;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    s3 = mock(AmazonS3.class);
    queryCaptor = ArgumentCaptor.forClass(Query.class);
    Environment env = mock(Environment.class);
    when(env.getProperty("application.file-store.bucket")).thenReturn(BUCKET_NAME);
    migration = new DeleteTrainee160419Form(template, s3, env);
  }

  @Test
  void shouldInvokeTemplate() {
    FormRPartA form = mock(FormRPartA.class);
    when(template.findAndRemove(queryCaptor.capture(), eq(FormRPartA.class), eq("FormRPartA")))
        .thenReturn(form);

    migration.migrate();

    verify(form).getId();
    verify(form).getTraineeTisId();
    verifyExpectedOperations();
  }

  @Test
  void shouldNotFailForNoResult() {
    migration.migrate();

    verify(template).findAndRemove(queryCaptor.capture(), eq(FormRPartA.class), eq("FormRPartA"));
    verifyExpectedOperations();
  }

  private void verifyExpectedOperations() {
    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected query \"_id\"",
        queryObject.getString("_id").equals("73240025-eaa4-4d17-b6a3-322e67e047b3"));
    verify(s3).deleteObject(BUCKET_NAME,
        "160419/forms/formr-a/73240025-eaa4-4d17-b6a3-322e67e047b3.json");
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
