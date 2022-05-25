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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.AssertionErrors.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.mapper.CovidDeclarationMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartBMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.service.impl.FormRPartBServiceImpl;

@ExtendWith(MockitoExtension.class)
class SortWorkPlacementsTest {
  private static final LocalDate OLDEST_WORK = LocalDate.of(2000, 1, 1);
  private static final LocalDate MIDDLE_WORK = LocalDate.of(2010, 10, 10);
  private static final LocalDate NEWEST_WORK = LocalDate.of(2020, 2, 5);

  private Work workOldest;
  private Work workMiddle;
  private Work workNewest;

  private SortWorkPlacements migration;

  @Mock
  private MongoTemplate template;

  @Mock
  private CovidDeclarationMapper covidDeclarationMapper;

  @Mock
  private FormRPartBServiceImpl service;

  @InjectMocks
  FormRPartBMapper mapper = new FormRPartBMapperImpl();

  @BeforeEach
  void setUp() {
    migration = new SortWorkPlacements(template, service, mapper);

    workOldest = new Work();
    workOldest.setEndDate(OLDEST_WORK);
    workMiddle = new Work();
    workMiddle.setEndDate(MIDDLE_WORK);
    workNewest = new Work();
    workNewest.setEndDate(NEWEST_WORK);
  }

  @Test
  void shouldNotSaveFormsWithWorkPlacementsAlreadySorted() {
    //given
    List<Work> workInOrder = new ArrayList<>();
    workInOrder.add(workNewest);
    workInOrder.add(workMiddle);
    workInOrder.add(workOldest);

    FormRPartB form = new FormRPartB();
    form.setWork(workInOrder);

    when(template.findAll(FormRPartB.class)).thenReturn(Collections.singletonList(form));

    //when
    migration.migrate();

    //then
    verifyNoInteractions(service);
  }

  @Test
  void shouldSortWorkPlacementsWithoutEndDates() {
    //given
    List<Work> workInOrder = new ArrayList<>();
    workInOrder.add(workNewest);
    workInOrder.add(new Work());
    workInOrder.add(workOldest);

    FormRPartB form = new FormRPartB();
    form.setWork(workInOrder);

    when(covidDeclarationMapper.toDto(any())).thenReturn(null);
    when(template.findAll(FormRPartB.class)).thenReturn(Collections.singletonList(form));

    //when
    migration.migrate();

    //then
    ArgumentCaptor<FormRPartBDto> formCaptor = ArgumentCaptor.forClass(FormRPartBDto.class);
    verify(service).save(formCaptor.capture());

    FormRPartBDto updatedForm = formCaptor.getValue();
    List<WorkDto> sortedWorkDto = updatedForm.getWork();
    assertNull("Unexpected work ordering.",
        sortedWorkDto.get(0).getEndDate());
    assertThat("Unexpected work ordering.",
        sortedWorkDto.get(1).getEndDate().isAfter(sortedWorkDto.get(2).getEndDate()), is(true));
  }

  @Test
  void shouldSaveFormsWithWorkPlacementsThatNeedToBeSorted() {
    //given
    List<Work> workInOrder = new ArrayList<>();
    workInOrder.add(workMiddle);
    workInOrder.add(workOldest);
    workInOrder.add(workNewest);

    FormRPartB form = new FormRPartB();
    form.setWork(workInOrder);

    when(template.findAll(FormRPartB.class)).thenReturn(Collections.singletonList(form));

    //when
    migration.migrate();

    //then
    ArgumentCaptor<FormRPartBDto> formCaptor = ArgumentCaptor.forClass(FormRPartBDto.class);
    verify(service).save(formCaptor.capture());

    FormRPartBDto updatedForm = formCaptor.getValue();
    List<WorkDto> sortedWorkDto = updatedForm.getWork();
    assertThat("Unexpected work ordering.",
        sortedWorkDto.get(0).getEndDate().isAfter(sortedWorkDto.get(1).getEndDate()), is(true));
    assertThat("Unexpected work ordering.",
        sortedWorkDto.get(1).getEndDate().isAfter(sortedWorkDto.get(2).getEndDate()), is(true));
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(template);
  }
}
