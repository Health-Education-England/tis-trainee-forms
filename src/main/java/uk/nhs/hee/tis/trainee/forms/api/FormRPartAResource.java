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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.api.util.HeaderUtil;
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartAValidator;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;

@Slf4j
@RestController
@RequestMapping("/api")
public class FormRPartAResource {

  private static final String ENTITY_NAME = "formR_partA";

  private final FormRPartAService formRPartAService;
  private final FormRPartAValidator formRPartAValidator;

  public FormRPartAResource(FormRPartAService formRPartAService,
      FormRPartAValidator formRPartAValidator) {
    this.formRPartAService = formRPartAService;
    this.formRPartAValidator = formRPartAValidator;
  }

  /**
   * POST  /formr-parta : Create a new FormRPartA.
   *
   * @param formRPartADto the formRPartADto to create
   * @return the ResponseEntity with status 201 (Created) and with body the new formRPartADto, or
   * with status 400 (Bad Request) if the formRPartA has already an ID
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PostMapping("/formr-parta")
  public ResponseEntity<FormRPartADto> createFormRPartA(
      @RequestBody FormRPartADto formRPartADto)
      throws URISyntaxException, MethodArgumentNotValidException {
    log.debug("REST request to save FormRPartA : {}", formRPartADto);
    if (formRPartADto.getId() != null) {
      return ResponseEntity.badRequest().headers(HeaderUtil
          .createFailureAlert(ENTITY_NAME, "idexists",
              "A new formRpartA cannot already have an ID"))
          .body(null);
    }
    formRPartAValidator.validate(formRPartADto);
    FormRPartADto result = formRPartAService.save(formRPartADto);
    return ResponseEntity.created(new URI("/api/formr-parta/" + result.getId()))
        .body(result);
  }

  /**
   * PUT  /formr-parta : Update a FormRPartA.
   *
   * @param formRPartADto the formRPartADto to update
   * @return the ResponseEntity with status 200 and with body the new formRPartADto, or with status
   * 500 (Internal Server Error) if the formRPartBDto couldn't be updated. If the id is not
   * provided, will create a new FormRPartA
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PutMapping("/formr-parta")
  public ResponseEntity<FormRPartADto> updateFormRPartA(
      @RequestBody FormRPartADto formRPartADto)
      throws URISyntaxException, MethodArgumentNotValidException {
    log.debug("REST request to update FormRPartA : {}", formRPartADto);
    if (formRPartADto.getId() == null) {
      return createFormRPartA(formRPartADto);
    }
    formRPartAValidator.validate(formRPartADto);
    FormRPartADto result = formRPartAService.save(formRPartADto);
    return ResponseEntity.ok().body(result);
  }

  /**
   * GET /formr-partas/:traineeTisId.
   *
   * @param traineeProfileId The trainee ID to get the forms for.
   * @return list of formR partA based on the traineeTisId
   */
  @GetMapping("/formr-partas/{traineeTisId}")
  public ResponseEntity<List<FormRPartSimpleDto>> getFormRPartAsByTraineeId(
      @PathVariable(name = "traineeTisId") String traineeProfileId) {
    // TODO: verify if the traineeId belongs to the trainee
    log.debug("FormR-PartA of a trainee by traineeTisId {}", traineeProfileId);
    List<FormRPartSimpleDto> formRPartSimpleDtos = formRPartAService
        .getFormRPartAsByTraineeTisId(traineeProfileId);
    return ResponseEntity.ok(formRPartSimpleDtos);
  }

  /**
   * GET /formr-parta/:id
   *
   * @param id The ID of the form
   * @return the formR partA based on the id
   */
  @GetMapping("/formr-parta/{id}")
  public ResponseEntity<FormRPartADto> getFormRPartAsById(
      @PathVariable(name = "id") String id
  ) {
    // TODO: verify the formR PartA for the id belongs to the trainee
    log.debug("FormR-PartA by id {}", id);
    FormRPartADto formRPartADto = formRPartAService.getFormRPartAById(id);
    if (formRPartADto != null) {
      return ResponseEntity.ok(formRPartADto);
    } else {
      return new ResponseEntity(HttpStatus.NOT_FOUND);
    }
  }
}
