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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.nhs.hee.tis.trainee.forms.model.AbstractForm;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartARepository;
import uk.nhs.hee.tis.trainee.forms.repository.FormRPartBRepository;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;


@Slf4j
@Service
@Transactional
@XRayEnabled
public class FormRelocateService {

  private final FormRPartARepository formRPartARepository;
  private final FormRPartBRepository formRPartBRepository;

  private final LtftService ltftService;

  /**
   * Constructor for Form Relocate service.
   *
   * @param formRPartARepository  spring data repository (Form R Part A)
   * @param formRPartBRepository  spring data repository (Form R Part B)
   * @param ltftService           LTFT service
   */
  public FormRelocateService(FormRPartARepository formRPartARepository, FormRPartBRepository formRPartBRepository, LtftService ltftService) {
    this.formRPartARepository = formRPartARepository;
    this.formRPartBRepository = formRPartBRepository;
    this.ltftService = ltftService;
  }

  /**
   * Move all FormR and LTFT forms from source trainee to target trainee.
   * Errors are logged but do not stop the process.
   *
   * @param sourceTraineeTisId The TIS ID of the source trainee.
   * @param targetTraineeTisId The TIS ID of the target trainee.
   */
  public void moveAllForms(String sourceTraineeTisId, String targetTraineeTisId) {
    AtomicReference<Integer> movedFormA = new AtomicReference<>(0);
    List<FormRPartA> formRPartAs = formRPartARepository.findByTraineeTisId(sourceTraineeTisId);
    log.info("Moving {} FormR PartA's from {} to {}.",
        formRPartAs.size(), sourceTraineeTisId, targetTraineeTisId);
    formRPartAs.forEach(form -> {
      try {
        relocateForm(form.getId().toString(), targetTraineeTisId);
        movedFormA.getAndSet(movedFormA.get() + 1);
      } catch (ApplicationException e) {
        log.error("Error occurred when moving FormR PartA {}: {}", form.getId(), e.toString());
      }
    });
    log.debug("Moved {} of {} FormR PartA from {} to {}.",
        movedFormA.get(), formRPartAs.size(), sourceTraineeTisId, targetTraineeTisId);

    AtomicReference<Integer> movedFormB = new AtomicReference<>(0);
    List<FormRPartB> formRPartBs = formRPartBRepository.findByTraineeTisId(sourceTraineeTisId);
    log.info("Moving {} FormR PartB's from {} to {}",
        formRPartBs.size(), sourceTraineeTisId, targetTraineeTisId);
    formRPartBs.forEach(form -> {
      try {
        relocateForm(form.getId().toString(), targetTraineeTisId);
        movedFormB.getAndSet(movedFormB.get() + 1);
      } catch (ApplicationException e) {
        log.error("Error occurred when moving FormR PartB {}: {}", form.getId(), e.toString());
      }
    });
    log.debug("Moved {} of {} FormR PartB from {} to {}.",
        movedFormB.get(), formRPartBs.size(), sourceTraineeTisId, targetTraineeTisId);

    // Move LTFT forms
    Map<String, Integer> movedStats
        = ltftService.moveLtftForms(sourceTraineeTisId, targetTraineeTisId);
    log.debug("Moved {} LTFT forms and {} submission histories from {} to {}.",
        movedStats.get("ltft"), movedStats.get("ltft-submission"), sourceTraineeTisId,
        targetTraineeTisId);
  }

  /**
   * Relocate Form.
   */
  public void relocateForm(String formId, String targetTrainee) {

    // Get Form from MongoDB by FormId
    Optional<AbstractForm> optionalForm = getMoveFormInfoInDb(formId);

    if (optionalForm.isEmpty()) {
      log.error("Cannot find form with ID " + formId + " from DB.");
      throw new ApplicationException("Cannot find form with ID " + formId + " from DB.");
    }
    else {
      AbstractForm form = optionalForm.get();
      String sourceTrainee = form.getTraineeTisId();

      if (sourceTrainee.equals(targetTrainee)) {
        log.error("The form is attached to the trainee " + targetTrainee + " already.");
        throw new ApplicationException(
            "The form is attached to the trainee " + targetTrainee + " already.");
      }

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

  private Optional<AbstractForm> getMoveFormInfoInDb(String formId) {
    UUID formUuid = UUID.fromString(formId);
    try {
      Optional<FormRPartA> optionalFormRPartA = formRPartARepository.findById(formUuid);
      if (optionalFormRPartA.isPresent()) {
        return Optional.of(optionalFormRPartA.get());
      }
    } catch (Exception e) {
      log.error("Failed to get Form R Part A with ID {}: {}", formId, e.toString());
    }
    //try PartB repository, even if PartA lookup failed
    try {
      Optional<FormRPartB> optionalFormRPartB = formRPartBRepository.findById(formUuid);
      return Optional.ofNullable(optionalFormRPartB.orElse(null));
    } catch (Exception e) {
      log.error("Failed to get Form R Part B with ID {}: {}", formId, e.toString());
      throw new ApplicationException("Fail to get form with ID " + formId + ": " + e);
    }
  }

  private void performRelocate(AbstractForm abstractForm,
                               String fromTraineeId,
                               String toTraineeId) {
    abstractForm.setTraineeTisId(toTraineeId);
    updateTargetTraineeInDb(abstractForm, toTraineeId);
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
}
