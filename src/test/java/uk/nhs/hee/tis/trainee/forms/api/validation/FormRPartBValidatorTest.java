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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;

@ExtendWith(MockitoExtension.class)
class FormRPartBValidatorTest {

  private static final UUID DEFAULT_ID = UUID.randomUUID();
  private static final UUID ANOTHER_ID = UUID.randomUUID();
  private static final String DEFAULT_TRAINEE_TIS_ID = "DEFAULT_TRAINEE_TIS_ID";
  private static final LifecycleState DEFAULT_LIFECYCLESTATE = LifecycleState.DRAFT;

  @InjectMocks
  FormRPartBValidator validator;

  @Mock
  private FormRPartBRepository formRPartBRepositoryMock;

  private FormRPartBDto formRPartBDto;

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    formRPartBDto = new FormRPartBDto();
    formRPartBDto.setId(DEFAULT_ID.toString());
    formRPartBDto.setLifecycleState(DEFAULT_LIFECYCLESTATE);
  }

  @Test
  void validateDraftIfNoExistingDraftFound() {
    when(formRPartBRepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartBDto.getTraineeTisId(),
            formRPartBDto.getLifecycleState()))
        .thenReturn(Lists.emptyList());

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartBDto);
    assertThat("Should not return any errors",
        fieldErrors.size(), is(0));
  }

  @Test
  void validateDraftIfMultipleDraftsFound() {
    FormRPartB formRPartB1 = new FormRPartB();
    formRPartB1.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB1.setId(ANOTHER_ID);
    formRPartB1.setLifecycleState(LifecycleState.DRAFT);

    FormRPartB formRPartB2 = new FormRPartB();
    formRPartB2.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB2.setId(UUID.randomUUID());
    formRPartB2.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartBRepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartBDto.getTraineeTisId(),
            formRPartBDto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartB1, formRPartB2));

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartBDto);
    assertThat("Should return 1 error", fieldErrors.size(), is(1));
    assertThat("Error not valid", fieldErrors.get(0).getDefaultMessage(),
        is("More than one draft form R Part B already exist"));
  }

  @Test
  void validateUpdateDraftIfOneDraftWithSameIdFound() {
    FormRPartB formRPartB = new FormRPartB();
    formRPartB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB.setId(DEFAULT_ID);
    formRPartB.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartBRepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartBDto.getTraineeTisId(),
            formRPartBDto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartB));

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartBDto);
    assertThat("Should not return any errors", fieldErrors.size(), is(0));
  }

  @Test
  void validateUpdateDraftIfOneDraftWithDifferentIdFound() {
    FormRPartB formRPartB = new FormRPartB();
    formRPartB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB.setId(ANOTHER_ID);
    formRPartB.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartBRepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartBDto.getTraineeTisId(),
            formRPartBDto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartB));

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartBDto);
    assertThat("Should return 1 error", fieldErrors.size(), is(1));
    assertThat("Error not valid", fieldErrors.get(0).getDefaultMessage(),
        is("Draft form R Part B already exists"));
  }

  @Test
  void validateCreateDraftIfOneDraftFound() {
    formRPartBDto.setId(null);

    FormRPartB formRPartB = new FormRPartB();
    formRPartB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB.setId(ANOTHER_ID);
    formRPartB.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartBRepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartBDto.getTraineeTisId(),
            formRPartBDto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartB));

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartBDto);
    assertThat("Should return 1 error", fieldErrors.size(), is(1));
    assertThat("Error not valid", fieldErrors.get(0).getDefaultMessage(),
        is("Draft form R Part B already exists"));
  }

  @Test
  void validateShouldThrowExceptionWhenValidationFails() {
    FormRPartB formRPartB = new FormRPartB();
    formRPartB.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartB.setId(ANOTHER_ID);
    formRPartB.setLifecycleState(LifecycleState.DRAFT);

    when(formRPartBRepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartBDto.getTraineeTisId(),
            formRPartBDto.getLifecycleState()))
        .thenReturn(Lists.list(formRPartB));

    String message = assertThrows(MethodArgumentNotValidException.class,
        () -> validator.validate(formRPartBDto)).getMessage();

    assertThat(message, containsString("Draft form R Part B already exists"));
  }

  @Test
  void validateShouldNotThrowExceptionWhenValidationSucceed() {
    when(formRPartBRepositoryMock
        .findByTraineeTisIdAndLifecycleState(formRPartBDto.getTraineeTisId(),
            formRPartBDto.getLifecycleState()))
        .thenReturn(Lists.emptyList());

    assertDoesNotThrow(() -> validator.validate(formRPartBDto));
  }

  @Test
  void shouldNotValidateSubmittedForm() {
    formRPartBDto.setLifecycleState(LifecycleState.SUBMITTED);

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartBDto);

    assertThat("Unexpected number of errors.", fieldErrors.size(), is(0));
    verifyNoInteractions(formRPartBRepositoryMock);
  }
}
