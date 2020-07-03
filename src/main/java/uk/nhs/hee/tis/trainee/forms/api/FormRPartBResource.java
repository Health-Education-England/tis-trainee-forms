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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.api.util.HeaderUtil;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

@Slf4j
@RestController
@RequestMapping("/api")
public class FormRPartBResource {

  private static final String ENTITY_NAME = "formR_partB";

  private final FormRPartBService formRPartBService;

  public FormRPartBResource(FormRPartBService formRPartBService) {
    this.formRPartBService = formRPartBService;
  }

  /**
   * POST  /formr-partb : Create a new FormRPartB.
   *
   * @param formRPartBDto the formRPartBDto to create
   * @return the ResponseEntity with status 201 (Created) and with body the new formRPartBDto, or
   * with status 400 (Bad Request) if the formRPartB has already an ID
   * @throws URISyntaxException if the Location URI syntax is incorrect
   */
  @PostMapping("/formr-partb")
  public ResponseEntity<FormRPartBDto> createFormRPartB(
      @RequestBody FormRPartBDto formRPartBDto) throws URISyntaxException {
    log.debug("REST request to save FormRPartB : {}", formRPartBDto);
    if (formRPartBDto.getId() != null) {
      return ResponseEntity.badRequest().headers(HeaderUtil
          .createFailureAlert(ENTITY_NAME, "idexists",
              "A new formRpartB cannot already have an ID"))
          .body(null);
    }
    FormRPartBDto result = formRPartBService.save(formRPartBDto);
    return ResponseEntity.created(new URI("/api/formr-partb/" + result.getId()))
        .body(result);
  }

  /**
   * PUT /formr-partb : Update a FormRPartB.
   *
   * @param formRPartBDto the formRPartBDto to update
   * @return the ResponseEntity with status 200 and with body the new formRPartBDto, or with status
   * 500 (Internal Server Error) if the formRPartBDto couldn't be updated. If the id is not
   * provided, will create a new FormRPartB
   * @throws URISyntaxException
   */
  @PutMapping("/formr-partb")
  public ResponseEntity<FormRPartBDto> updateFormRPartB(
      @RequestBody FormRPartBDto formRPartBDto) throws URISyntaxException {
    log.debug("REST request to update FormRPartB : {}", formRPartBDto);
    if (formRPartBDto.getId() == null) {
      return createFormRPartB(formRPartBDto);
    }
    FormRPartBDto result = formRPartBService.save(formRPartBDto);
    return ResponseEntity.ok().body(result);
  }

  /**
   * GET /formr-partbs/:traineeTisId.
   *
   * @param traineeProfileId the id of trainee profile to get
   * @return list of formR partB based on the traineeTisId
   */
  @GetMapping("/formr-partbs/{traineeTisId}")
  public ResponseEntity<List<FormRPartSimpleDto>> getFormRPartBsByTraineeId(
      @PathVariable(name = "traineeTisId") String traineeProfileId) {
    // TODO: verify if the traineeId belongs to the trainee
    log.debug("FormR-PartB of a trainee by traineeTisId {}", traineeProfileId);
    List<FormRPartSimpleDto> formRPartBDtos = formRPartBService
        .getFormRPartBsByTraineeTisId(traineeProfileId);
    return ResponseEntity.ok(formRPartBDtos);
  }

  /**
   * GET /formr-partb/:id
   *
   * @param id The ID of the form
   * @return the formR partB based on the id
   */
  @GetMapping("/formr-partb/{id}")
  public ResponseEntity<FormRPartBDto> getFormRPartAsById(
      @PathVariable(name = "id") String id
  ) {
    // TODO: verify the formR PartA for the id belongs to the trainee
    log.debug("FormR-PartA by id {}", id);
    FormRPartBDto formRPartBDto = formRPartBService.getFormRPartBById(id);
    if (formRPartBDto != null) {
      return ResponseEntity.ok(formRPartBDto);
    } else {
      return new ResponseEntity(HttpStatus.NOT_FOUND);
    }
  }
}
