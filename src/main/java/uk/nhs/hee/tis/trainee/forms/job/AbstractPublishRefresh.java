/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
 *
 */

package uk.nhs.hee.tis.trainee.forms.job;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * An abstract execution to publish all exportable forms as if they have been updated, useful for
 * refreshing downstream dependants after data discrepancies.
 *
 * @param <T> The type of the form to be refreshed.
 */
@Slf4j
public abstract class AbstractPublishRefresh<T> {

  /**
   * Get the type name of the form to be refreshed.
   *
   * @return The forms type name.
   */
  protected abstract String getFormTypeName();

  /**
   * Get the ID of a form.
   *
   * @param form The form to get the ID from.
   * @return The form's ID.
   */
  protected abstract UUID getFormId(T form);

  /**
   * Get the forms to be refreshed, should also apply any relevant filtering.
   *
   * @return The filtered list of forms to be refreshed.
   */
  protected abstract List<T> getForms();

  /**
   * Refresh the given form by publishing to an event topic.
   *
   * @param form The form to be refreshed.
   */
  protected abstract void publishForm(T form);

  /**
   * Execute the job to publish all exportable forms a refresh.
   *
   * @return The number of published forms.
   */
  protected Integer execute() {
    String formType = getFormTypeName();

    log.info("Starting {} downstream refresh.", formType);
    List<T> forms = getForms();

    int total = forms.size();
    log.info("Found {} {}s to refresh.", total, formType);

    int published = 0;

    for (T form : forms) {
      UUID formId = getFormId(form);
      log.debug("Publishing refresh notification for {} {}.", formType, formId);

      try {
        publishForm(form);
        published++;
      } catch (Exception e) {
        log.error("Unable to publish refresh notification for {} {}.", formType, formId);
      }
    }

    log.info("Finished {} downstream refresh, published count: {}/{}.", formType, published, total);
    return published;
  }
}
