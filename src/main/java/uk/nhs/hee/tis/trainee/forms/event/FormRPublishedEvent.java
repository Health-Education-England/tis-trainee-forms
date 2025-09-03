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

package uk.nhs.hee.tis.trainee.forms.event;

import uk.nhs.hee.tis.trainee.forms.dto.FormRPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.PublishedPdf;

/**
 * An event for when a FormR PartA PDF is published.
 */
abstract public class FormRPublishedEvent {
  String id;
  String traineeId;
  Object form; // To be defined in subclasses
  PublishedPdf pdf;

  /**
   * Create an event for when a FormR PartA PDF is published.
   *
   * @param request The FormR PartA PDF request which triggered this published event.
   * @param pdf     The reference to the published PDF.
   */
  public FormRPublishedEvent(FormRPdfRequestDto request, PublishedPdf pdf) {
    traineeId = request.getTraineeId();
    id = request.getId();
    form = request.getForm();
    this.pdf = pdf;
  }
}