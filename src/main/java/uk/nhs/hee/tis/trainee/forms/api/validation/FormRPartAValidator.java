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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.service.FormFieldValidationService;

@Component
@Slf4j
public class FormRPartAValidator {

  private static final String FORMR_PARTA_DTO_NAME = "FormRPartADto";

  FormRPartARepository formRPartARepository;

  FormFieldValidationService validatingService;

  public FormRPartAValidator(FormRPartARepository formRPartARepository,
      FormFieldValidationService validatingService) {
    this.formRPartARepository = formRPartARepository;
    this.validatingService = validatingService;
  }

  /**
   * validation for formR PartA fields.
   *
   * @param formRPartADto Dto to be validated
   * @throws MethodArgumentNotValidException if validation fails, throw exception
   */
  public void validate(FormRPartADto formRPartADto) throws MethodArgumentNotValidException {
    List<FieldError> fieldErrors = new ArrayList<>();
    fieldErrors.addAll(checkIfDraftUnique(formRPartADto));
    fieldErrors.addAll(checkSubmittedFormContent(formRPartADto));

    if (!fieldErrors.isEmpty()) {
      BeanPropertyBindingResult bindingResult =
          new BeanPropertyBindingResult(formRPartADto, FORMR_PARTA_DTO_NAME);
      fieldErrors.forEach(bindingResult::addError);
      Method method = this.getClass().getMethods()[0];
      throw new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }
  }

  /**
   * Check when formR PartA Dto is a draft, should validate if it's unique.
   */
  List<FieldError> checkIfDraftUnique(FormRPartADto formRPartADto) {
    List<FieldError> fieldErrors = new ArrayList<>();
    LifecycleState lifecycleState = formRPartADto.getLifecycleState();

    if (lifecycleState.equals(LifecycleState.DRAFT)) {
      // query all drafted formRPartAs
      List<FormRPartA> existingFormRPartA = formRPartARepository
          .findByTraineeTisIdAndLifecycleState(formRPartADto.getTraineeTisId(),
              LifecycleState.DRAFT);
      // there should be only one draft in the db, no more other draft allowed to be saved
      if (!existingFormRPartA.isEmpty()) {
        if (existingFormRPartA.size() == 1) {
          FormRPartA formRPartA = existingFormRPartA.get(0);
          if (formRPartADto.getId() == null || !formRPartA.getId().toString()
              .equals(formRPartADto.getId())) {
            fieldErrors.add(new FieldError(FORMR_PARTA_DTO_NAME, "lifecycleState",
                "Draft form R Part A already exists"));
          }
        } else { // size > 1
          fieldErrors.add(new FieldError(FORMR_PARTA_DTO_NAME, "lifecycleState",
              "More than one draft form R Part A already exist"));
        }
      }
    }
    return fieldErrors;
  }

  /**
   * When formR PartA Dto is submitted, its content should be validated.
   */
  List<FieldError> checkSubmittedFormContent(FormRPartADto formRPartADto) {
    List<FieldError> fieldErrors = new ArrayList<>();
    LifecycleState lifecycleState = formRPartADto.getLifecycleState();

    if (lifecycleState.equals(LifecycleState.SUBMITTED)) {
      //temporary - should be replaced by annotation
      if (formRPartADto.getWholeTimeEquivalent() == null
          || formRPartADto.getWholeTimeEquivalent().isEmpty()) {
        fieldErrors.add(new FieldError(FORMR_PARTA_DTO_NAME, "wholeTimeEquivalent",
            "wholeTimeEquivalent is missing or empty"));
      }

      try {
        validatingService.validateFormRPartA(formRPartADto);
      } catch (ConstraintViolationException e) {
        log.warn("Form R Part A field validation failed for form {}", formRPartADto.getId());

        e.getConstraintViolations().forEach(c -> {
          FieldError err = new FieldError(FORMR_PARTA_DTO_NAME,
              c.getPropertyPath().toString(),
              c.getInvalidValue(),
              false,
              null,
              null,
              c.getMessage());

          fieldErrors.add(err);
        });
      }
    }
    return fieldErrors;
  }
}
