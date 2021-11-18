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

package uk.nhs.hee.tis.trainee.forms.service.exception;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * View Model for transferring error message with a list of field errors.
 */
@Getter
public class ErrorVM implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String message;
  private final String description;

  private final List<FieldErrorVM> fieldErrors = new ArrayList<>();

  public ErrorVM(String message) {
    this(message, null);
  }

  public ErrorVM(String message, String description) {
    this.message = message;
    this.description = description;
  }

  /**
   * Add a {@link FieldErrorVM} against a fields.
   *
   * @param objectName The name of the object containing the field
   * @param field      The name of field with the error
   * @param message    The error message
   */
  public void add(String objectName, String field, String message) {
    fieldErrors.add(new FieldErrorVM(objectName, field, message));
  }
}
