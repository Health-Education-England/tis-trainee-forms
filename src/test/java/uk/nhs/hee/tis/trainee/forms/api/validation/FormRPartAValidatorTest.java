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

package uk.nhs.hee.tis.trainee.forms.api.validation;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;

@ExtendWith(MockitoExtension.class)
class FormRPartAValidatorTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_TRAINEE_TIS_ID = "DEFAULT_TRAINEE_TIS_ID";
  private static final LifecycleState DEFAULT_LIFECYCLESTATE = LifecycleState.DRAFT;

  @InjectMocks
  FormRPartAValidator validator;

  @Mock
  private FormRPartARepository formRPartARepositoryMock;

  private FormRPartADto formRPartADto;

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    formRPartADto = new FormRPartADto();
    formRPartADto.setId(DEFAULT_ID);
    formRPartADto.setLifecycleState(DEFAULT_LIFECYCLESTATE);
  }

  @Test
  void validateDraftIfNoExistingDraftFound() {
    when(formRPartARepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartADto.getTraineeTisId(),
            formRPartADto.getLifecycleState()))
        .thenReturn(Lists.emptyList());

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartADto);
    assertThat("Should not return any errors",
        fieldErrors.size(), is(0));
  }

  @Test
  void validateDraftIfMultipleDraftsFound() {
    FormRPartA formRPartA1 = new FormRPartA();
    formRPartA1.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA1.setId("ANOTHER_ID_1");
    formRPartA1.setLifecycleState(LifecycleState.DRAFT);

    FormRPartA formRPartA2 = new FormRPartA();
    formRPartA2.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA2.setId("ANOTHER_ID_2");
    formRPartA2.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartARepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartADto.getTraineeTisId(),
            formRPartADto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartA1, formRPartA2));

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartADto);
    assertThat("Should return 1 error", fieldErrors.size(), is(1));
    assertThat("Error not valid", fieldErrors.get(0).getDefaultMessage(),
        is("More than one draft form R Part A already exist"));
  }

  @Test
  void validateUpdateDraftIfOneDraftWithSameIdFound() {
    FormRPartA formRPartA = new FormRPartA();
    formRPartA.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA.setId(DEFAULT_ID);
    formRPartA.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartARepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartADto.getTraineeTisId(),
            formRPartADto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartA));

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartADto);
    assertThat("Should not return any errors", fieldErrors.size(), is(0));
  }

  @Test
  void validateUpdateDraftIfOneDraftWithDifferentIdFound() {
    FormRPartA formRPartA = new FormRPartA();
    formRPartA.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA.setId("ANOTHER_ID");
    formRPartA.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartARepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartADto.getTraineeTisId(),
            formRPartADto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartA));

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartADto);
    assertThat("Should return 1 error", fieldErrors.size(), is(1));
    assertThat("Error not valid", fieldErrors.get(0).getDefaultMessage(),
        is("Draft form R Part A already exists"));
  }

  @Test
  void validateCreateDraftIfOneDraftFound() {
    formRPartADto.setId(null);

    FormRPartA formRPartA = new FormRPartA();
    formRPartA.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA.setId("ANOTHER_ID");
    formRPartA.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartARepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartADto.getTraineeTisId(),
            formRPartADto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartA));

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartADto);
    assertThat("Should return 1 error", fieldErrors.size(), is(1));
    assertThat("Error not valid", fieldErrors.get(0).getDefaultMessage(),
        is("Draft form R Part A already exists"));
  }

  @Test
  void validateShouldThrowExceptionWhenValidationFails() {
    FormRPartA formRPartA = new FormRPartA();
    formRPartA.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA.setId("ANOTHER_ID");
    formRPartA.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartARepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartADto.getTraineeTisId(),
            formRPartADto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartA));

    String message = assertThrows(MethodArgumentNotValidException.class,
        () -> validator.validate(formRPartADto)).getMessage();

    assertThat(message, containsString("Draft form R Part A already exists"));
  }

  @Test
  void validateShouldNotThrowExceptionWhenValidationSucceed() {
    when(formRPartARepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartADto.getTraineeTisId(),
            formRPartADto.getLifecycleState()))
        .thenReturn(Lists.emptyList());

    assertDoesNotThrow(() -> validator.validate(formRPartADto));
  }
}
