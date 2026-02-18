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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.TestJwtUtil;
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartAValidator;
import uk.nhs.hee.tis.trainee.forms.config.FilterConfiguration;
import uk.nhs.hee.tis.trainee.forms.config.MongoConfiguration;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartAPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

@WebMvcTest(FormRPartAResource.class)
@ComponentScan(basePackageClasses = FilterConfiguration.class)
class FormRPartAResourceTest {

  private static final String DEFAULT_ID = "4e41356d-77a6-4c23-b58b-c340c2ba4bf9";
  private static final String DEFAULT_TRAINEE_TIS_ID = "47165";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final LifecycleState DEFAULT_LIFECYCLESTATE = LifecycleState.DRAFT;

  private static final Jwt AUTH_TOKEN = TestJwtUtil.createTokenForTisId(DEFAULT_TRAINEE_TIS_ID);

  @TestConfiguration
  static class TestConfig {

    @Bean
    public MongoMappingContext mongoMappingContext() {
      return mock(MongoMappingContext.class);
    }
  }

  @MockitoBean
  private MongoConfiguration mongoConfiguration;

  @MockitoBean
  private LockProvider lockProvider;

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private FormRPartAService service;

  @MockitoBean
  private FormRPartAValidator validator;

  @MockitoBean
  PdfService pdfService;

  @MockitoBean
  private RestTemplateBuilder restTemplateBuilder;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private FormRPartADto dto;
  private FormRPartSimpleDto simpleDto;

