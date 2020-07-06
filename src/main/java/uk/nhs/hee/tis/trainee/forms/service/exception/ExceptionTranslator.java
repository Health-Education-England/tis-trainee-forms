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

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures.
 */
@ControllerAdvice
public class ExceptionTranslator {

  private final Logger log = LoggerFactory
      .getLogger(ExceptionTranslator.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorVM processValidationError(MethodArgumentNotValidException ex) {
    BindingResult result = ex.getBindingResult();
    List<FieldError> fieldErrors = result.getFieldErrors();

    return processFieldErrors(fieldErrors);
  }

  private ErrorVM processFieldErrors(
      List<FieldError> fieldErrors) {
    ErrorVM dto = new ErrorVM(ErrorConstants.ERR_VALIDATION);

    for (FieldError fieldError : fieldErrors) {
      dto.add(fieldError.getObjectName(), fieldError.getField(), fieldError.getDefaultMessage());
    }

    return dto;
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorVM> processRuntimeException(
      Exception ex) {
    BodyBuilder builder;
    ErrorVM errorVM;
    log.error(ex.getMessage(), ex);
    ResponseStatus responseStatus = AnnotationUtils
        .findAnnotation(ex.getClass(), ResponseStatus.class);
    if (responseStatus != null) {
      builder = ResponseEntity.status(responseStatus.value());
      errorVM = new ErrorVM("error." + responseStatus.value().value(), responseStatus.reason());
    } else {
      builder = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR);
      errorVM = new ErrorVM(ErrorConstants.ERR_INTERNAL_SERVER_ERROR, "Internal server error");
    }
    return builder.body(errorVM);
  }
}
