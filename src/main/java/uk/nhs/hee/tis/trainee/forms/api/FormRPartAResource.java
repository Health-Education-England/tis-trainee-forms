/*
 * The MIT License (MIT)
 *
 * Copyright 2020 Crown Copyright (Health Education England)
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
import io.awspring.cloud.s3.Location;
import io.awspring.cloud.s3.S3Resource;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.api.util.HeaderUtil;
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartAValidator;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoiningPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartAPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.PublishedPdf;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.event.FormRPartAPublishedEvent;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

@Slf4j
@RestController
@RequestMapping("/api")
@XRayEnabled
public class FormRPartAResource {

  private static final String ENTITY_NAME = "formR_partA";

  private final FormRPartAService service;
  private final FormRPartAValidator validator;
  private final TraineeIdentity loggedInTraineeIdentity;
  private final PdfService pdfService;

  /**
   * Initialise the FormR PartA resource.
   *
   * @param service         The service to use.
   * @param validator       The form validator to use.
   * @param traineeIdentity The authenticated trainee identity.
   * @param pdfService      The PDF service to use.
   */
  public FormRPartAResource(FormRPartAService service, FormRPartAValidator validator,
      TraineeIdentity traineeIdentity, PdfService pdfService) {
    this.service = service;
    this.validator = validator;
    this.loggedInTraineeIdentity = traineeIdentity;
    this.pdfService = pdfService;
  }

  /**
   * POST  /formr-parta : Create a new FormRPartA.
   *
   * @param dto   the dto to create
   * @return the ResponseEntity with status 201 (Created) and with body the new dto, or with status
   * 400 (Bad Request) if the formRPartA has already an ID
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PostMapping("/formr-parta")
  public ResponseEntity<FormRPartADto> createFormRPartA(@RequestBody FormRPartADto dto)
      throws URISyntaxException, MethodArgumentNotValidException {
    log.info("REST request to save FormRPartA : {}", dto);
    if (dto.getId() != null) {
      return ResponseEntity.badRequest().headers(HeaderUtil
          .createFailureAlert(ENTITY_NAME, "idexists",
              "A new FormRPartA cannot already have an ID")).body(null);
    }

    if (!dto.getTraineeTisId().equals(loggedInTraineeIdentity.getTraineeId())) {
      log.warn("The form's trainee TIS ID did not match authenticated user.");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    validator.validate(dto);
    FormRPartADto result = service.save(dto);
    return ResponseEntity.created(new URI("/api/formr-parta/" + result.getId())).body(result);
  }

  /**
   * PUT  /formr-parta : Update a FormRPartA.
   *
   * @param dto   the dto to update
   * @return the ResponseEntity with status 200 and with body the new dto, or with status 500
   * (Internal Server Error) if the formRPartADto couldn't be updated. If the id is not provided,
   * will create a new FormRPartA
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PutMapping("/formr-parta")
  public ResponseEntity<FormRPartADto> updateFormRPartA(@RequestBody FormRPartADto dto)
      throws URISyntaxException, MethodArgumentNotValidException, IOException {
    log.info("REST request to update FormRPartA : {}", dto);
    if (dto.getId() == null) {
      return createFormRPartA(dto);
      //TODO: could be submitted so might need to publish PDF here as well
    }

    if (!dto.getTraineeTisId().equals(loggedInTraineeIdentity.getTraineeId())) {
      log.warn("The form's trainee TIS ID did not match authenticated user.");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    validator.validate(dto);
    FormRPartADto result = service.save(dto);

    if (dto.getLifecycleState() == LifecycleState.SUBMITTED) {
      String traineeId = loggedInTraineeIdentity.getTraineeId();
      publishSubmittedFormPdf(traineeId, dto);
    }
    return ResponseEntity.ok().body(result);
  }

  /**
   * GET /formr-partas.
   *
   * @return list of the trainee's formR partA forms.
   */
  @GetMapping("/formr-partas")
  public ResponseEntity<List<FormRPartSimpleDto>> getTraineeFormRPartAs() {
    log.trace("FormRPartAs of authenticated user.");

    List<FormRPartSimpleDto> formRPartSimpleDtos = service.getFormRPartAs();
    return ResponseEntity.ok(formRPartSimpleDtos);
  }

  /**
   * GET /formr-parta/:id.
   *
   * @param id    The ID of the form
   * @return the formR partA based on the id
   */
  @GetMapping("/formr-parta/{id}")
  public ResponseEntity<FormRPartADto> getFormRPartAsById(@PathVariable String id) {
    log.info("FormRPartA by id {}", id);

    FormRPartADto formRPartADto = service.getFormRPartAById(id);
    if (formRPartADto != null) {
      log.info("Retrieved FormRPartA id {} for trainee {} programme membership {}",
          id, formRPartADto.getTraineeTisId(), formRPartADto.getProgrammeMembershipId());
    }
    return ResponseEntity.of(Optional.ofNullable(formRPartADto));
  }

  /**
   * DELETE: /formr-parta/:id.
   *
   * @param id    The ID of the form
   * @return the status of the deletion.
   */
  @DeleteMapping("/formr-parta/{id}")
  public ResponseEntity<Void> deleteFormRPartAById(@PathVariable String id) {
    log.info("Delete FormRPartA by id {}", id);

    boolean deleted;

    try {
      deleted = service.deleteFormRPartAById(id);
    } catch (IllegalArgumentException e) {
      log.error(e.getMessage());
      return ResponseEntity.badRequest().build();
    }

    if (deleted) {
      return ResponseEntity.noContent().build();
    } else {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Generate a FormR PartA PDF, unless one already exists.
   *
   * @return The downloaded or generated PDF.
   * @throws IOException A new PDF could not be generated.
   */
  @PutMapping(value = "/formr-parta-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> generatePdf(@Valid @RequestBody FormRPartADto formRPartA)
      throws IOException {

    String traineeId = loggedInTraineeIdentity.getTraineeId();
    String formId = formRPartA.getId();

    log.info("Trainee '{}' requesting FormR PartA PDF for form '{}'.", traineeId, formId);
    String key = String.format("%s/forms/formr_parta/%s.pdf", traineeId, formId);

    Resource publishedPdf = pdfService.getUploadedPdf(key).orElseGet(() -> {
      try {
        FormRPartAPdfRequestDto request = new FormRPartAPdfRequestDto(formId, traineeId,
            formRPartA);
        return pdfService.generateFormRPartA(request, false);
      } catch (IOException e) {
        return null;
      }
    });

    if (publishedPdf == null) {
      return ResponseEntity.unprocessableEntity().build();
    }

    return ResponseEntity.ok(publishedPdf.getContentAsByteArray());
  }

  private void publishSubmittedFormPdf(String traineeId, FormRPartADto formRPartA)
      throws IOException {
    String formId = formRPartA.getId();

    log.info("Publishing submitted FormR PartA PDF for trainee '{}' form '{}'.", traineeId, formId);
    String key = String.format("%s/forms/formr_parta/%s.pdf", traineeId, formId);

    Optional<Resource> savedPdf = pdfService.getUploadedPdf(key);
    if (savedPdf.isEmpty()) {
      FormRPartAPdfRequestDto request = new FormRPartAPdfRequestDto(formId, traineeId, formRPartA);
      pdfService.generateFormRPartA(request, true);
    } else {
      //TODO: can you overwrite an S3 object like this? Do we need to (submit-unsubmit-submit again)?
      FormRPartAPdfRequestDto request = new FormRPartAPdfRequestDto(formId, traineeId, formRPartA);
      pdfService.generateFormRPartA(request, true);
    }
  }
}
