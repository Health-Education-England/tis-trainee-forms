/*
 * The MIT License (MIT)
 *
 * Copyright 2020 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.mapper.FormRPartAMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.impl.FormRPartAServiceImpl;

@ExtendWith(MockitoExtension.class)
class FormRPartAServiceImplTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final LocalDate DEFAULT_SUBMISSION_DATE = LocalDate.of(2020, 8, 29);

  private FormRPartAServiceImpl service;

  @Mock
  private FormRPartARepository repositoryMock;

  @Mock
  private AmazonS3 s3Mock;

  private FormRPartA entity;


  @BeforeEach
  void setUp() {
    service = new FormRPartAServiceImpl(repositoryMock, new FormRPartAMapperImpl(),
        new ObjectMapper(), s3Mock);
    initData();
  }

  /**
   * init test data.
   */
  void initData() {
    entity = new FormRPartA();
    entity.setId(DEFAULT_ID);
    entity.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    entity.setForename(DEFAULT_FORENAME);
    entity.setSurname(DEFAULT_SURNAME);
  }

  @Test
  void shouldSaveUnsubmittedFormRPartA() {
    entity.setId(null);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);

    when(repositoryMock.save(entity)).thenAnswer(invocation -> {
      FormRPartA entity = invocation.getArgument(0);
      entity.setId(DEFAULT_ID);
      return entity;
    });

    FormRPartADto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), is(DEFAULT_ID));
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getSurname(), is(DEFAULT_SURNAME));

    verifyNoInteractions(s3Mock);
  }

  @Test
  void shouldSaveSubmittedFormRPartA() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(LifecycleState.SUBMITTED);
    dto.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    FormRPartADto savedDto = service.save(dto);

    assertThat("Unexpected form ID.", savedDto.getId(), notNullValue());
    assertThat("Unexpected trainee ID.", savedDto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", savedDto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", savedDto.getSurname(), is(DEFAULT_SURNAME));
    verify(s3Mock).putObject(any(PutObjectRequest.class));
    entity.setId(savedDto.getId());
    verify(repositoryMock).save(entity);
  }

  @Test
  void shouldThrowExceptionWhenFormRPartANotSaved() {
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    FormRPartADto dto = new FormRPartADto();
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(LifecycleState.SUBMITTED);
    dto.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    when(s3Mock.putObject(any())).thenThrow(new RuntimeException("Expected Exception"));

    assertThrows(RuntimeException.class, () -> service.save(dto));
    verifyNoInteractions(repositoryMock);
  }

  @Test
  void shouldGetFormRPartAsByTraineeTisId() {
    List<FormRPartA> entities = Collections.singletonList(entity);
    when(repositoryMock.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(entities);

    List<FormRPartSimpleDto> dtos = service.getFormRPartAsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected numbers of forms.", dtos.size(), is(entities.size()));

    FormRPartSimpleDto dto = dtos.get(0);
    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
  }

  @Test
  void shouldGetFormRPartAById() {
    when(repositoryMock.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID))
        .thenReturn(Optional.of(entity));

    FormRPartADto dto = service.getFormRPartAById(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected form ID.", dto.getId(), is(DEFAULT_ID));
    assertThat("Unexpected trainee ID.", dto.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected forename.", dto.getForename(), is(DEFAULT_FORENAME));
    assertThat("Unexpected surname.", dto.getSurname(), is(DEFAULT_SURNAME));
  }
}
