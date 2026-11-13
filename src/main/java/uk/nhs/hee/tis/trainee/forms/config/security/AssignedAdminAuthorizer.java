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
 */

package uk.nhs.hee.tis.trainee.forms.config.security;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

/**
 * Authorization checks for whether the requesting admin is assigned to an LTFT form.
 */
@Slf4j
@Component("assignedAdminAuthorizer")
public class AssignedAdminAuthorizer {

  private final AdminIdentity adminIdentity;
  private final LtftService ltftService;

  /**
   * Creates an instance of the authorizer.
   *
   * @param adminIdentity The identity of the requesting admin.
   * @param ltftService   The service for accessing LTFT forms.
   */
  public AssignedAdminAuthorizer(AdminIdentity adminIdentity, LtftService ltftService) {
    this.adminIdentity = adminIdentity;
    this.ltftService = ltftService;
  }

  /**
   * Whether the requesting admin is the admin assigned to the given form.
   *
   * @param formId The ID of the form.
   * @return True if the requester is the assigned admin of the form.
   * @throws ResponseStatusException if the form does not exist
   */
  public boolean isAssignedAdmin(UUID formId) {
    if (formId == null) {
      return false;
    }

    return ltftService.findFormForAdmin(formId)
        .map(this::isAssignedTo)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
            String.format("LTFT form with ID %s not found or not accessible to the admin.",
                formId)));
  }

  /**
   * Whether the requesting admin is the admin assigned to the given form.
   *
   * @param form The form to check.
   * @return True if the requester is the assigned admin of the form.
   */
  private boolean isAssignedTo(LtftForm form) {
    String requesterEmail = adminIdentity.getEmail();

    if (requesterEmail == null || requesterEmail.isBlank() || form.getStatus() == null
        || form.getStatus().current() == null) {
      return false;
    }

    Person assigned = form.getStatus().current().assignedAdmin();
    boolean allowed = assigned != null && requesterEmail.equalsIgnoreCase(assigned.email());

    if (!allowed) {
      log.warn("Admin is not the assigned admin of LTFT form {}.", form.getId());
    }

    return allowed;
  }
}
