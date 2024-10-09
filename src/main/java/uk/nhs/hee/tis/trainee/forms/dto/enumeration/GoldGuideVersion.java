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

package uk.nhs.hee.tis.trainee.forms.dto.enumeration;

import java.io.File;
import java.util.Set;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * An enumeration of supported Gold Guide versions.
 */
public enum GoldGuideVersion {

  GG9("gg9.html"),
  GG10("gg10.html");

  private static final String COJ_TEMPLATE_PATH = "conditions-of-joining";

  private final String templateFileName;

  GoldGuideVersion(String templateFileName) {
    this.templateFileName = templateFileName;
  }

  /**
   * Get the Conditions of Joining template spec for this version of the Gold Guide.
   *
   * @return The built template spec.
   */
  public TemplateSpec getConditionsOfJoiningTemplate() {
    String templatePath = COJ_TEMPLATE_PATH + File.separatorChar + templateFileName;
    return new TemplateSpec(templatePath, Set.of(), TemplateMode.HTML, null);
  }
}
