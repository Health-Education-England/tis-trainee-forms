package uk.nhs.hee.tis.trainee.forms.service;

import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;

@Service
@Validated
@Slf4j
public class FormFieldValidationService {

  public void validateFormRPartA(@Valid FormRPartADto formRPartADto) {
    //do any composite field validation not handled by annotation constraints
    log.info("Successful field validation on FormR PartA {}", formRPartADto.getId());
  }

  public void validateFormRPartB(@Valid FormRPartBDto formRPartBDto) {
    //do any composite field validation not handled by annotation constraints
    log.info("Successful field validation on FormR PartB {}", formRPartBDto.getId());
  }

}
