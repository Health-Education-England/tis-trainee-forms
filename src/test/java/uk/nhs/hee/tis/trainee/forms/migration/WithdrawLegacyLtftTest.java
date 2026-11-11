/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;

class WithdrawLegacyLtftTest {

  private WithdrawLegacyLtft migration;
  private MongoTemplate template;
  private LtftFormRepository repository;
  private LtftMapper mapper;
  private AbstractAuditedForm.Status.StatusDetail statusDetail;

  @BeforeEach
  void setUp() {
    template = mock(MongoTemplate.class);
    repository = mock(LtftFormRepository.class);
    mapper = mock(LtftMapper.class);
    statusDetail = mock(AbstractAuditedForm.Status.StatusDetail.class);
    when(mapper.toStatusDetail(any(LftfStatusInfoDetailDto.class))).thenReturn(statusDetail);

    migration = new WithdrawLegacyLtft(template, repository, mapper);
  }

  @Test
  void shouldQueryByListedFormRefs() {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    when(template.find(queryCaptor.capture(), eq(LtftForm.class))).thenReturn(List.of());

    migration.migrate();

    Document queryObject = queryCaptor.getValue().getQueryObject();
    Document formRefCriteria = (Document) queryObject.get("formRef");
    assertThat("Unexpected query criteria.", formRefCriteria, notNullValue());

    List<String> refs = (List<String>) formRefCriteria.get("$in");
    assertThat("Unexpected form refs.", refs,
        hasItems("ltft_281057_001", "ltft_275689_001", "ltft_313691_001"));
  }

  @Test
  void shouldNotFailAndNotSaveWhenNoFormsFound() {
    when(template.find(any(Query.class), eq(LtftForm.class))).thenReturn(List.of());

    migration.migrate();

    verifyNoInteractions(repository);
  }

  @Test
  void shouldMapReasonAndMessageToStatusDetail() {
    when(template.find(any(Query.class), eq(LtftForm.class))).thenReturn(List.of());

    migration.migrate();

    ArgumentCaptor<LftfStatusInfoDetailDto> detailCaptor = ArgumentCaptor.captor();
    verify(mapper).toStatusDetail(detailCaptor.capture());

    LftfStatusInfoDetailDto detail = detailCaptor.getValue();
    assertThat("Unexpected reason.", detail.reason(), is("Withdrawn during pilot"));
    assertThat("Unexpected message.", detail.message().isBlank(), is(false));
  }

  @Test
  void shouldWithdrawSubmittedAndUnsubmittedForms() {
    LtftForm submitted = formWithState("ltft_281057_001", LifecycleState.SUBMITTED, 1);
    LtftForm unsubmitted = formWithState("ltft_248325_001", LifecycleState.UNSUBMITTED, 2);
    when(template.find(any(Query.class), eq(LtftForm.class)))
        .thenReturn(List.of(submitted, unsubmitted));

    migration.migrate();

    assertWithdrawn(submitted, 1);
    assertWithdrawn(unsubmitted, 2);
    verify(repository).save(submitted);
    verify(repository).save(unsubmitted);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"WITHDRAWN", "APPROVED", "REJECTED", "DRAFT"})
  void shouldNotChangeFormsThatCannotTransitionToWithdrawn(LifecycleState state) {
    LtftForm form = formWithState("ltft_190825_001", state, 1);
    when(template.find(any(Query.class), eq(LtftForm.class))).thenReturn(List.of(form));

    migration.migrate();

    assertThat("Unexpected state.", form.getStatus().current().state(), is(state));
    verify(repository, never()).save(form);
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
    verifyNoInteractions(repository);
  }

  private void assertWithdrawn(LtftForm form, int expectedRevision) {
    var current = form.getStatus().current();

    assertThat("Unexpected state.", current.state(), is(LifecycleState.WITHDRAWN));
    assertThat("Unexpected detail.", current.detail(), is(statusDetail));
    assertThat("Unexpected revision.", form.getRevision(), is(expectedRevision));

    Person modifiedBy = current.modifiedBy();
    assertThat("Unexpected modifier name.", modifiedBy.name(), is("System migration"));
    assertThat("Unexpected modifier role.", modifiedBy.role(), is("ADMIN"));
    assertThat("Unexpected modifier email.", modifiedBy.email(), nullValue());
  }

  private LtftForm formWithState(String formRef, LifecycleState state, int revision) {
    LtftForm form = new LtftForm();
    form.setId(UUID.randomUUID());
    form.setFormRef(formRef);
    form.setRevision(revision);
    form.setLifecycleState(state, null, Person.builder().name("Test").build(), revision, null);
    return form;
  }
}
