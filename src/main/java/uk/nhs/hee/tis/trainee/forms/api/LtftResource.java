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
import com.fasterxml.jackson.annotation.JsonView;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.validation.Create;
import uk.nhs.hee.tis.trainee.forms.dto.validation.Update;
import uk.nhs.hee.tis.trainee.forms.dto.views.Trainee;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

/**
 * A controller for Less Than Full-time (LTFT) endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/ltft")
@XRayEnabled
@PreAuthorize("hasFeature('forms.ltft')")
public class LtftResource {

  private final LtftService service;
  private final PdfService pdfService;

  /**
   * Construct a REST controller for LTFT related endpoints.
   *
   * @param service A service providing LTFT functionality.
   */
  public LtftResource(LtftService service, PdfService pdfService) {
    this.service = service;
    this.pdfService = pdfService;
  }

  /**
   * Retrieve a list of LTFT summaries for the logged-in user.
   *
   * @return The list of LTFT summaries, or an empty list if none found.
   */
  @GetMapping
  @JsonView(Trainee.Read.class)
  @PreAuthorize("hasFeature('forms.ltft')")
  public ResponseEntity<List<LtftSummaryDto>> getLtftSummaries() {
    log.info("Request to get summary list of LTFT records.");
    List<LtftSummaryDto> ltfts = service.getLtftSummaries();
    return ResponseEntity.ok(ltfts);
  }

  /**
   * Get an existing LTFT form.
   *
   * @param formId The id of the LTFT form to retrieve.
   *
   * @return The DTO of the saved form.
   */
  @GetMapping("/{formId}")
  @JsonView(Trainee.Read.class)

  public ResponseEntity<LtftFormDto> getLtft(@PathVariable UUID formId) {
    log.info("Request to retrieve LTFT form {}.", formId);
    Optional<LtftFormDto> ltft = service.getLtftForm(formId);
    return ResponseEntity.of(ltft);
  }

  /**
   * Get a PDF of a LTFT form with a particular ID.
   *
   * @param formId The ID of the form.
   * @return The generated PDF
   */
  @GetMapping(value = "/{formId}", produces = MediaType.APPLICATION_PDF_VALUE)
  ResponseEntity<byte[]> getLtftPdf(@PathVariable UUID formId) {
    log.info("PDF requested by trainee for LTFT '{}'", formId);
    Optional<LtftFormDto> formDetail = service.getLtftForm(formId);

    if (formDetail.isPresent()) {
      LtftFormDto ltft = formDetail.get();

      try {
        byte[] pdf = pdfService.generatePdf(ltft, "trainee");
        return ResponseEntity.ok(pdf);
      } catch (IOException e) {
        return ResponseEntity.unprocessableEntity().build();
      }
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Save a new LTFT form.
   *
   * @param dto The DTO of the new LTFT form (which should not have an id).
   *
   * @return The DTO of the saved form (with an id).
   */
  @PostMapping
  @JsonView(Trainee.Read.class)
  public ResponseEntity<LtftFormDto> createLtft(
      @RequestBody @JsonView(Trainee.Write.class) @Validated(Create.class) LtftFormDto dto) {
    log.info("Request to save new LTFT form: {}", dto);
    Optional<LtftFormDto> savedLtft = service.createLtftForm(dto);
    return savedLtft.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.badRequest().build());
  }

  /**
   * Update an existing LTFT form.
   *
   * @param formId The id of the LTFT form to update.
   * @param dto    The DTO of the form.
   *
   * @return The DTO of the saved form.
   */
  @PutMapping("/{formId}")
  @JsonView(Trainee.Read.class)
  public ResponseEntity<LtftFormDto> updateLtft(@PathVariable UUID formId,
      @RequestBody @JsonView(Trainee.Write.class) @Validated(Update.class) LtftFormDto dto) {
    log.info("Request to update LTFT form {}: {}", formId, dto);
    Optional<LtftFormDto> savedLtft = service.updateLtftForm(formId, dto);
    return savedLtft.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.badRequest().build());
  }

  /**
   * Allow a trainee to submit an existing LTFT form.
   *
   * @param formId The id of the LTFT form to submit.
   *
   * @return The DTO of the submitted form, or a bad request if the form could not be submitted.
   */
  @PutMapping("/{formId}/submit")
  @JsonView(Trainee.Read.class)
  public ResponseEntity<LtftFormDto> submitLtft(@PathVariable UUID formId,
      @RequestBody LtftFormDto.StatusDto.LftfStatusInfoDetailDto reason) {
    log.info("Request to submit LTFT form {} with reason {}.", formId, reason);
    Optional<LtftFormDto> submittedLtft = service.submitLtftForm(formId, reason);
    return submittedLtft.map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.badRequest().build());
  }

  /**
   * Allow a trainee to unsubmit an existing LTFT form.
   *
   * @param formId The id of the LTFT form to unsubmit.
   *
   * @return The DTO of the unsubmitted form, or a bad request if the form could not be unsubmitted.
   */
  @PutMapping("/{formId}/unsubmit")
  @JsonView(Trainee.Read.class)
  public ResponseEntity<LtftFormDto> unsubmitLtft(@PathVariable UUID formId,
      @RequestBody LtftFormDto.StatusDto.LftfStatusInfoDetailDto reason) {
    log.info("Request to unsubmit LTFT form {} with reason {}.", formId, reason);
    Optional<LtftFormDto> unsubmittedLtft = service.unsubmitLtftForm(formId, reason);
    return unsubmittedLtft.map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.badRequest().build());
  }

  /**
   * Allow a trainee to withdraw an existing LTFT form.
   *
   * @param formId The id of the LTFT form to unsubmit.
   *
   * @return The DTO of the withdrawn form, or a bad request if the form could not be withdrawn.
   */
  @PutMapping("/{formId}/withdraw")
  @JsonView(Trainee.Read.class)
  public ResponseEntity<LtftFormDto> withdrawLtft(@PathVariable UUID formId,
      @RequestBody LtftFormDto.StatusDto.LftfStatusInfoDetailDto reason) {
    log.info("Request to withdraw LTFT form {} with reason {}.", formId, reason);
    Optional<LtftFormDto> withdrawnLtft = service.withdrawLtftForm(formId, reason);
    return withdrawnLtft.map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.badRequest().build());
  }

  /**
   * Delete an existing LTFT form.
   *
   * @param formId The id of the LTFT form to delete.
   * @return A response entity indicating the result of the deletion.
   */
  @DeleteMapping("/{formId}")
  public ResponseEntity<Void> deleteLtft(@PathVariable UUID formId) {
    log.info("Request to delete LTFT form {}.", formId);
    Optional<Boolean> deleted = service.deleteLtftForm(formId);
    if (deleted.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return deleted.get().equals(true)
        ? ResponseEntity.ok().build()
        : ResponseEntity.badRequest().build();
  }
}
