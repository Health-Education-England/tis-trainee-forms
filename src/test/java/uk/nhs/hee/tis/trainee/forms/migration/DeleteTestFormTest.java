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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest.Builder;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

class DeleteTestFormTest {

  private static final String BUCKET_NAME = "test-bucket";
  private DeleteTestForm migration;
  private MongoTemplate template;
  private ArgumentCaptor<Query> queryCaptor;
  private S3Client s3;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    s3 = mock(S3Client.class);
    queryCaptor = ArgumentCaptor.forClass(Query.class);
    Environment env = mock(Environment.class);
    when(env.getProperty("application.file-store.bucket")).thenReturn(BUCKET_NAME);
    migration = new DeleteTestForm(template, s3, env);
  }

  @Test
  void shouldInvokeTemplate() {
    FormRPartB form = mock(FormRPartB.class);
    when(template.findAndRemove(queryCaptor.capture(), eq(FormRPartB.class), eq("FormRPartB")))
        .thenReturn(form);

    migration.migrate();

    verify(form).getId();
    verify(form).getTraineeTisId();
    verifyExpectedOperations();
  }

  @Test
  void shouldNotFailForNoResult() {
    migration.migrate();

    verify(template).findAndRemove(queryCaptor.capture(), eq(FormRPartB.class), eq("FormRPartB"));
    verifyExpectedOperations();
  }

  private void verifyExpectedOperations() {
    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected query \"_id\"",
        queryObject.getString("_id").equals("f874c846-623d-478c-8937-7595afbc969a"));

    ArgumentCaptor<Consumer<Builder>> consumerCaptor = ArgumentCaptor.captor();
    verify(s3).deleteObject(consumerCaptor.capture());

    DeleteObjectRequest request1 = DeleteObjectRequest.builder()
        .applyMutation(consumerCaptor.getValue())
        .build();
    assertThat("Unexpected bucket.", request1.bucket(), is(BUCKET_NAME));
    assertThat("Unexpected key.", request1.key(),
        is("256060/forms/formr-b/f874c846-623d-478c-8937-7595afbc969a.json"));
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
