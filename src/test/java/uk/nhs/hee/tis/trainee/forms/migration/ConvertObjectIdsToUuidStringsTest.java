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
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

class ConvertObjectIdsToUuidStringsTest {

  private static final String ID_FIELD = "_id";
  private static final String TRAINEE_ID_FIELD = "traineeTisId";
  private static final String PART_A_COLLECTION_NAME = "part-a-collection";
  private static final String PART_B_COLLECTION_NAME = "part-b-collection";
  private ConvertObjectIdsToUuidStrings migration;
  private MongoTemplate template;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    migration = new ConvertObjectIdsToUuidStrings(template);

    when(template.getCollectionName(FormRPartA.class)).thenReturn(PART_A_COLLECTION_NAME);
    when(template.getCollectionName(FormRPartB.class)).thenReturn(PART_B_COLLECTION_NAME);

    MappingMongoConverter converter = mock(MappingMongoConverter.class);
    when(converter.read(any(), any())).thenAnswer(args -> {
      Class<? extends AbstractForm> formClass = args.getArgument(0);
      Document document = args.getArgument(1);

      AbstractForm form = formClass.getConstructor().newInstance();
      form.setId(UUID.fromString(document.get(ID_FIELD).toString()));
      form.setTraineeTisId(document.getString(TRAINEE_ID_FIELD));
      return form;
    });
    when(template.getConverter()).thenReturn(converter);
  }

  @Test
  void shouldNotFailWhenNoDocumentsToMigrate() {
    when(template.find(any(), eq(Document.class), any())).thenReturn(List.of());

    assertDoesNotThrow(() -> migration.migrateCollections());
  }

  @ParameterizedTest
  @ValueSource(strings = {"part-a-collection", "part-b-collection"})
  void shouldOnlyIncludeObjectIdsInMigration(String collectionName) {
    migration.migrateCollections();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(template).find(queryCaptor.capture(), eq(Document.class), eq(collectionName));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    List<?> idType = queryObject.getEmbedded(List.of(ID_FIELD, "$type"), List.class);
    assertThat("Unexpected id type criteria size.", idType, hasSize(1));
    assertThat("Unexpected id type criteria.", idType.get(0), is("objectId"));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
          part-a-collection | part-b-collection
          part-b-collection | part-a-collection
      """)
  void shouldSaveFormRsWithNewUuid(String formCollection, String noActionCollection) {
    Document document1 = new Document();
    document1.put(ID_FIELD, ObjectId.get());

    Document document2 = new Document();
    document2.put(ID_FIELD, ObjectId.get());

    when(template.find(any(), eq(Document.class), eq(formCollection)))
        .thenReturn(List.of(document1, document2))
        .thenReturn(List.of());
    when(template.remove(any(), eq(formCollection))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.captor();
    verify(template, times(2)).insert(documentCaptor.capture(), eq(formCollection));

    verify(template, never()).insert(any(), eq(noActionCollection));
    verify(template, never()).remove(any(), eq(noActionCollection));

    List<Document> documents = documentCaptor.getAllValues();
    assertThat("Unexpected form save count.", documents.size(), is(2));

    for (Document document : documents) {
      assertDoesNotThrow(() -> UUID.fromString(document.getString(ID_FIELD)));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"part-a-collection", "part-b-collection"})
  void shouldDeleteOriginalForms(String collectionName) {
    ObjectId id1 = ObjectId.get();
    Document document1 = new Document();
    document1.put(ID_FIELD, id1);

    ObjectId id2 = ObjectId.get();
    Document document2 = new Document();
    document2.put(ID_FIELD, id2);

    when(template.find(any(), eq(Document.class), eq(collectionName)))
        .thenReturn(List.of(document1, document2))
        .thenReturn(List.of());

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    when(template.remove(queryCaptor.capture(), eq(collectionName))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected query count.", queries.size(), is(2));

    Document queryObject = queries.get(0).getQueryObject();
    ObjectId id = queryObject.getObjectId("_id");
    assertThat("Unexpected ID requirement.", id, is(id1));

    queryObject = queries.get(1).getQueryObject();
    id = queryObject.getObjectId("_id");
    assertThat("Unexpected ID requirement.", id, is(id2));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void shouldNotFailMigrationWhenDeleteFails(int deletedCount) {
    Document document1 = new Document();
    document1.put(ID_FIELD, ObjectId.get());

    Document document2 = new Document();
    document2.put(ID_FIELD, ObjectId.get());

    when(template.find(any(), eq(Document.class), any()))
        .thenReturn(List.of(document1))
        .thenReturn(List.of(document2))
        .thenReturn(List.of());
    when(template.remove(any(), anyString())).thenReturn(
        DeleteResult.acknowledged(deletedCount));

    assertDoesNotThrow(() -> migration.migrateCollections());
    verify(template, times(2)).remove(any(), anyString());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
