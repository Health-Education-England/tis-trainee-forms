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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.util.Lists;
import org.hibernate.validator.internal.engine.path.PathImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.FormFieldValidationService;

@ExtendWith(MockitoExtension.class)
class FormRPartAValidatorTest {

  private static final UUID DEFAULT_ID = UUID.randomUUID();
  private static final UUID ANOTHER_ID = UUID.randomUUID();
  private static final String DEFAULT_TRAINEE_TIS_ID = "DEFAULT_TRAINEE_TIS_ID";
  private static final LifecycleState DEFAULT_LIFECYCLESTATE = LifecycleState.DRAFT;

  FormRPartAValidator validator;

  @Mock
  private FormRPartARepository formRPartARepositoryMock;

  @Mock
  private FormFieldValidationService formFieldValidationServiceMock;

  private FormRPartADto formRPartADto;

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    formRPartADto = new FormRPartADto();
    formRPartADto.setId(DEFAULT_ID.toString());
    formRPartADto.setLifecycleState(DEFAULT_LIFECYCLESTATE);

    formRPartARepositoryMock = mock(FormRPartARepository.class);
    formFieldValidationServiceMock = mock(FormFieldValidationService.class);
    validator = new FormRPartAValidator(formRPartARepositoryMock, formFieldValidationServiceMock);
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
    formRPartA1.setId(ANOTHER_ID);
    formRPartA1.setLifecycleState(LifecycleState.DRAFT);

    FormRPartA formRPartA2 = new FormRPartA();
    formRPartA2.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartA2.setId(UUID.randomUUID());
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
    formRPartA.setId(ANOTHER_ID);
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
    formRPartA.setId(ANOTHER_ID);
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
    formRPartA.setId(ANOTHER_ID);
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

  @Test
  void shouldNotValidateSubmittedForm() {
    formRPartADto.setLifecycleState(LifecycleState.SUBMITTED);

    List<FieldError> fieldErrors = validator.checkIfDraftUnique(formRPartADto);

    assertThat("Unexpected number of errors.", fieldErrors.size(), is(0));
    verifyNoInteractions(formRPartARepositoryMock);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldReturnFieldErrorWhenSubmittedFormWteMissingOrNull(String value) {
    formRPartADto.setLifecycleState(LifecycleState.SUBMITTED);
    formRPartADto.setWholeTimeEquivalent(value);
    List<FieldError> fieldErrors = validator.checkSubmittedFormContent(formRPartADto);
    assertThat("Should return an error", fieldErrors.size(), is(1));
  }

  @Test
  void shouldNotReturnFieldErrorWhenSubmittedFormWteValid() {
    formRPartADto.setLifecycleState(LifecycleState.SUBMITTED);
    formRPartADto.setWholeTimeEquivalent("0.99");
    List<FieldError> fieldErrors = validator.checkSubmittedFormContent(formRPartADto);
    assertThat("Should not return an error", fieldErrors.size(), is(0));
  }

  @ParameterizedTest
  @EnumSource(
      value = LifecycleState.class,
      names = {"SUBMITTED"},
      mode = Mode.EXCLUDE)
  void shouldNotReturnFieldErrorWhenNotSubmittedForm(LifecycleState state) {
    formRPartADto.setLifecycleState(state);
    formRPartADto.setWholeTimeEquivalent(null);
    List<FieldError> fieldErrors = validator.checkSubmittedFormContent(formRPartADto);
    assertThat("Should not return an error", fieldErrors.size(), is(0));
  }

  @Test
  void shouldAddSubmittedFormFieldViolationsToErrors() {
    String violationMessage = "Constraint violation";
    String dottedPath = "class.instance.field";
    String invalidValue = "invalid value";

    ConstraintViolation<FormRPartADto> cv
        = createDummyConstraintViolation(violationMessage, dottedPath, invalidValue);

    formRPartADto.setWholeTimeEquivalent("0.99");
    ConstraintViolationException e
        = new ConstraintViolationException("violation message", Set.of(cv));
    doThrow(e).when(formFieldValidationServiceMock).validateFormRPartA(any());

    formRPartADto.setLifecycleState(LifecycleState.SUBMITTED);
    List<FieldError> fieldErrors = validator.checkSubmittedFormContent(formRPartADto);
    assertThat("Should return an error", fieldErrors.size(), is(1));
    FieldError fieldError = fieldErrors.get(0);
    assertThat("Should include the violation message", fieldError.getDefaultMessage(),
        is(violationMessage));
    assertThat("Should include the path as the field", fieldError.getField(),
        is(dottedPath));
    assertThat("Should include the invalid value", fieldError.getRejectedValue(),
        is("invalid value"));
  }

  @ExtendWith(OutputCaptureExtension.class)
  @Test
  void validationErrorsAreLogged(CapturedOutput output) {
    String violationMessage = "Constraint violation";
    String dottedPath = "class.instance.field";
    String invalidValue = "invalid value";

    ConstraintViolation<FormRPartADto> cv
        = createDummyConstraintViolation(violationMessage, dottedPath, invalidValue);

    formRPartADto.setWholeTimeEquivalent("0.99");
    ConstraintViolationException e
        = new ConstraintViolationException("violation message", Set.of(cv));
    doThrow(e).when(formFieldValidationServiceMock).validateFormRPartA(any());

    formRPartADto.setLifecycleState(LifecycleState.SUBMITTED);

    assertThrows(MethodArgumentNotValidException.class,
        () -> validator.validate(formRPartADto)).getMessage();

    assertThat("Validation errors should be logged",
        output.getOut().contains("Field error in object 'FormRPartADto' "
            + "on field 'class.instance.field': "
            + "rejected value [invalid value]; "
            + "codes []; arguments []; "
            + "default message [Constraint violation]"), is(true));
  }

  private ConstraintViolation<FormRPartADto> createDummyConstraintViolation(String message,
      String dottedPath, String invalidValue) {
    ConstraintViolation<FormRPartADto> cv = new ConstraintViolation<>() {
      @Override
      public String getMessage() {
        return message;
      }

      @Override
      public String getMessageTemplate() {
        return null;
      }

      @Override
      public FormRPartADto getRootBean() {
        return null;
      }

      @Override
      public Class<FormRPartADto> getRootBeanClass() {
        return null;
      }

      @Override
      public Object getLeafBean() {
        return null;
      }

      @Override
      public Object[] getExecutableParameters() {
        return new Object[0];
      }

      @Override
      public Object getExecutableReturnValue() {
        return null;
      }

      @Override
      public Path getPropertyPath() {
        return PathImpl.createPathFromString(dottedPath);
      }

      @Override
      public Object getInvalidValue() {
        return invalidValue;
      }

      @Override
      public ConstraintDescriptor<?> getConstraintDescriptor() {
        return null;
      }

      @Override
      public <U> U unwrap(Class<U> type) {
        return null;
      }
    };
    return cv;
  }
}
