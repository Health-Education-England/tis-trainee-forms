package uk.nhs.hee.tis.trainee.forms.service;

import javax.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;

@Service
@Validated
public class FormFieldValidationService {

  public void validateFormRPartA(@Valid FormRPartADto formRPartADto) {
    //do any composite field validation not handled by annotation constraints
    String x = "adda";
  }

}
