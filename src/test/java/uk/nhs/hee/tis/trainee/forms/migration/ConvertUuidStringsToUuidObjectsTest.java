/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.verification.Times;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

class ConvertUuidStringsToUuidObjectsTest {

  private static final String ID_FIELD = "_id";
  private static final UUID ID_UUID_1 = UUID.randomUUID();
  private static final String ID_STRING_1 = ID_UUID_1.toString();
  private static final UUID ID_UUID_2 = UUID.randomUUID();
  private static final String ID_STRING_2 = ID_UUID_2.toString();
  private static final String PART_A_COLLECTION_NAME = "part-a-collection";
  private static final String PART_B_COLLECTION_NAME = "part-b-collection";

  private ConvertUuidStringsToUuidObjects migration;
  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    migration = new ConvertUuidStringsToUuidObjects(template);

    when(template.getCollectionName(FormRPartA.class)).thenReturn(PART_A_COLLECTION_NAME);
    when(template.getCollectionName(FormRPartB.class)).thenReturn(PART_B_COLLECTION_NAME);
  }

  @Test
  void shouldNotFailWhenNoDocumentsToMigrate() {
    when(template.findAll(eq(Document.class), any())).thenReturn(List.of());

    assertDoesNotThrow(() -> migration.migrateCollections());
  }

  @Test
  void shouldOnlyIncludeUuidStringsInMigration() {
    Document document1 = new Document();
    document1.put(ID_FIELD, ID_STRING_1);

    Document document2 = new Document();
    document2.put(ID_FIELD, ID_UUID_2);

    when(template.findAll(eq(Document.class), any())).thenReturn(List.of(document1, document2),
        List.of());
    when(template.remove(any(), any(String.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    verify(template, new Times(1)).insert(any(Document.class), eq(PART_A_COLLECTION_NAME));
    verify(template, new Times(1)).remove(any(), eq(PART_A_COLLECTION_NAME));
  }

  @Test
  void shouldSaveFormRPartAsWithNewUuid() {
    Document document1 = new Document();
    document1.put(ID_FIELD, ID_STRING_1);

    Document document2 = new Document();
    document2.put(ID_FIELD, ID_STRING_2);

    when(template.findAll(eq(Document.class), eq(PART_A_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));
    when(template.remove(any(), eq(PART_A_COLLECTION_NAME))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
    verify(template, new Times(2)).insert(documentCaptor.capture(), eq(PART_A_COLLECTION_NAME));

    List<Document> documents = documentCaptor.getAllValues();
    assertThat("Unexpected document save count.", documents.size(), is(2));
    assertThat("Unexpected document ID.", documents.get(0).get(ID_FIELD), is(ID_UUID_1));
    assertThat("Unexpected document ID.", documents.get(1).get(ID_FIELD), is(ID_UUID_2));
  }

  @Test
  void shouldSaveFormRPartBsWithNewUuid() {
    Document document1 = new Document();
    document1.put(ID_FIELD, ID_STRING_1);

    Document document2 = new Document();
    document2.put(ID_FIELD, ID_STRING_2);

    when(template.findAll(eq(Document.class), eq(PART_B_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));
    when(template.remove(any(), eq(PART_B_COLLECTION_NAME))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
    verify(template, new Times(2)).insert(documentCaptor.capture(), eq(PART_B_COLLECTION_NAME));

    List<Document> documents = documentCaptor.getAllValues();
    assertThat("Unexpected document save count.", documents.size(), is(2));
    assertThat("Unexpected document ID.", documents.get(0).get(ID_FIELD), is(ID_UUID_1));
    assertThat("Unexpected document ID.", documents.get(1).get(ID_FIELD), is(ID_UUID_2));
  }

  @Test
  void shouldDeleteOriginalFormRPartAsFromDatabase() {
    Document document1 = new Document();
    document1.put(ID_FIELD, ID_STRING_1);

    Document document2 = new Document();
    document2.put(ID_FIELD, ID_STRING_2);

    when(template.findAll(eq(Document.class), eq(PART_A_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), any(String.class))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected query count.", queries.size(), is(2));

    Document queryObject = queries.get(0).getQueryObject();
    String id = queryObject.getString("_id");
    assertThat("Unexpected ID requirement.", id, is(ID_STRING_1));

    queryObject = queries.get(1).getQueryObject();
    id = queryObject.getString("_id");
    assertThat("Unexpected ID requirement.", id, is(ID_STRING_2));
  }

  @Test
  void shouldDeleteOriginalFormRPartBsFromDatabase() {
    Document document1 = new Document();
    document1.put(ID_FIELD, ID_STRING_1);

    Document document2 = new Document();
    document2.put(ID_FIELD, ID_STRING_2);

    when(template.findAll(eq(Document.class), eq(PART_B_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), any(String.class))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected query count.", queries.size(), is(2));

    Document queryObject = queries.get(0).getQueryObject();
    String id = queryObject.getString("_id");
    assertThat("Unexpected ID requirement.", id, is(ID_STRING_1));

    queryObject = queries.get(1).getQueryObject();
    id = queryObject.getString("_id");
    assertThat("Unexpected ID requirement.", id, is(ID_STRING_2));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void shouldNotFailMigrationWhenDeleteFromDatabaseFails(int deletedCount) {
    Document document1 = new Document();
    document1.put(ID_FIELD, ID_STRING_1);

    Document document2 = new Document();
    document2.put(ID_FIELD, ID_STRING_2);

    when(template.findAll(eq(Document.class), any())).thenReturn(List.of(document1),
        List.of(document2));
    when(template.remove(any(), any(String.class))).thenReturn(
        DeleteResult.acknowledged(deletedCount));

    assertDoesNotThrow(() -> migration.migrateCollections());
    verify(template, new Times(2)).remove(any(), any(String.class));
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
