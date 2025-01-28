/*
 * The MIT License (MIT)
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.api.util.AuthTokenUtil;
import uk.nhs.hee.tis.trainee.forms.api.util.HeaderUtil;
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartBValidator;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

@Slf4j
@RestController
@RequestMapping("/api")
@XRayEnabled
public class FormRPartBResource {

  private static final String ENTITY_NAME = "formR_partB";

  private final FormRPartBService service;
  private final FormRPartBValidator validator;
  private final TraineeIdentity loggedInTraineeIdentity;

  /**
   * Initialise the FormR PartB resource.
   *
   * @param service         The service to use.
   * @param validator       The form validator to use.
   * @param traineeIdentity The authenticated trainee identity.
   */
  public FormRPartBResource(FormRPartBService service, FormRPartBValidator validator,
      TraineeIdentity traineeIdentity) {
    this.service = service;
    this.validator = validator;
    this.loggedInTraineeIdentity = traineeIdentity;
  }

  /**
   * POST  /formr-partb : Create a new FormRPartB.
   *
   * @param dto   the dto to create
   * @return the ResponseEntity with status 201 (Created) and with body the new dto, or with status
   * 400 (Bad Request) if the formRPartB has already an ID
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PostMapping("/formr-partb")
  public ResponseEntity<FormRPartBDto> createFormRPartB(@RequestBody FormRPartBDto dto)
      throws URISyntaxException, MethodArgumentNotValidException {
    log.debug("REST request to save FormRPartB : {}", dto);
    if (dto.getId() != null) {
      return ResponseEntity.badRequest().headers(HeaderUtil
          .createFailureAlert(ENTITY_NAME, "idexists",
              "A new formRpartB cannot already have an ID")).body(null);
    }

    if (!dto.getTraineeTisId().equals(loggedInTraineeIdentity.getTraineeId())) {
      log.warn("The form's trainee TIS ID did not match authenticated user.");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    validator.validate(dto);
    FormRPartBDto result = service.save(dto);
    return ResponseEntity.created(new URI("/api/formr-partb/" + result.getId())).body(result);
  }

  /**
   * PUT /formr-partb : Update a FormRPartB.
   *
   * @param dto   the dto to update
   * @return the ResponseEntity with status 200 and with body the new dto, or with status 500
   * (Internal Server Error) if the dto couldn't be updated. If the id is not provided, will create
   * a new FormRPartB
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PutMapping("/formr-partb")
  public ResponseEntity<FormRPartBDto> updateFormRPartB(@RequestBody FormRPartBDto dto)
      throws URISyntaxException, MethodArgumentNotValidException {
    log.debug("REST request to update FormRPartB : {}", dto);
    if (dto.getId() == null) {
      return createFormRPartB(dto);
    }

    if (!dto.getTraineeTisId().equals(loggedInTraineeIdentity.getTraineeId())) {
      log.warn("The form's trainee TIS ID did not match authenticated user.");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    validator.validate(dto);
    FormRPartBDto result = service.save(dto);
    return ResponseEntity.ok().body(result);
  }

  /**
   * GET /formr-partbs.
   *
   * @return list of the trainee's formR partB forms.
   */
  @GetMapping("/formr-partbs")
  public ResponseEntity<List<FormRPartSimpleDto>> getTraineeFormRPartBs() {
    log.trace("FormR-PartAs of authenticated user.");

    List<FormRPartSimpleDto> formRPartSimpleDtos = service.getFormRPartBs();
    return ResponseEntity.ok(formRPartSimpleDtos);
  }

  /**
   * GET /formr-partb/:id.
   *
   * @param id    The ID of the form
   * @return the formR partB based on the id
   */
  @GetMapping("/formr-partb/{id}")
  public ResponseEntity<FormRPartBDto> getFormRPartBsById(@PathVariable String id) {
    log.debug("FormR-PartB by id {}", id);

    FormRPartBDto formRPartBDto = service.getFormRPartBById(id);
    return ResponseEntity.of(Optional.ofNullable(formRPartBDto));
  }

  /**
   * DELETE: /formr-partb/:id.
   *
   * @param id    The ID of the form
   * @return the status of the deletion.
   */
  @DeleteMapping("/formr-partb/{id}")
  public ResponseEntity<Void> deleteFormRPartBById(@PathVariable String id) {
    log.info("Delete FormR-PartB by id {}", id);

    boolean deleted;

    try {
      deleted = service.deleteFormRPartBById(id);
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
}
