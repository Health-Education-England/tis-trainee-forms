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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.service.LtftService;

class AssignedAdminAuthorizerTest {

  private static final UUID FORM_ID = UUID.randomUUID();
  private static final String ASSIGNED_ADMIN_EMAIL = "assigned.admin@example.com";

  private AssignedAdminAuthorizer authorizer;

  private LtftService service;

  @BeforeEach
  void setUp() {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setEmail(ASSIGNED_ADMIN_EMAIL);

    service = mock(LtftService.class);
    authorizer = new AssignedAdminAuthorizer(adminIdentity, service);
  }

  @Test
  void shouldReturnFalseWhenNoFormId() {
    boolean isAssignedAdmin = authorizer.isAssignedAdmin(null);

    assertThat("Unexpected authorizer result.", isAssignedAdmin, is(false));
  }

  @Test
  void shouldThrowWhenFormNotFound() {
    when(service.findFormForAdmin(FORM_ID)).thenReturn(Optional.empty());

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> authorizer.isAssignedAdmin(FORM_ID));

    assertThat("Unexpected exception status.", exception.getStatusCode(), is(NOT_FOUND));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldReturnFalseWhenNoRequesterEmail(String requesterEmail) {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setEmail(requesterEmail);
    AssignedAdminAuthorizer authorizerNoEmail = new AssignedAdminAuthorizer(adminIdentity,
        service);

    Person admin = Person.builder().email(ASSIGNED_ADMIN_EMAIL).build();
    LtftForm form = new LtftForm();
    form.setAssignedAdmin(admin, admin);
    when(service.findFormForAdmin(FORM_ID)).thenReturn(Optional.of(form));

    boolean isAssignedAdmin = authorizerNoEmail.isAssignedAdmin(FORM_ID);

    assertThat("Unexpected authorizer result.", isAssignedAdmin, is(false));
  }

  @Test
  void shouldReturnFalseWhenNoFormStatus() {
    LtftForm form = new LtftForm();
    form.setStatus(null);
    when(service.findFormForAdmin(FORM_ID)).thenReturn(Optional.of(form));

    boolean isAssignedAdmin = authorizer.isAssignedAdmin(FORM_ID);

    assertThat("Unexpected authorizer result.", isAssignedAdmin, is(false));
  }

  @Test
  void shouldReturnFalseWhenNoCurrentFormStatus() {
    LtftForm form = new LtftForm();
    form.setStatus(Status.builder().current(null).build());
    when(service.findFormForAdmin(FORM_ID)).thenReturn(Optional.of(form));

    boolean isAssignedAdmin = authorizer.isAssignedAdmin(FORM_ID);

    assertThat("Unexpected authorizer result.", isAssignedAdmin, is(false));
  }

  @Test
  void shouldReturnFalseWhenNoAssignedAdmin() {
    LtftForm form = new LtftForm();
    form.setStatus(Status.builder().current(
        StatusInfo.builder()
            .assignedAdmin(null)
            .build()
    ).build());
    when(service.findFormForAdmin(FORM_ID)).thenReturn(Optional.of(form));

    boolean isAssignedAdmin = authorizer.isAssignedAdmin(FORM_ID);

    assertThat("Unexpected authorizer result.", isAssignedAdmin, is(false));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "no.assigned@example.com")
  void shouldReturnFalseWhenRequestIsNotAssigned(String assignedEmail) {
    Person admin = Person.builder().email(assignedEmail).build();
    LtftForm form = new LtftForm();
    form.setStatus(Status.builder().current(
        StatusInfo.builder()
            .assignedAdmin(admin)
            .build()
    ).build());
    when(service.findFormForAdmin(FORM_ID)).thenReturn(Optional.of(form));

    boolean isAssignedAdmin = authorizer.isAssignedAdmin(FORM_ID);

    assertThat("Unexpected authorizer result.", isAssignedAdmin, is(false));
  }

  @Test
  void shouldReturnTrueWhenRequestIsAssigned() {
    Person admin = Person.builder().email(ASSIGNED_ADMIN_EMAIL).build();
    LtftForm form = new LtftForm();
    form.setStatus(Status.builder().current(
        StatusInfo.builder()
            .assignedAdmin(admin)
            .build()
    ).build());
    when(service.findFormForAdmin(FORM_ID)).thenReturn(Optional.of(form));

    boolean isAssignedAdmin = authorizer.isAssignedAdmin(FORM_ID);

    assertThat("Unexpected authorizer result.", isAssignedAdmin, is(true));
  }
}
