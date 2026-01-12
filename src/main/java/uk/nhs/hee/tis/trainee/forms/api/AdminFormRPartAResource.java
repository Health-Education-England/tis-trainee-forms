/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;

/**
 * A controller for admin facing Form-R Part A endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/formr-parta")
@XRayEnabled
public class AdminFormRPartAResource {

  private final FormRPartAService service;

  public AdminFormRPartAResource(FormRPartAService service) {
    this.service = service;
  }

  /**
   * PUT  Unsubmit a FormRPartA with the given ID.
   *
   * @param formId The ID of the form to unsubmit.
   * @return The unsubmitted form.
   */
  @PreAuthorize("""
      hasAnyRole(
        'HEE_Admin',
        'HEE_Admin_Revalidation',
        'HEE_Admin_Sensitive',
        'HEE_TIS_Admin')
      """)
  // TODO: review permission requirements.
  @PutMapping("/{formId}/unsubmit")
  public ResponseEntity<FormRPartADto> unsubmitFormRPartA(@PathVariable UUID formId) {
    log.info("Admin request to unsubmit FormRPartA with id {}", formId);
    Optional<FormRPartADto> unsubmitted = service.unsubmitFormRPartAById(formId);
    return ResponseEntity.of(unsubmitted);
  }

  /**
   * GET /formr-partas.
   *
   * @param traineeId The trainee ID RequestParam
   * @return list of the trainee's formR partA forms.
   */
  @GetMapping
  public ResponseEntity<List<FormRPartSimpleDto>> getTraineeFormRPartAs(
      @RequestParam String traineeId
  ) {
    log.info("FormRPartAs of trainee with id {}", traineeId);

    List<FormRPartSimpleDto> formRPartSimpleDtos = service.getFormRPartAs(traineeId);
    return ResponseEntity.ok(formRPartSimpleDtos);
  }

  /**
   * GET /formr-parta/:id.
   *
   * @param id The ID of the form
   * @return the formR partA
   */
  @GetMapping("/{id}")
  public ResponseEntity<FormRPartADto> getFormRPartAById(
      @PathVariable String id
  ) {
    log.info("Request for FormRPartA by id {}", id);

    FormRPartADto formRPartADto = service.getAdminsFormRPartAById(id);
    if (formRPartADto != null) {
      log.info("Retrieved FormRPartA id {} for trainee {} programme membership {}",
          id, formRPartADto.getTraineeTisId(), formRPartADto.getProgrammeMembershipId());
    }
    return ResponseEntity.of(Optional.ofNullable(formRPartADto));
  }

  /**
   * Delete a Form-R with the given ID.
   *
   * @param formId The ID of the form.
   * @return the status of the deletion.
   */
  @PreAuthorize("hasRole('TSS_Support_Admin')")
  @DeleteMapping("/{formId}")
  public ResponseEntity<FormRPartADto> deleteById(@PathVariable UUID formId) {
    log.info("Admin request to delete FormRPartA with id {}", formId);
    Optional<FormRPartADto> deleted = service.partialDeleteFormRPartAById(formId);
    return ResponseEntity.of(deleted);
  }
}
