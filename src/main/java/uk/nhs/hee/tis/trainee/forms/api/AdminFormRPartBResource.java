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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

/**
 * A controller for admin facing Form-R Part B endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/formr-partb")
@XRayEnabled
public class AdminFormRPartBResource {

  private final FormRPartBService service;

  public AdminFormRPartBResource(FormRPartBService service) {
    this.service = service;
  }

  /**
   * PUT  Unsubmit a FormRPartB with the given ID.
   *
   * @param formId The ID of the form to unsubmit.
   * @return The unsubmitted form.
   */
  // TODO: review permission requirements.
  @PreAuthorize("""
      hasAnyRole(
        'HEE_Admin',
        'HEE_Admin_Revalidation',
        'HEE_Admin_Sensitive',
        'HEE_TIS_Admin')
      """)
  @PutMapping("/{formId}/unsubmit")
  public ResponseEntity<FormRPartBDto> unsubmitFormRPartB(@PathVariable UUID formId) {
    log.info("Admin request to unsubmit FormRPartB with id {}", formId);
    Optional<FormRPartBDto> unsubmitted = service.unsubmitFormRPartBById(formId);
    return ResponseEntity.of(unsubmitted);
  }

  /**
   * GET /formr-partbs.
   *
   * @param traineeId The trainee ID RequestParam
   * @return list of the trainee's formR partB forms.
   */
  @GetMapping
  public ResponseEntity<List<FormRPartSimpleDto>> getTraineeFormRPartBs(
      @RequestParam String traineeId
  ) {
    log.info("FormRPartBs of trainee with id {}", traineeId);

    List<FormRPartSimpleDto> formRPartSimpleDtos = service.getFormRPartBs(traineeId);
    return ResponseEntity.ok(formRPartSimpleDtos);
  }

  /**
   * GET /formr-partb/:id.
   *
   * @param id        The ID of the form
   * @return the formR partB
   */
  @GetMapping("/{id}")
  public ResponseEntity<FormRPartBDto> getFormRPartBsById(
      @PathVariable String id
  ) {
    log.info("FormRPartB by id {}", id);

    FormRPartBDto formRPartBDto = service.getAdminsFormRPartBById(id);
    if (formRPartBDto != null) {
      log.info("Retrieved FormRPartB id {} for trainee {} programme membership {}",
          id, formRPartBDto.getTraineeTisId(), formRPartBDto.getProgrammeMembershipId());
    }
    return ResponseEntity.of(Optional.ofNullable(formRPartBDto));
  }

  /**
   * Delete a Form-R with the given ID.
   *
   * @param formId The ID of the form.
   * @return the status of the deletion.
   */
  @PreAuthorize("hasRole('TSS_Support_Admin')")
  @DeleteMapping("/{formId}")
  public ResponseEntity<FormRPartBDto> deleteById(@PathVariable UUID formId) {
    log.info("Admin request to delete FormRPartB with id {}", formId);
    Optional<FormRPartBDto> deleted = service.partialDeleteFormRPartBById(formId);
    return ResponseEntity.of(deleted);
  }
}
