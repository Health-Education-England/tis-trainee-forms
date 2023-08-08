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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartAValidator;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;

@Slf4j
@RestController
@RequestMapping("/api")
@XRayEnabled
public class FormRPartAResource {

  private static final String ENTITY_NAME = "formR_partA";

  private final FormRPartAService service;
  private final FormRPartAValidator validator;

  public FormRPartAResource(FormRPartAService service, FormRPartAValidator validator) {
    this.service = service;
    this.validator = validator;
  }

  /**
   * POST  /formr-parta : Create a new FormRPartA.
   *
   * @param dto   the dto to create
   * @param token The authorization token from the request header.
   * @return the ResponseEntity with status 201 (Created) and with body the new dto, or with status
   * 400 (Bad Request) if the formRPartA has already an ID
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PostMapping("/formr-parta")
  public ResponseEntity<FormRPartADto> createFormRPartA(@RequestBody FormRPartADto dto,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token)
      throws URISyntaxException, MethodArgumentNotValidException {
    log.debug("REST request to save FormRPartA : {}", dto);
    if (dto.getId() != null) {
      return ResponseEntity.badRequest().headers(HeaderUtil
          .createFailureAlert(ENTITY_NAME, "idexists",
              "A new formRpartA cannot already have an ID")).body(null);
    }

    Optional<ResponseEntity<FormRPartADto>> responseEntity = AuthTokenUtil
        .verifyTraineeTisId(dto.getTraineeTisId(), token);
    if (responseEntity.isPresent()) {
      return responseEntity.get();
    }

    validator.validate(dto);
    FormRPartADto result = service.save(dto);
    return ResponseEntity.created(new URI("/api/formr-parta/" + result.getId())).body(result);
  }

  /**
   * PUT  /formr-parta : Update a FormRPartA.
   *
   * @param dto   the dto to update
   * @param token The authorization token from the request header.
   * @return the ResponseEntity with status 200 and with body the new dto, or with status 500
   * (Internal Server Error) if the formRPartBDto couldn't be updated. If the id is not provided,
   * will create a new FormRPartA
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PutMapping("/formr-parta")
  public ResponseEntity<FormRPartADto> updateFormRPartA(@RequestBody FormRPartADto dto,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token)
      throws URISyntaxException, MethodArgumentNotValidException {
    log.debug("REST request to update FormRPartA : {}", dto);
    if (dto.getId() == null) {
      return createFormRPartA(dto, token);
    }

    Optional<ResponseEntity<FormRPartADto>> errorResponse = AuthTokenUtil
        .verifyTraineeTisId(dto.getTraineeTisId(), token);
    if (errorResponse.isPresent()) {
      return errorResponse.get();
    }

    validator.validate(dto);
    FormRPartADto result = service.save(dto);
    return ResponseEntity.ok().body(result);
  }

  /**
   * GET /formr-partas.
   *
   * @param token The authorization token from the request header.
   * @return list of the trainee's formR partA forms.
   */
  @GetMapping("/formr-partas")
  public ResponseEntity<List<FormRPartSimpleDto>> getTraineeFormRPartAs(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.trace("FormR-PartAs of authenticated user.");
    String traineeTisId;

    try {
      traineeTisId = AuthTokenUtil.getTraineeTisId(token);
    } catch (IOException e) {
      log.warn("Unable to read tisId from token.", e);
      return ResponseEntity.badRequest().build();
    }

    List<FormRPartSimpleDto> formRPartSimpleDtos = service
        .getFormRPartAsByTraineeTisId(traineeTisId);
    return ResponseEntity.ok(formRPartSimpleDtos);
  }

  /**
   * GET /formr-parta/:id.
   *
   * @param id    The ID of the form
   * @param token The authorization token from the request header.
   * @return the formR partA based on the id
   */
  @GetMapping("/formr-parta/{id}")
  public ResponseEntity<FormRPartADto> getFormRPartAsById(@PathVariable String id,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.debug("FormR-PartA by id {}", id);

    String traineeTisId;
    try {
      traineeTisId = AuthTokenUtil.getTraineeTisId(token);
    } catch (IOException e) {
      log.warn("Unable to read tisId from token.", e);
      return ResponseEntity.badRequest().build();
    }

    FormRPartADto formRPartADto = service.getFormRPartAById(id, traineeTisId);
    return ResponseEntity.of(Optional.ofNullable(formRPartADto));
  }

  /**
   * DELETE: /formr-parta/:id.
   *
   * @param id    The ID of the form
   * @param token The authorization token from the request header.
   * @return the status of the deletion.
   */
  @DeleteMapping("/formr-parta/{id}")
  public ResponseEntity<Void> deleteFormRPartAById(@PathVariable String id,
      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) {
    log.info("Delete FormR-PartA by id {}", id);

    String traineeTisId;
    try {
      traineeTisId = AuthTokenUtil.getTraineeTisId(token);
    } catch (IOException e) {
      log.warn("Unable to read tisId from token.", e);
      return ResponseEntity.badRequest().build();
    }

    boolean deleted;

    try {
      deleted = service.deleteFormRPartAById(id, traineeTisId);
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
