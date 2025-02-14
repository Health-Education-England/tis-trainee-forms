/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoiningPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.ProgrammeMembershipDto;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

/**
 * A controller for Conditions of Joining endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/coj")
@XRayEnabled
public class ConditionsOfJoiningResource {

  private final PdfService pdfService;
  private final TraineeIdentity traineeIdentity;

  /**
   * Initialise the Conditions of Joining resource.
   *
   * @param pdfService      The PDF service to use.
   * @param traineeIdentity The authenticated trainee user.
   */
  public ConditionsOfJoiningResource(PdfService pdfService, TraineeIdentity traineeIdentity) {
    this.pdfService = pdfService;
    this.traineeIdentity = traineeIdentity;
  }

  /**
   * Generate a Conditions of Joining PDF, unless one already exists.
   *
   * @param programmeMembership The programme membership to get/generate the PDF for.
   * @return The downloaded or generated PDF.
   * @throws IOException A new PDF could not be generated.
   */
  @PutMapping(produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> generatePdf(
      @Valid @RequestBody ProgrammeMembershipDto programmeMembership) throws IOException {

    String traineeId = traineeIdentity.getTraineeId();

    log.info("Trainee '{}' requesting Conditions of Joining PDF for PM '{}'.", traineeId,
        programmeMembership.tisId());
    String key = String.format("%s/forms/coj/%s.pdf", traineeId, programmeMembership.tisId());

    Resource publishedPdf = pdfService.getUploadedPdf(key).orElseGet(() -> {
      try {
        ConditionsOfJoiningPdfRequestDto request = new ConditionsOfJoiningPdfRequestDto(traineeId,
            programmeMembership.tisId(), programmeMembership.programmeName(),
            programmeMembership.conditionsOfJoining());
        return pdfService.generateConditionsOfJoining(request, false);
      } catch (IOException e) {
        return null;
      }
    });

    if (publishedPdf == null) {
      return ResponseEntity.unprocessableEntity().build();
    }

    return ResponseEntity.ok(publishedPdf.getContentAsByteArray());
  }
}
