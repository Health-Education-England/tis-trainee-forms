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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartBValidator;
import uk.nhs.hee.tis.trainee.forms.config.InterceptorConfiguration;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

@Import(FormRPartBResource.class)
@ContextConfiguration(classes = InterceptorConfiguration.class)
@WebMvcTest(FormRPartBResource.class)
class FormRPartBResourceTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final LifecycleState DEFAULT_LIFECYCLESTATE = LifecycleState.DRAFT;

  private static final String AUTH_TOKEN
      = TestJwtUtil.generateTokenForTisId(DEFAULT_TRAINEE_TIS_ID);

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private FormRPartBService service;

  @MockBean
  private FormRPartBValidator validator;

  @MockBean
  private TraineeIdentity traineeIdentity;

  private FormRPartBDto dto;
  private FormRPartSimpleDto simpleDto;

  /**
   * init test data.
   */
  @BeforeEach
  void initData() {
    dto = new FormRPartBDto();
    dto.setId(DEFAULT_ID);
    dto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    dto.setForename(DEFAULT_FORENAME);
    dto.setSurname(DEFAULT_SURNAME);
    dto.setLifecycleState(DEFAULT_LIFECYCLESTATE);

    simpleDto = new FormRPartSimpleDto();
    simpleDto.setId(DEFAULT_ID);
    simpleDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    simpleDto.setLifecycleState(DEFAULT_LIFECYCLESTATE);
  }

  @Test
  void postShouldNotCreateFormWhenNoToken() throws Exception {
    dto.setId(null);
    mockMvc.perform(post("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldNotCreateFormWhenTokenIsInvalid() throws Exception {
    dto.setId(null);
    mockMvc.perform(post("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, "aa.bb.cc"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldNotCreateFormWhenFormExists() throws Exception {
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(post("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldNotCreateFormWhenDtoValidationFails() throws Exception {
    dto.setId(null);

    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dto, "formDto");
    bindingResult.addError(new FieldError("formDto", "formField", "Form field not valid."));

    Method method = validator.getClass().getMethod("validate", FormRPartBDto.class);
    Exception exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
        bindingResult);
    doThrow(exception).when(validator).validate(dto);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(post("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldNotCreateFormWhenNotForLoggedInTrainee() throws Exception {
    dto.setId(null);
    when(traineeIdentity.getTraineeId()).thenReturn("another trainee id");

    mockMvc.perform(post("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldCreateFormWhenDtoValidationPasses() throws Exception {
    dto.setId(null);
    FormRPartBDto createdDto = new FormRPartBDto();
    createdDto.setId(DEFAULT_ID);
    createdDto.setLifecycleState(LifecycleState.DRAFT);

    when(service.save(dto)).thenReturn(createdDto);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(put("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string(HttpHeaders.LOCATION, "/api/formr-partb/" + DEFAULT_ID))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.lifecycleState").value(LifecycleState.DRAFT.name()));
  }

  @Test
  void putShouldNotUpdateFormWhenNoToken() throws Exception {
    mockMvc.perform(put("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void putShouldNotUpdateFormWhenTokenIsInvalid() throws Exception {
    mockMvc.perform(put("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, "aa.bb.cc"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void putShouldNotCreateFormWhenDtoValidationFails() throws Exception {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dto, "formDto");
    bindingResult.addError(new FieldError("formDto", "formField", "Form field not valid."));

    Method method = validator.getClass().getMethod("validate", FormRPartBDto.class);
    Exception exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
        bindingResult);
    doThrow(exception).when(validator).validate(dto);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(put("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void putShouldNotUpdateFormWhenNotForLoggedInTrainee() throws Exception {
    when(traineeIdentity.getTraineeId()).thenReturn("another trainee id");

    mockMvc.perform(put("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void putShouldUpdateFormWhenFormExists() throws Exception {
    FormRPartBDto savedDto = new FormRPartBDto();
    savedDto.setId(DEFAULT_ID);
    savedDto.setLifecycleState(LifecycleState.SUBMITTED);

    when(service.save(dto)).thenReturn(savedDto);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(put("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.lifecycleState").value(LifecycleState.SUBMITTED.name()));
  }

  @Test
  void putShouldCreateFormWhenFormNotExists() throws Exception {
    dto.setId(null);
    FormRPartBDto createdDto = new FormRPartBDto();
    createdDto.setId(DEFAULT_ID);
    createdDto.setLifecycleState(LifecycleState.DRAFT);

    when(service.save(dto)).thenReturn(createdDto);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(put("/api/formr-partb")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string(HttpHeaders.LOCATION, "/api/formr-partb/" + DEFAULT_ID))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.lifecycleState").value(LifecycleState.DRAFT.name()));
  }

  @Test
  void getShouldNotReturnTraineesFormsWhenNoToken() throws Exception {
    mockMvc.perform(get("/api/formr-partbs")
            .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void getShouldNotReturnTraineesFormsWhenTokenHasNoTraineeId() throws Exception {
    mockMvc.perform(get("/api/formr-partbs")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .header(HttpHeaders.AUTHORIZATION, "aa.bb.cc"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void getShouldReturnTraineesFormsWhenTokenHasTraineeId() throws Exception {
    when(service.getFormRPartBs()).thenReturn(Collections.singletonList(simpleDto));
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(get("/api/formr-partbs")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").value(hasSize(1)))
        .andExpect(jsonPath("$.[0].id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.[0].traineeTisId").value(DEFAULT_TRAINEE_TIS_ID))
        .andExpect(jsonPath("$.[0].lifecycleState").value(DEFAULT_LIFECYCLESTATE.name()));
  }

  @Test
  void getByIdShouldNotReturnFormWhenNoToken() throws Exception {
    mockMvc.perform(get("/api/formr-partb/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void getByIdShouldNotReturnFormWhenTokenHasNoTraineeId() throws Exception {
    mockMvc.perform(get("/api/formr-partb/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .header(HttpHeaders.AUTHORIZATION, "aa.bb.cc"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void getByIdShouldNotReturnFormWhenFormIsNotTrainees() throws Exception {
    when(service.getFormRPartBById(DEFAULT_ID)).thenReturn(null);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(get("/api/formr-partb/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isNotFound());
  }

  @Test
  void getByIdShouldReturnFormWhenFormIsTrainees() throws Exception {
    when(service.getFormRPartBById(DEFAULT_ID)).thenReturn(dto);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(get("/api/formr-partb/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.traineeTisId").value(DEFAULT_TRAINEE_TIS_ID))
        .andExpect(jsonPath("$.forename").value(DEFAULT_FORENAME))
        .andExpect(jsonPath("$.surname").value(DEFAULT_SURNAME))
        .andExpect(jsonPath("$.lifecycleState").value(DEFAULT_LIFECYCLESTATE.name()));
  }

  @Test
  void deleteByIdShouldNotDeleteFormWhenNoToken() throws Exception {
    mockMvc.perform(delete("/api/formr-partb/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void deleteByIdShouldNotDeleteFormWhenTokenHasNoTraineeId() throws Exception {
    mockMvc.perform(delete("/api/formr-partb/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .header(HttpHeaders.AUTHORIZATION, "aa.bb.cc"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void deleteByIdShouldReturnNotFoundWhenFormIsNotDeleted() throws Exception {
    when(service.deleteFormRPartBById(DEFAULT_ID)).thenReturn(false);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(delete("/api/formr-partb/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteByIdShouldReturnNoContentWhenFormIsDeleted() throws Exception {
    when(service.deleteFormRPartBById(DEFAULT_ID)).thenReturn(true);
    when(traineeIdentity.getTraineeId()).thenReturn(DEFAULT_TRAINEE_TIS_ID);

    mockMvc.perform(delete("/api/formr-partb/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .header(HttpHeaders.AUTHORIZATION, AUTH_TOKEN))
        .andExpect(status().isNoContent());
  }
}
