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
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.mapper.CovidDeclarationMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

class ConvertObjectIdsToUuidStringsTest {

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
  }

  @Test
  void shouldNotFailWhenNoDocumentsToMigrate() {
    when(template.find(any(), any(), any())).thenReturn(List.of());

    assertDoesNotThrow(() -> migration.migrateCollections());
  }

  @Test
  void shouldOnlyIncludeObjectIdsInMigration() {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.find(queryCaptor.capture(), any(), any())).thenReturn(List.of());

    migration.migrateCollections();

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected query count.", queries.size(), is(2));

    for (Query query : queries) {
      Document queryObject = query.getQueryObject();
      Object type = queryObject.getEmbedded(List.of("_id", "$type"), List.class).get(0);
      assertThat("Unexpected ID requirement.", type, is("objectId"));
    }
  }

  @Test
  void shouldSaveFormRPartAsWithNewUuid() {
    FormRPartA form1 = new FormRPartA();
    form1.setId("not-uuid-1");

    FormRPartA form2 = new FormRPartA();
    form2.setId("not-uuid-2");

    when(template.find(any(), eq(FormRPartA.class), eq(PART_A_COLLECTION_NAME))).thenReturn(
        List.of(form1, form2));
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
    FormRPartB form1 = new FormRPartB();
    form1.setId("not-uuid-1");

    FormRPartB form2 = new FormRPartB();
    form2.setId("not-uuid-2");

    when(template.find(any(), eq(FormRPartB.class), eq(PART_B_COLLECTION_NAME))).thenReturn(
        List.of(form1, form2));
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
    FormRPartA form1 = new FormRPartA();
    form1.setId("not-uuid-1");

    FormRPartA form2 = new FormRPartA();
    form2.setId("not-uuid-2");

    when(template.find(any(), eq(FormRPartA.class), any())).thenReturn(List.of(form1, form2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), eq(FormRPartA.class))).thenReturn(
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
    FormRPartB form1 = new FormRPartB();
    form1.setId("not-uuid-1");

    FormRPartB form2 = new FormRPartB();
    form2.setId("not-uuid-2");

    when(template.find(any(), eq(FormRPartB.class), any())).thenReturn(List.of(form1, form2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), eq(FormRPartB.class))).thenReturn(
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
    FormRPartA form1 = new FormRPartA();
    form1.setId("not-uuid-1");

    FormRPartB form2 = new FormRPartB();
    form2.setId("not-uuid-2");

    when(template.find(any(), eq(FormRPartA.class), any())).thenReturn(List.of(form1));
    when(template.find(any(), eq(FormRPartB.class), any())).thenReturn(List.of(form2));
    when(template.remove(any(), any(Class.class))).thenReturn(
        DeleteResult.acknowledged(deletedCount));

    assertDoesNotThrow(() -> migration.migrateCollections());
    verify(template, new Times(2)).remove(any(), any(Class.class));
  }

  @Test
  void shouldDeleteOriginalFormRPartAsFromS3() {
    FormRPartA form1 = new FormRPartA();
    form1.setId("not-uuid-1");
    form1.setTraineeTisId("trainee1");

    FormRPartA form2 = new FormRPartA();
    form2.setId("not-uuid-2");
    form2.setTraineeTisId("trainee2");

    when(template.find(any(), eq(FormRPartA.class), any())).thenReturn(List.of(form1, form2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), eq(FormRPartA.class))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    String keyTemplate = "%s/forms/%s/%s.json";
    verify(s3).deleteObject(BUCKET_NAME,
        String.format(keyTemplate, "trainee1", form1.getFormType(), "not-uuid-1"));
    verify(s3).deleteObject(BUCKET_NAME,
        String.format(keyTemplate, "trainee2", form1.getFormType(), "not-uuid-2"));
  }

  @Test
  void shouldDeleteOriginalFormRPartBsFromS3() {
    FormRPartB form1 = new FormRPartB();
    form1.setId("not-uuid-1");
    form1.setTraineeTisId("trainee1");

    FormRPartB form2 = new FormRPartB();
    form2.setId("not-uuid-2");
    form2.setTraineeTisId("trainee2");

    when(template.find(any(), eq(FormRPartB.class), any())).thenReturn(List.of(form1, form2));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    when(template.remove(queryCaptor.capture(), eq(FormRPartB.class))).thenReturn(
        DeleteResult.acknowledged(1));

    migration.migrateCollections();

    String keyTemplate = "%s/forms/%s/%s.json";
    verify(s3).deleteObject(BUCKET_NAME,
        String.format(keyTemplate, "trainee1", form1.getFormType(), "not-uuid-1"));
    verify(s3).deleteObject(BUCKET_NAME,
        String.format(keyTemplate, "trainee2", form1.getFormType(), "not-uuid-2"));
  }

  @Test
  void shouldNotFailMigrationWhenDeleteFromS3Fails() {
    FormRPartA form1 = new FormRPartA();
    form1.setId("not-uuid-1");

    FormRPartB form2 = new FormRPartB();
    form2.setId("not-uuid-2");

    when(template.find(any(), eq(FormRPartA.class), any())).thenReturn(List.of(form1));
    when(template.find(any(), eq(FormRPartB.class), any())).thenReturn(List.of(form2));
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
