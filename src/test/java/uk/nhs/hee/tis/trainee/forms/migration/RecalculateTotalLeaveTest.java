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
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.mapper.CovidDeclarationMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

class RecalculateTotalLeaveTest {

  private RecalculateTotalLeave migration;

  private MongoTemplate template;
  private FormRPartBService service;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    service = mock(FormRPartBService.class);

    FormRPartBMapperImpl mapper = new FormRPartBMapperImpl();
    ReflectionTestUtils.setField(mapper, "covidDeclarationMapper",
        new CovidDeclarationMapperImpl());
    migration = new RecalculateTotalLeave(template, service, mapper);
  }

  @Test
  void shouldOnlyRecalculateDocumentsWithZeroTotalLeaveAndUnCalculatedLeave() {
    migration.migrate();

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(template).find(queryCaptor.capture(), eq(FormRPartB.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();

    List<Document> andList = queryObject.get("$and", List.class);
    assertThat("Unexpected number of AND conditions.", andList.size(), is(1));

    Document andObject = (Document) andList.get(0);
    int totalLeave = andObject.getInteger("totalLeave");
    assertThat("Unexpected total leave in filter.", totalLeave, is(0));

    List<Document> orList = andObject.get("$or", List.class);
    assertThat("Unexpected number of OR conditions.", orList.size(), is(6));

    Set<String> orFields = orList.stream()
        .filter(doc -> doc.containsValue(new Document().append("$ne", 0)))
        .flatMap(doc -> doc.keySet().stream())
        .collect(Collectors.toSet());
    assertThat("Unexpected OR filter fields.", orFields,
        hasItems("sicknessAbsence", "parentalLeave", "careerBreaks", "paidLeave",
            "unauthorisedLeave", "otherLeave"));
  }

  @Test
  void shouldSumAllLeaveFields() {
    FormRPartB form = FormRPartB.builder()
        .sicknessAbsence(1)
        .parentalLeave(10)
        .careerBreaks(100)
        .paidLeave(1000)
        .unauthorisedLeave(10000)
        .otherLeave(100000)
        .build();
    when(template.find(any(), eq(FormRPartB.class))).thenReturn(Collections.singletonList(form));

    migration.migrate();

    ArgumentCaptor<FormRPartBDto> formCaptor = ArgumentCaptor.forClass(FormRPartBDto.class);
    verify(service).save(formCaptor.capture());

    FormRPartBDto updatedForm = formCaptor.getValue();
    assertThat("Unexpected total leave.", form.getTotalLeave(), is(111111));
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
