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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.nhs.hee.tis.trainee.forms.api.util.HeaderUtil;
import uk.nhs.hee.tis.trainee.forms.service.FormRelocateService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api")
@XRayEnabled
public class FormRelocateResource {

  private static final String ENTITY_NAME = "formR_partA";

  private final FormRelocateService service;

  public FormRelocateResource(FormRelocateService service) {
    this.service = service;
  }

  /**
   * POST  /form-relocate : Relocate a form (attach a form from one trainee to another)
   *
   * @param formId  The ID of the form that need to relocate
   * @param targetTrainee  The TraineeTisId of the target trainee
   * @return the status of relocation
   */
  @PostMapping("/form-relocate/{formId}")
  public ResponseEntity<Void> relocateFormR(@PathVariable String formId,
                                            @RequestParam String targetTrainee) throws IOException{
    log.info("Request received to relocate Form with ID {} to trainee {}", formId, targetTrainee);

    if (targetTrainee.isEmpty()) {
      return ResponseEntity.badRequest().headers(HeaderUtil
          .moveFailureAlert("TargetTrainee is empty",
              "Unable to relocate form R to target trainee: targetTrainee is empty"))
          .body(null);
    }

    try {
      service.relocateFormR(formId, targetTrainee);
    } catch (ApplicationException e) {
      log.warn("Unable to relocate form R to target trainee. {}", e);
      return ResponseEntity.badRequest().headers(HeaderUtil
          .moveFailureAlert(e.toString(),
              "Unable to relocate form R to target trainee."+e)).body(null);
    }

    return ResponseEntity.noContent().build();
  }
}
