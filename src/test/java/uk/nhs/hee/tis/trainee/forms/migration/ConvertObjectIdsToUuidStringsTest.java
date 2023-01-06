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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
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
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.mapper.CovidDeclarationMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

class ConvertObjectIdsToUuidStringsTest {

  private static final String ID_FIELD = "_id";
  private static final String TRAINEE_ID_FIELD = "traineeTisId";
  private static final String BUCKET_NAME = "test-bucket";
  private static final String PART_A_COLLECTION_NAME = "part-a-collection";
  private static final String PART_B_COLLECTION_NAME = "part-b-collection";
  private ConvertObjectIdsToUuidStrings migration;
  private MongoTemplate template;
  private AmazonS3 s3;
  private FormRPartAService partAService;
  private FormRPartBService partBService;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    s3 = mock(AmazonS3.class);
    Environment env = mock(Environment.class);
    when(env.getProperty("application.file-store.bucket")).thenReturn(BUCKET_NAME);

    partAService = mock(FormRPartAService.class);
    FormRPartAMapper partAMapper = new FormRPartAMapperImpl();
    partBService = mock(FormRPartBService.class);
    FormRPartBMapper partBMapper = new FormRPartBMapperImpl();
    ReflectionTestUtils.setField(partBMapper, "covidDeclarationMapper",
        new CovidDeclarationMapperImpl());
    migration = new ConvertObjectIdsToUuidStrings(template, s3, env,
        partAService, partAMapper,
        partBService, partBMapper);

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
    when(template.findAll(eq(Document.class), any())).thenReturn(List.of());