  /**
   * init test data.
   */
  @BeforeEach
  void initData() {
    dto = new FormRPartADto();
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
    mockMvc.perform(post("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldNotCreateFormWhenFormExists() throws Exception {
    mockMvc.perform(post("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldNotCreateFormWhenDtoValidationFails() throws Exception {
    dto.setId(null);

    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dto, "formDto");
    bindingResult.addError(new FieldError("formDto", "formField", "Form field not valid."));

    Method method = validator.getClass().getMethod("validate", FormRPartADto.class);
    Exception exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
        bindingResult);
    doThrow(exception).when(validator).validate(dto);

    mockMvc.perform(post("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldNotCreateFormWhenNotForLoggedInTrainee() throws Exception {
    dto.setId(null);

    mockMvc.perform(post("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(TestJwtUtil.createTokenForTisId("another trainee id"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void postShouldCreateFormWhenDtoValidationPasses() throws Exception {
    dto.setId(null);
    FormRPartADto createdDto = new FormRPartADto();
    createdDto.setId(DEFAULT_ID);
    createdDto.setLifecycleState(LifecycleState.DRAFT);

    when(service.save(dto)).thenReturn(createdDto);

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string(HttpHeaders.LOCATION, "/api/formr-parta/" + DEFAULT_ID))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.lifecycleState").value(LifecycleState.DRAFT.name()));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EnumSource.Mode.EXCLUDE, names = {"SUBMITTED"})
  void postShouldCreateFormButNotPublishPdfIfNotSubmitted(LifecycleState state) throws Exception {
    dto.setId(null);
    FormRPartADto createdDto = new FormRPartADto();
    createdDto.setId(DEFAULT_ID);
    createdDto.setLifecycleState(state);

    when(service.save(dto)).thenReturn(createdDto);

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isCreated());
    verifyNoInteractions(pdfService);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED"})
  void postShouldCreateFormAndPublishPdfIfSubmitted(LifecycleState state) throws Exception {
    dto.setId(null);
    FormRPartADto createdDto = new FormRPartADto();
    createdDto.setId(DEFAULT_ID);
    createdDto.setLifecycleState(state);

    when(service.save(dto)).thenReturn(createdDto);

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isCreated());
    FormRPartAPdfRequestDto expectedRequest
        = new FormRPartAPdfRequestDto(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID, createdDto);
    verify(pdfService).generateFormRPartA(expectedRequest, true);
    verifyNoMoreInteractions(pdfService);
  }

  @Test
  void putShouldNotUpdateFormWhenNoToken() throws Exception {
    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void putShouldNotCreateFormWhenDtoValidationFails() throws Exception {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dto, "formDto");
    bindingResult.addError(new FieldError("formDto", "formField", "Form field not valid."));

    Method method = validator.getClass().getMethod("validate", FormRPartADto.class);
    Exception exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
        bindingResult);
    doThrow(exception).when(validator).validate(dto);

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void putShouldNotUpdateFormWhenNotForLoggedInTrainee() throws Exception {

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(TestJwtUtil.createTokenForTisId("another trainee id"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void putShouldUpdateFormWhenFormExists() throws Exception {
    FormRPartADto savedDto = new FormRPartADto();
    savedDto.setId(DEFAULT_ID);
    savedDto.setLifecycleState(LifecycleState.SUBMITTED);

    when(service.save(dto)).thenReturn(savedDto);

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.lifecycleState").value(LifecycleState.SUBMITTED.name()));
  }

  @Test
  void putShouldCreateFormWhenFormNotExists() throws Exception {
    dto.setId(null);
    FormRPartADto createdDto = new FormRPartADto();
    createdDto.setId(DEFAULT_ID);
    createdDto.setLifecycleState(LifecycleState.DRAFT);

    when(service.save(dto)).thenReturn(createdDto);

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string(HttpHeaders.LOCATION, "/api/formr-parta/" + DEFAULT_ID))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.lifecycleState").value(LifecycleState.DRAFT.name()));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EnumSource.Mode.EXCLUDE, names = {"SUBMITTED"})
  void putShouldUpdateFormButNotPublishPdfIfNotSubmitted(LifecycleState state) throws Exception {
    FormRPartADto savedDto = new FormRPartADto();
    savedDto.setId(DEFAULT_ID);
    savedDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    savedDto.setLifecycleState(state);

    when(service.save(dto)).thenReturn(savedDto);

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isOk());
    verifyNoInteractions(pdfService);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED"})
  void putShouldUpdateFormAndPublishSavedPdfIfSubmitted(LifecycleState state) throws Exception {
    FormRPartADto savedDto = new FormRPartADto();
    savedDto.setId(DEFAULT_ID);
    savedDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    savedDto.setLifecycleState(state);
    LocalDateTime savedTime = LocalDateTime.now();
    savedDto.setLastModifiedDate(savedTime);

    when(service.save(dto)).thenReturn(savedDto);

    mockMvc.perform(put("/api/formr-parta")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isOk());
    FormRPartAPdfRequestDto expectedRequest
        = new FormRPartAPdfRequestDto(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID, savedDto);
    verify(pdfService).generateFormRPartA(expectedRequest, true);
    verifyNoMoreInteractions(pdfService);
  }

  @Test
  void getShouldNotReturnTraineesFormsWhenNoToken() throws Exception {
    mockMvc.perform(get("/api/formr-partas")
            .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void getShouldNotReturnTraineesFormsWhenTokenHasNoTraineeId() throws Exception {
    mockMvc.perform(get("/api/formr-partas")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(jwt -> jwt.claim("claim", "value"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void getShouldReturnTraineesFormsWhenTokenHasTraineeId() throws Exception {
    when(service.getFormRPartAs()).thenReturn(Collections.singletonList(simpleDto));

    mockMvc.perform(get("/api/formr-partas")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").value(hasSize(1)))
        .andExpect(jsonPath("$.[0].id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.[0].traineeTisId").value(DEFAULT_TRAINEE_TIS_ID))
        .andExpect(jsonPath("$.[0].lifecycleState").value(DEFAULT_LIFECYCLESTATE.name()));
  }

  @Test
  void getByIdShouldNotReturnFormWhenNoToken() throws Exception {
    mockMvc.perform(get("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void getByIdShouldNotReturnFormWhenTokenHasNoTraineeId() throws Exception {
    mockMvc.perform(get("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(jwt -> jwt.claim("claim", "value"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void getByIdShouldNotReturnFormWhenFormIsNotTrainees() throws Exception {
    when(service.getFormRPartAById(DEFAULT_ID)).thenReturn(null);

    mockMvc.perform(get("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isNotFound());
  }

  @Test
  void getByIdShouldReturnFormWhenFormIsTrainees() throws Exception {
    when(service.getFormRPartAById(DEFAULT_ID)).thenReturn(dto);

    mockMvc.perform(get("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(AUTH_TOKEN)))
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
    mockMvc.perform(delete("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void deleteByIdShouldNotDeleteFormWhenTokenHasNoTraineeId() throws Exception {
    mockMvc.perform(delete("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(jwt -> jwt.claim("claim", "value"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void deleteByIdShouldReturnNotFoundWhenFormIsNotDeleted() throws Exception {
    when(service.deleteFormRPartAById(DEFAULT_ID)).thenReturn(false);

    mockMvc.perform(delete("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteByIdShouldReturnNoContentWhenFormIsDeleted() throws Exception {
    when(service.deleteFormRPartAById(DEFAULT_ID)).thenReturn(true);

    mockMvc.perform(delete("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteByIdShouldReturnBadRequestWhenIllegalFormId() throws Exception {
    doThrow(new IllegalArgumentException("error")).when(service).deleteFormRPartAById(DEFAULT_ID);

    mockMvc.perform(delete("/api/formr-parta/" + DEFAULT_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void putPdfShouldNotCreatePdfWhenNoToken() throws Exception {
    String formJson = getDefaultFormJson();

    mockMvc.perform(put("/api/formr-parta-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(formJson))
        .andExpect(status().isForbidden());

    verifyNoInteractions(pdfService);
  }

  @Test
  void putPdfShouldNotCreatePdfWhenTraineeIdNotInToken() throws Exception {
    String formJson = getDefaultFormJson();
    mockMvc.perform(put("/api/formr-parta-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(formJson)
            .with(jwt().jwt(jwt -> jwt.claim("claim", "value"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(pdfService);
  }

  @Test
  void putPdfShouldNotCreatePdfWhenDtoValidationFails() throws Exception {
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(dto, "formDto");
    bindingResult.addError(new FieldError("formDto", "formField", "Form field not valid."));

    Method method = validator.getClass().getMethod("validate", FormRPartADto.class);
    Exception exception = new MethodArgumentNotValidException(new MethodParameter(method, 0),
        bindingResult);
    doThrow(exception).when(validator).validate(dto);

    mockMvc.perform(put("/api/formr-parta-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(dto))
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(pdfService);
  }

  @Test
  void putPdfShouldReturnUnprocessableWhenPdfNull() throws Exception {
    String formJson = getDefaultFormJson();

    when(pdfService.getUploadedPdf(
        DEFAULT_TRAINEE_TIS_ID + "/forms/formr_parta/" + DEFAULT_ID + ".pdf"))
        .thenReturn(Optional.empty());
    when(pdfService.generateFormRPartA(any(), anyBoolean())).thenReturn(null);

    mockMvc.perform(put("/api/formr-parta-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(formJson)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void putPdfShouldReturnUnprocessableWhenIoException() throws Exception {
    String formJson = getDefaultFormJson();

    when(pdfService.getUploadedPdf(
        DEFAULT_TRAINEE_TIS_ID + "/forms/formr_parta/" + DEFAULT_ID + ".pdf"))
        .thenReturn(Optional.empty());
    doThrow(new IOException("Test exception"))
        .when(pdfService).generateFormRPartA(any(), anyBoolean());

    mockMvc.perform(put("/api/formr-parta-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(formJson)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void putPdfShouldReturnPreviousPdfWhenUploadedPdfExists() throws Exception {
    String formJson = getDefaultFormJson();

    byte[] response = "response content".getBytes();
    Resource resource = mock(Resource.class);
    when(resource.getContentAsByteArray()).thenReturn(response);
    when(pdfService.getUploadedPdf(
        DEFAULT_TRAINEE_TIS_ID + "/forms/formr_parta/" + DEFAULT_ID + ".pdf"))
        .thenReturn(Optional.of(resource));

    mockMvc.perform(put("/api/formr-parta-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(formJson)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(response));

    verify(pdfService, never()).generateFormRPartA(any(), anyBoolean());
  }

  @Test
  void putPdfShouldReturnGeneratePdfWhenUploadedPdfNotExists()
      throws Exception {
    String formJson = getDefaultFormJson();

    when(pdfService.getUploadedPdf(
        DEFAULT_TRAINEE_TIS_ID + "/forms/formr_parta/" + DEFAULT_ID + ".pdf"))
        .thenReturn(Optional.empty());

    byte[] response = "response content".getBytes();
    Resource resource = mock(Resource.class);
    when(resource.getContentAsByteArray()).thenReturn(response);
    when(pdfService.generateFormRPartA(any(), anyBoolean())).thenReturn(resource);

    mockMvc.perform(put("/api/formr-parta-pdf")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(formJson)
            .with(jwt().jwt(AUTH_TOKEN)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(response));
  }

  /**
   * Generate a default form JSON.
   *
   * @return the form JSON.
   */
  private String getDefaultFormJson() {
    try (Reader reader = new InputStreamReader(Objects.requireNonNull(
        getClass().getResourceAsStream("/forms/testFormRPartA.json")))) {
      return FileCopyUtils.copyToString(reader);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
