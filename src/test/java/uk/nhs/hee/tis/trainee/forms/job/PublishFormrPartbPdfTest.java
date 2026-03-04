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
 *
 */

package uk.nhs.hee.tis.trainee.forms.job;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.CovidDeclarationMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

class PublishFormrPartbPdfTest {

  private PublishFormrPartbPdf job;

  private FormRPartBRepository repository;
  private PdfService service;

  private FormRPartBMapper mapper;

  @BeforeEach
  void setUp() {
    repository = mock(FormRPartBRepository.class);
    service = mock(PdfService.class);

    mapper = new FormRPartBMapperImpl();
    ReflectionTestUtils.setField(mapper, "covidDeclarationMapper",
        new CovidDeclarationMapperImpl());

    job = new PublishFormrPartbPdf(repository, service, mapper);
  }

  @Test
  void shouldNotPublishWhenNoFormrPartBsFound() {
    when(repository.streamByLifecycleStateIn(any())).thenReturn(Stream.of());

    job.execute();

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = Mode.EXCLUDE, names = {"APPROVED", "DRAFT",
      "REJECTED", "WITHDRAWN"})
  void shouldNotPublishDraftFormrPartBs(LifecycleState state) {
    UUID id1 = UUID.randomUUID();
    FormRPartB form1 = new FormRPartB();
    form1.setId(id1);

    UUID id2 = UUID.randomUUID();
    FormRPartB form2 = new FormRPartB();
    form2.setId(id2);

    ArgumentCaptor<Set<LifecycleState>> statesCaptor = ArgumentCaptor.captor();
    when(repository.streamByLifecycleStateIn(statesCaptor.capture())).thenReturn(Stream.of());

    job.execute();

    Set<LifecycleState> states = statesCaptor.getValue();
    assertThat("Unexpected state query count.", states, hasSize(3));
    assertThat("Unexpected state in query.", states, hasItem(state));
  }

  @Test
  void shouldPublishAllFoundFormrPartBs() throws IOException {
    UUID id1 = UUID.randomUUID();
    FormRPartB form1 = new FormRPartB();
    form1.setId(id1);
    form1.setTraineeTisId("123");

    UUID id2 = UUID.randomUUID();
    FormRPartB form2 = new FormRPartB();
    form2.setId(id2);
    form2.setTraineeTisId("456");

    when(repository.streamByLifecycleStateIn(any())).thenReturn(Stream.of(form1, form2));

    int publishCount = job.execute();

    assertThat("Unexpected published Form-R count.", publishCount, is(2));

    FormRPartBPdfRequestDto expectedRequest1
        = new FormRPartBPdfRequestDto(id1.toString(), "123", mapper.toDto(form1));
    FormRPartBPdfRequestDto expectedRequest2
        = new FormRPartBPdfRequestDto(id2.toString(), "456", mapper.toDto(form2));
    verify(service).generateFormRPartB(expectedRequest1, false);
    verify(service).generateFormRPartB(expectedRequest2, false);
    verifyNoMoreInteractions(service);
  }

  @Test
  void shouldPublishPartialFormrPartBsWhenFailures() throws IOException {
    UUID id1 = UUID.randomUUID();
    FormRPartB form1 = new FormRPartB();
    form1.setId(id1);
    form1.setTraineeTisId("123");

    UUID id2 = UUID.randomUUID();
    FormRPartB form2 = new FormRPartB();
    form2.setId(id2);
    form2.setTraineeTisId("456");

    when(repository.streamByLifecycleStateIn(any())).thenReturn(Stream.of(form1, form2));
    FormRPartBPdfRequestDto expectedRequest1
        = new FormRPartBPdfRequestDto(id1.toString(), "123", mapper.toDto(form1));
    FormRPartBPdfRequestDto expectedRequest2
        = new FormRPartBPdfRequestDto(id2.toString(), "456", mapper.toDto(form2));

    doThrow(RuntimeException.class).when(service)
        .generateFormRPartB(expectedRequest1, false);

    int publishCount = job.execute();

    assertThat("Unexpected published Form-R count.", publishCount, is(1));

    verify(service).generateFormRPartB(expectedRequest1, false);
    verify(service).generateFormRPartB(expectedRequest2, false);
    verifyNoMoreInteractions(service);
  }
}