    assertDoesNotThrow(() -> migration.migrateCollections());
  }

  @Test
  void shouldOnlyIncludeObjectIdsInMigration() {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");

    Document document2 = new Document();
    String uuid = UUID.randomUUID().toString();
    document2.put(ID_FIELD, uuid);

    when(template.findAll(eq(Document.class), any())).thenReturn(List.of(document1, document2),
        List.of());
    when(template.remove(any(), any(Class.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    verify(partAService).save(any());
    verify(template).remove(any(), eq(FormRPartA.class));

    verifyNoInteractions(partBService);
    verify(template, new Times(0)).remove(any(), eq(FormRPartB.class));
  }

  @Test
  void shouldSaveFormRPartAsWithNewUuid() {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");

    Document document2 = new Document();
    document2.put(ID_FIELD, "not-uuid-2");

    when(template.findAll(eq(Document.class), eq(PART_A_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));
    when(template.remove(any(), eq(FormRPartA.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    ArgumentCaptor<FormRPartADto> dtoCaptor = ArgumentCaptor.forClass(FormRPartADto.class);
    verify(partAService, new Times(2)).save(dtoCaptor.capture());
    verifyNoInteractions(partBService);

    List<FormRPartADto> dtos = dtoCaptor.getAllValues();
    assertThat("Unexpected DTO save count.", dtos.size(), is(2));

    for (FormRPartADto dto : dtos) {
      assertDoesNotThrow(() -> UUID.fromString(dto.getId()));
    }
  }

  @Test
  void shouldSaveFormRPartBsWithNewUuid() {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");

    Document document2 = new Document();
    document2.put(ID_FIELD, "not-uuid-2");

    when(template.findAll(eq(Document.class), eq(PART_B_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));
    when(template.remove(any(), eq(FormRPartB.class))).thenReturn(DeleteResult.acknowledged(1));

    migration.migrateCollections();

    ArgumentCaptor<FormRPartBDto> dtoCaptor = ArgumentCaptor.forClass(FormRPartBDto.class);
    verify(partBService, new Times(2)).save(dtoCaptor.capture());
    verifyNoInteractions(partAService);

    List<FormRPartBDto> dtos = dtoCaptor.getAllValues();
    assertThat("Unexpected DTO save count.", dtos.size(), is(2));

    for (FormRPartBDto dto : dtos) {
      assertDoesNotThrow(() -> UUID.fromString(dto.getId()));
    }
  }

  @Test
  void shouldDeleteOriginalFormRPartAsFromDatabase() {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");

    Document document2 = new Document();
    document2.put(ID_FIELD, "not-uuid-2");

    when(template.findAll(eq(Document.class), eq(PART_A_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), any(Class.class))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected query count.", queries.size(), is(2));

    Document queryObject = queries.get(0).getQueryObject();
    String id = queryObject.getString("_id");
    assertThat("Unexpected ID requirement.", id, is("not-uuid-1"));

    queryObject = queries.get(1).getQueryObject();
    id = queryObject.getString("_id");
    assertThat("Unexpected ID requirement.", id, is("not-uuid-2"));
  }

  @Test
  void shouldDeleteOriginalFormRPartBsFromDatabase() {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");

    Document document2 = new Document();
    document2.put(ID_FIELD, "not-uuid-2");

    when(template.findAll(eq(Document.class), eq(PART_B_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), any(Class.class))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected query count.", queries.size(), is(2));

    Document queryObject = queries.get(0).getQueryObject();
    String id = queryObject.getString("_id");
    assertThat("Unexpected ID requirement.", id, is("not-uuid-1"));

    queryObject = queries.get(1).getQueryObject();
    id = queryObject.getString("_id");
    assertThat("Unexpected ID requirement.", id, is("not-uuid-2"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void shouldNotFailMigrationWhenDeleteFromDatabaseFails(int deletedCount) {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");

    Document document2 = new Document();
    document2.put(ID_FIELD, "not-uuid-2");

    when(template.findAll(eq(Document.class), any())).thenReturn(List.of(document1),
        List.of(document2));
    when(template.remove(any(), any(Class.class))).thenReturn(
        DeleteResult.acknowledged(deletedCount));

    assertDoesNotThrow(() -> migration.migrateCollections());
    verify(template, new Times(2)).remove(any(), any(Class.class));
  }

  @Test
  void shouldDeleteOriginalFormRPartAsFromS3() {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");
    document1.put(TRAINEE_ID_FIELD, "trainee1");

    Document document2 = new Document();
    document2.put(ID_FIELD, "not-uuid-2");
    document2.put(TRAINEE_ID_FIELD, "trainee2");

    when(template.findAll(eq(Document.class), eq(PART_A_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), any(Class.class))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    String keyTemplate = "%s/forms/%s/%s.json";
    verify(s3).deleteObject(BUCKET_NAME,
        String.format(keyTemplate, "trainee1", "formr-a", "not-uuid-1"));
    verify(s3).deleteObject(BUCKET_NAME,
        String.format(keyTemplate, "trainee2", "formr-a", "not-uuid-2"));
  }

  @Test
  void shouldDeleteOriginalFormRPartBsFromS3() {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");
    document1.put(TRAINEE_ID_FIELD, "trainee1");

    Document document2 = new Document();
    document2.put(ID_FIELD, "not-uuid-2");
    document2.put(TRAINEE_ID_FIELD, "trainee2");

    when(template.findAll(eq(Document.class), eq(PART_B_COLLECTION_NAME))).thenReturn(
        List.of(document1, document2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), any(Class.class))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    String keyTemplate = "%s/forms/%s/%s.json";
    verify(s3).deleteObject(BUCKET_NAME,
        String.format(keyTemplate, "trainee1", "formr-b", "not-uuid-1"));
    verify(s3).deleteObject(BUCKET_NAME,
        String.format(keyTemplate, "trainee2", "formr-b", "not-uuid-2"));
  }

  @Test
  void shouldNotFailMigrationWhenDeleteFromS3Fails() {
    Document document1 = new Document();
    document1.put(ID_FIELD, "not-uuid-1");

    Document document2 = new Document();
    document2.put(ID_FIELD, "not-uuid-2");

    when(template.findAll(eq(Document.class), any())).thenReturn(List.of(document1),
        List.of(document2));
    when(template.remove(any(), any(Class.class))).thenReturn(DeleteResult.acknowledged(1));

    doThrow(RuntimeException.class).when(s3).deleteObject(any(), any());

    assertDoesNotThrow(() -> migration.migrateCollections());
    verify(s3, new Times(2)).deleteObject(any(), any());
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
    verifyNoInteractions(partAService);
    verifyNoInteractions(partBService);
    verifyNoInteractions(s3);
  }
}
