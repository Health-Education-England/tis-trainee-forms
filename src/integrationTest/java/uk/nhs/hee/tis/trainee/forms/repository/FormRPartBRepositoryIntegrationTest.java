/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.data.domain.Sort.Direction.ASC;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.FindIterable;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class FormRPartBRepositoryIntegrationTest {

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private MongoTemplate template;

  @MockBean
  private JwtDecoder jwtDecoder;

  @AfterEach
  void tearDown() {
    template.findAllAndRemove(new Query(), FormRPartB.class);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      _id_         | _id
      traineeTisId | traineeTisId
      """)
  void shouldCreateSingleFieldIndexes(String indexName, String fieldName) {
    IndexOperations indexOperations = template.indexOps(FormRPartB.class);
    List<IndexInfo> indexes = indexOperations.getIndexInfo();

    assertThat("Unexpected index count.", indexes, hasSize(2));

    IndexInfo index = indexes.stream()
        .filter(i -> i.getName().equals(indexName))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Expected index not found."));

    List<IndexField> indexFields = index.getIndexFields();
    assertThat("Unexpected index field count.", indexFields, hasSize(1));

    IndexField indexField = indexFields.get(0);
    assertThat("Unexpected index field key.", indexField.getKey(), is(fieldName));
    assertThat("Unexpected index field direction.", indexField.getDirection(), is(ASC));

    assertThat("Unexpected hidden index.", index.isHidden(), is(false));
    assertThat("Unexpected hashed index.", index.isHashed(), is(false));
    assertThat("Unexpected sparse index.", index.isSparse(), is(false));
    assertThat("Unexpected unique index.", index.isUnique(), is(false));
    assertThat("Unexpected wildcard index.", index.isWildcard(), is(false));
  }

  @Test
  void shouldStoreWithUuidIdType() throws JsonProcessingException {
    template.insert(new FormRPartB());

    String document = template.execute(FormRPartB.class, collection -> {
      FindIterable<Document> documents = collection.find();
      return documents.cursor().next().toJson();
    });

    ObjectNode jsonDocument = (ObjectNode) new ObjectMapper().readTree(document);

    String idType = jsonDocument.get("_id").get("$binary").get("subType").textValue();
    assertThat("Unexpected ID format.", idType, is("04"));
  }

  @Test
  void shouldNotStoreUnexpectedFields() throws JsonProcessingException {
    template.insert(new FormRPartB());

    String document = template.execute(FormRPartB.class, collection -> {
      FindIterable<Document> documents = collection.find();
      return documents.cursor().next().toJson();
    });

    ObjectNode jsonDocument = (ObjectNode) new ObjectMapper().readTree(document);
    List<String> fieldNames = new ArrayList<>();
    jsonDocument.fieldNames().forEachRemaining(fieldNames::add);

    assertThat("Unexpected field count.", fieldNames, hasSize(5));
    assertThat("Unexpected fields.", fieldNames,
        hasItems("_id", "currentDeclarations", "previousDeclarations", "work", "_class"));
  }
}
