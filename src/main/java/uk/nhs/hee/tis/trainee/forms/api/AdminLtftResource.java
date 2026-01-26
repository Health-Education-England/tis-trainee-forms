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

import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.REJECTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;

import com.amazonaws.xray.spring.aop.XRayEnabled;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.Valid;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.dto.FormPatchDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonDto;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

/**
 * A controller for admin facing Less Than Full-time (LTFT) endpoints.
 */
@Slf4j
@RestController
@PreAuthorize("hasRole('NHSE_LTFT_Admin')")
@RequestMapping("/api/admin/ltft")
@XRayEnabled
public class AdminLtftResource {

  private final LtftService service;
  private final PdfService pdfService;
  private final ObjectMapper objectMapper;

  /**
   * Construct the controller.
   *
   * @param service      The LTFT service for accessing LTFT functionality.
   * @param pdfService   The PDF service for generating PDFs.
   * @param objectMapper The mapper for handling JSON conversion.
   */
  public AdminLtftResource(LtftService service, PdfService pdfService, ObjectMapper objectMapper) {
    this.service = service;
    this.pdfService = pdfService;
    this.objectMapper = objectMapper;
  }

  /**
   * Get a count of LTFT applications associated with the admin's local office.
   *
   * @param params The query parameters, will be passed as filters.
   * @return The number of LTFT applications found meeting the criteria.
   */
  @GetMapping("/count")
  public ResponseEntity<String> getAdminLtftCount(
      @RequestParam(required = false) Map<String, String> params) {
    long count = service.getAdminLtftCount(params);
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(String.valueOf(count));
  }

  /**
   * Get the LTFT application summaries associated with the admin's local office.
   *
   * @param pageable The desired paging and sorting details, defaults to all records sorted by
   *                 submission date.
   * @param params   The query parameters, will be passed as filters.
   * @return A page of LTFT summaries meeting the criteria.
   */
  @GetMapping
  ResponseEntity<PagedModel<LtftAdminSummaryDto>> getLtftAdminSummaries(
      @RequestParam(required = false) Map<String, String> params,
      @PageableDefault(size = Integer.MAX_VALUE, sort = "proposedStartDate",
          direction = Direction.ASC) Pageable pageable) {
    Page<LtftAdminSummaryDto> page = service.getAdminLtftSummaries(params, pageable);
    return ResponseEntity.ok(new PagedModel<>(page));
  }

  /**
   * Get the details of a form with a particular ID associated with the admin's local office.
   *
   * @param id The ID of the form.
   * @return The found form details, empty if not found.
   */
  @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<LtftFormDto> getLtftAdminDetail(@PathVariable UUID id) {
    Optional<LtftFormDto> formDetail = service.getAdminLtftDetail(id);
    return ResponseEntity.of(formDetail);
  }

