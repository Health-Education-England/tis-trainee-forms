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

package uk.nhs.hee.tis.trainee.forms.service.exception;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;

@ExtendWith(MockitoExtension.class)
class ExceptionTranslatorTest {

  @InjectMocks
  ExceptionTranslator translator;

  @Test
  void testProcessValidationErrorShouldReturnExpectedErrorFormat() {
    FormRPartADto formRPartADto = Mockito.mock(FormRPartADto.class);
    BindingResult bindingResult = new BeanPropertyBindingResult(formRPartADto, "FormRPartADto");

    FieldError fieldError = new FieldError("FormRPartADto", "lifecycleState",
        "Draft form R Part A already exists");
    bindingResult.addError(fieldError);

    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
    ErrorVM error = translator.processValidationError(ex);

    error.getMessage();
    assertThat("should contain error message", error.getMessage(),
        is(ErrorConstants.ERR_VALIDATION));
    assertThat("should contain 1 fieldError", error.getFieldErrors().size(), is(1));
  }
}
