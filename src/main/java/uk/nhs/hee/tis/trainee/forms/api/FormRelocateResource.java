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

package uk.nhs.hee.tis.trainee.forms.api;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.api.util.HeaderUtil;
import uk.nhs.hee.tis.trainee.forms.service.FormRelocateService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@Slf4j
@RestController
@RequestMapping("/api")
@XRayEnabled
public class FormRelocateResource {

  private final FormRelocateService service;

  public FormRelocateResource(FormRelocateService service) {
    this.service = service;
  }

  /**
   * PATCH  /form-relocate : Relocate a form (attach a form from one trainee to another).
   *
   * @param formId  The ID of the form that need to relocate
   * @param targetTrainee  The TraineeTisId of the target trainee
   * @return the status of relocation
   */
  @PatchMapping("/form-relocate/{formId}")
  public ResponseEntity<Void> relocateForm(@PathVariable String formId,
                                            @RequestParam String targetTrainee) throws IOException {
    log.info("Request received to relocate Form with ID {} to trainee {}", formId, targetTrainee);

    if (targetTrainee.isEmpty()) {
      return ResponseEntity.badRequest().headers(HeaderUtil
          .moveFailureAlert("TargetTrainee is empty",
              "Unable to relocate form to target trainee: targetTrainee is empty"))
          .body(null);
    }

    try {
      service.relocateForm(formId, targetTrainee);
    } catch (ApplicationException e) {
      log.warn("Unable to relocate form to target trainee: " + e);
      return ResponseEntity.badRequest().headers(HeaderUtil
          .moveFailureAlert(e.toString(),
              "Unable to relocate form to target trainee." + e)).body(null);
    }

    return ResponseEntity.noContent().build();
  }

  /**
   * Move all FormR's from one trainee to another.
   *
   * @param fromTraineeId The TIS ID of the trainee to move forms from.
   * @param toTraineeId   The TIS ID of the trainee to move forms to.
   * @return True if the FormR's were moved.
   */
  @PatchMapping("/form-relocate/move/{fromTraineeId}/to/{toTraineeId}")
  public ResponseEntity<Boolean> moveForms(@PathVariable String fromTraineeId,
      @PathVariable String toTraineeId) {
    log.info("Request to move FormR's from trainee {} to trainee {}",
        fromTraineeId, toTraineeId);

    service.moveAllForms(fromTraineeId, toTraineeId);
    return ResponseEntity.ok(true);
  }
}