  /**
   * Get a PDF of a form with a particular ID associated with the admin's local office.
   *
   * @param id The ID of the form.
   * @return The generated PDF
   */
  @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_PDF_VALUE)
  ResponseEntity<byte[]> getLtftAdminDetailPdf(@PathVariable UUID id) {
    log.info("PDF requested by admin for LTFT '{}'", id);
    Optional<LtftFormDto> formDetail = service.getAdminLtftDetail(id);

    if (formDetail.isPresent()) {
      LtftFormDto ltft = formDetail.get();

      try {
        byte[] pdf = pdfService.generatePdf(ltft, "admin");
        return ResponseEntity.ok(pdf);
      } catch (IOException e) {
        return ResponseEntity.unprocessableEntity().build();
      }
    }

    return ResponseEntity.notFound().build();
  }

  /**
   * Patch the contents of an LTFT with a particular ID associated with the admin's local office.
   *
   * @param id        The ID of the form.
   * @param formPatch The patch to apply.
   * @return The updated form DTO.
   * @throws MethodArgumentNotValidException If the patch was not valid.
   */
  @PatchMapping("/{id}")
  public ResponseEntity<LtftFormDto> patchLtft(@PathVariable UUID id,
      @Valid @RequestBody FormPatchDto formPatch)
      throws MethodArgumentNotValidException {
    log.info("LTFT patch requested by admin for LTFT '{}'", id);
    ArrayNode patchJson = objectMapper.convertValue(formPatch.patch(), ArrayNode.class);
    BeanPropertyBindingResult validationResult = new BeanPropertyBindingResult(formPatch,
        "formPatch");

    if (patchJson.isEmpty()) {
      validationResult.addError(
          new FieldError(FormPatchDto.class.getSimpleName(), "patch", "must not be empty"));
    }

    // TODO: validate patchability generically in a central place (e.g. annotations).
    Set<String> allowedPaths = Set.of("/change/startDate", "/change/wte");
    int opIndex = 0;

    for (Iterator<JsonNode> opIterator = patchJson.elements(); opIterator.hasNext(); opIndex++) {
      JsonNode operation = opIterator.next();
      String op = operation.get("op").asText();
      String path = operation.get("path").asText();

      if (!Objects.equals(op, "replace")) {
        log.warn("Unsupported patch operation provided.");
        validationResult.addError(
            new FieldError(FormPatchDto.class.getSimpleName(), "patch.%s.op".formatted(opIndex),
                "only 'replace' supported"));
      }

      if (!allowedPaths.contains(path)) {
        log.warn("Invalid patch path provided.");
        validationResult.addError(
            new FieldError(FormPatchDto.class.getSimpleName(), "patch.%s.path".formatted(opIndex),
                "user not authorized to update '%s'".formatted(path)));
      }
    }

    if (validationResult.hasErrors()) {
      Method method = this.getClass().getMethods()[0];
      throw new MethodArgumentNotValidException(new MethodParameter(method, 0), validationResult);
    }

    Optional<LtftFormDto> patchedForm = service.applyAdminPatch(id, formPatch);
    return ResponseEntity.of(patchedForm);
  }

  @PutMapping("/{id}/assign")
  ResponseEntity<LtftFormDto> assignAdmin(@PathVariable UUID id, @RequestBody PersonDto admin) {
    Optional<LtftFormDto> form = service.assignAdmin(id, admin);
    return ResponseEntity.of(form);
  }

  /**
   * Approve the form with the given ID, must be associated with the user's local office.
   *
   * @param id The ID of the form to approve.
   * @return The approved form.
   * @throws MethodArgumentNotValidException When the state transition was not valid.
   */
  @PutMapping("/{id}/approve")
  ResponseEntity<LtftFormDto> approveLtft(@PathVariable UUID id)
      throws MethodArgumentNotValidException {
    Optional<LtftFormDto> form = service.updateStatusAsAdmin(id, APPROVED, null);
    return ResponseEntity.of(form);
  }

  /**
   * Reject the form with the given ID, must be associated with the user's local office.
   *
   * @param id The ID of the form to unsubmit.
   * @return The rejected form.
   * @throws MethodArgumentNotValidException When the state transition was not valid.
   */
  @PutMapping("/{id}/reject")
  ResponseEntity<LtftFormDto> rejectLtft(@PathVariable UUID id,
      @RequestBody LftfStatusInfoDetailDto detail)
      throws MethodArgumentNotValidException {
    Optional<LtftFormDto> form = service.updateStatusAsAdmin(id, REJECTED, detail);
    return ResponseEntity.of(form);
  }

  /**
   * Unsubmit the form with the given ID, must be associated with the user's local office.
   *
   * @param id The ID of the form to unsubmit.
   * @return The unsubmitted form.
   * @throws MethodArgumentNotValidException When the state transition was not valid.
   */
  @PutMapping("/{id}/unsubmit")
  ResponseEntity<LtftFormDto> unsubmitLtft(@PathVariable UUID id,
      @RequestBody LftfStatusInfoDetailDto detail)
      throws MethodArgumentNotValidException {
    Optional<LtftFormDto> form = service.updateStatusAsAdmin(id, UNSUBMITTED, detail);
    return ResponseEntity.of(form);
  }
}
