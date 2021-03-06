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
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;

@Component
public class FormRPartBValidator {

  private static final String FORMR_PARTB_DTO_NAME = "FormRPartBDto";

  FormRPartBRepository formRPartBRepository;

  public FormRPartBValidator(FormRPartBRepository formRPartBRepository) {
    this.formRPartBRepository = formRPartBRepository;
  }

  /**
   * validation for formR PartB fields.
   *
   * @param formRPartBDto Dto to be validated
   * @throws MethodArgumentNotValidException if validation fails, throw exception
   */
  public void validate(FormRPartBDto formRPartBDto) throws MethodArgumentNotValidException {
    List<FieldError> fieldErrors = new ArrayList<>();
    fieldErrors.addAll(checkIfDraftUnique(formRPartBDto));

    if (!fieldErrors.isEmpty()) {
      BeanPropertyBindingResult bindingResult =
          new BeanPropertyBindingResult(formRPartBDto, FORMR_PARTB_DTO_NAME);
      fieldErrors.forEach(bindingResult::addError);
      Method method = this.getClass().getMethods()[0];
      throw new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }
  }

  /**
   * Check when formR PartB Dto is a draft, should validate if it's unique.
   */
  List<FieldError> checkIfDraftUnique(FormRPartBDto formRPartBDto) {
    List<FieldError> fieldErrors = new ArrayList<>();
    LifecycleState lifecycleState = formRPartBDto.getLifecycleState();

    if (lifecycleState.equals(LifecycleState.DRAFT)) {
      // query all drafted formRPartBs
      List<FormRPartB> existingFormRPartB = formRPartBRepository
          .findByTraineeTisIdAndLifecycleState(formRPartBDto.getTraineeTisId(),
              LifecycleState.DRAFT);
      // there should be only one draft in the db, no more other draft allowed to be saved
      if (!existingFormRPartB.isEmpty()) {
        if (existingFormRPartB.size() == 1) {
          FormRPartB formRPartB = existingFormRPartB.get(0);
          if (formRPartBDto.getId() == null || !formRPartB.getId().equals(formRPartBDto.getId())) {
            fieldErrors.add(new FieldError(FORMR_PARTB_DTO_NAME, "lifecycleState",
                "Draft form R Part B already exists"));
          }
        } else { // size > 1
          fieldErrors.add(new FieldError(FORMR_PARTB_DTO_NAME, "lifecycleState",
              "More than one draft form R Part B already exist"));
        }
      }
    }
    return fieldErrors;
  }
}
