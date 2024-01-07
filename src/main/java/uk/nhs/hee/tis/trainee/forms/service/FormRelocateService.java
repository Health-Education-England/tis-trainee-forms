/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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

import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartARepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.repository.S3FormRPartBRepositoryImpl;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;


@Slf4j
@Service
@Transactional
@XRayEnabled
public class FormRelocateService {

  private final FormRPartARepository formRPartARepository;
  private final FormRPartBRepository formRPartBRepository;
  private final S3FormRPartARepositoryImpl abstractCloudRepositoryA;
  private final S3FormRPartBRepositoryImpl abstractCloudRepositoryB;

  /**
   * Constructor for Form Relocate service.
   *
   * @param formRPartARepository  spring data repository (Form R Part A)
   * @param formRPartBRepository  spring data repository (Form R Part B)
   * @param abstractCloudRepositoryA  abstract cloud repository (Form R Part A)
   * @param abstractCloudRepositoryB  abstract cloud repository (Form R Part B)
   */
  public FormRelocateService(FormRPartARepository formRPartARepository,
                             FormRPartBRepository formRPartBRepository,
                             S3FormRPartARepositoryImpl abstractCloudRepositoryA,
                             S3FormRPartBRepositoryImpl abstractCloudRepositoryB) {
    this.formRPartARepository = formRPartARepository;
    this.formRPartBRepository = formRPartBRepository;
    this.abstractCloudRepositoryA = abstractCloudRepositoryA;
    this.abstractCloudRepositoryB = abstractCloudRepositoryB;
  }

  /**
   * Relocate Form.
   */
  public void relocateForm(String formId, String targetTrainee) {

    // Get Form from MongoDB by FormId
    AbstractForm form = getMoveFormInfoInDb(formId);

    if (form == null) {
      log.error("Cannot find form with ID " + formId + " from DB.");
      throw new ApplicationException("Cannot find form with ID " + formId + " from DB.");
    }
    else {
      String sourceTrainee = form.getTraineeTisId();
      try {
        performRelocate(form, sourceTrainee, targetTrainee);
      } catch (Exception e) {
        log.error("Fail to relocate form to target trainee: " + e + ". Rolling back...");
        try {
          performRelocate(form, targetTrainee, sourceTrainee);
        } catch (Exception ex) {
          log.error("Fail to roll back: " + ex);
        }
        throw new ApplicationException("Fail to relocate form to target trainee: " + e.toString());
      }
    }
  }

  private AbstractForm getMoveFormInfoInDb(String formId) {
    try {
      Optional<FormRPartA> optionalFormRPartA =
          formRPartARepository.findById(UUID.fromString(formId));
      if (optionalFormRPartA.isPresent()) {
        return optionalFormRPartA.get();
      }
      else {
        Optional<FormRPartB> optionalFormRPartB =
            formRPartBRepository.findById(UUID.fromString(formId));
        return optionalFormRPartB.get();
      }
    } catch (Exception e) {
      log.error("Fail to get form with ID " + formId + ": " + e);
      throw new ApplicationException("Fail to get form with ID " + formId + ": " + e.toString());
    }
  }

  private void performRelocate(AbstractForm abstractForm,
                               String fromTraineeId,
                               String toTraineeId) {
    abstractForm.setTraineeTisId(toTraineeId);
    updateTargetTraineeInDb(abstractForm, toTraineeId);

    if (abstractForm.getLifecycleState() != LifecycleState.DRAFT) {
      relocateFormInS3(abstractForm, fromTraineeId, toTraineeId);
    }
  }

  private void updateTargetTraineeInDb(AbstractForm abstractForm, String targetTrainee) {
    if (abstractForm instanceof FormRPartA formRPartA) {
      formRPartARepository.save(formRPartA);
    } else if (abstractForm instanceof FormRPartB formRPartB) {
      formRPartBRepository.save(formRPartB);
    }
    log.info("Form with ID " + abstractForm.getId()
        + " moved under " + targetTrainee + " in DB ");
  }

  private void relocateFormInS3(AbstractForm abstractForm,
                                String sourceTrainee,
                                String targetTrainee) {
    String formId = abstractForm.getId().toString();

    if (abstractForm instanceof FormRPartA formRPartA) {
      abstractCloudRepositoryA.save(formRPartA);
      log.info("Form " + formId + " in S3 relocated from "
          + sourceTrainee + " to " + targetTrainee);
      abstractCloudRepositoryA.delete(formId, sourceTrainee);
      log.info("Form " + formId + " in S3 deleted.");
    } else if (abstractForm instanceof FormRPartB formRPartB) {
      abstractCloudRepositoryB.save(formRPartB);
      log.info("Form " + formId + " in S3 relocated from "
          + sourceTrainee + " to " + targetTrainee);
      abstractCloudRepositoryB.delete(formId, sourceTrainee);
      log.info("Form " + formId + " in S3 deleted.");
    }
  }
}
