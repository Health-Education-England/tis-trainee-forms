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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.api.validation.FormRPartBValidator;
import uk.nhs.hee.tis.trainee.forms.dto.DeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = FormRPartBResource.class)
public class FormRPartBResourceTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";

  private static final String DEFAULT_TYPE_OF_WORK = "DEFAULT_TYPE_OF_WORK";
  private static final LocalDate DEFAULT_WORK_START_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final LocalDate DEFAULT_WORk_END_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final String DEFAULT_WORK_TRAINING_POST = "DEFAULT_WORK_TRAINING_POST";
  private static final String DEFAULT_WORK_SITE = "DEFAULT_WORK_SITE";
  private static final String DEFAULT_WORK_SITE_LOCATION = "DEFAULT_WORK_SITE_LOCATION";
  private static final Integer DEFAULT_TOTAL_LEAVE = 10;

  private static final Boolean DEFAULT_IS_HONEST = true;
  private static final Boolean DEFAULT_IS_HEALTHY = true;
  private static final String DEFAULT_HEALTHY_STATEMENT = "DEFAULT_HEALTHY_STATEMENT";

  private static final Boolean DEFAULT_HAVE_PREVIOUS_DECLARATIONS = true;
  private static final String DEFAULT_PREVIOUS_DECLARATION_TYPE = "Signification event";
  private static final LocalDate DEFAULT_PREVIOUS_DATE_OF_ENTRY = LocalDate
      .now(ZoneId.systemDefault());
  private static final String DEFAULT_PREVIOUS_DECLARATION_SUMMARY =
      "DEFAULT_PREVIOUS_DECLARATION_SUMMARY";

  private static final Boolean DEFAULT_HAVE_CURRENT_DECLARATIONS = true;
  private static final String DEFAULT_CURRENT_DECLARATION_TYPE = "Signification event";
  private static final LocalDate DEFAULT_CURRENT_DATE_OF_ENTRY = LocalDate
      .now(ZoneId.systemDefault());
  private static final String DEFAULT_CURRENT_DECLARATION_SUMMARY =
      "DEFAULT_CURRENT_DECLARATION_SUMMARY";
  private static final String DEFAULT_COMPLIMENTS = "DEFAULT_COMPLIMENTS";
  private static final LifecycleState DEFAULT_LIFECYLCESTATE = LifecycleState.DRAFT;

  @Autowired
  private MappingJackson2HttpMessageConverter jacksonMessageConverter;

  private MockMvc mockMvc;

  @MockBean
  private FormRPartBService formRPartBServiceMock;

  @MockBean
  private FormRPartBValidator formRPartBValidator;

  private FormRPartBDto formRPartBDto;
  private WorkDto workDto;
  private FormRPartSimpleDto formRPartSimpleDto;
  private DeclarationDto previousDeclarationDto;
  private DeclarationDto currentDeclarationDto;

  /**
   * setup the Mvc test environment.
   */
  @BeforeEach
  void setup() {
    FormRPartBResource formRPartBResource = new FormRPartBResource(formRPartBServiceMock,
        formRPartBValidator);
    mockMvc = MockMvcBuilders.standaloneSetup(formRPartBResource)
        .setMessageConverters(jacksonMessageConverter)
        .build();
  }

  /**
   * init test data.
   */
  @BeforeEach
  void initData() {
    setupWorkData();
    setupPreviousDeclarationData();
    setupCurrentDeclarationData();

    formRPartBDto = new FormRPartBDto();
    formRPartBDto.setId(DEFAULT_ID);
    formRPartBDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartBDto.setForename(DEFAULT_FORENAME);
    formRPartBDto.setSurname(DEFAULT_SURNAME);
    formRPartBDto.setWork(Lists.newArrayList(workDto));
    formRPartBDto.setTotalLeave(DEFAULT_TOTAL_LEAVE);
    formRPartBDto.setIsHonest(DEFAULT_IS_HONEST);
    formRPartBDto.setIsHealthy(DEFAULT_IS_HEALTHY);
    formRPartBDto.setHealthStatement(DEFAULT_HEALTHY_STATEMENT);
    formRPartBDto.setHavePreviousDeclarations(DEFAULT_HAVE_PREVIOUS_DECLARATIONS);
    formRPartBDto.setPreviousDeclarations(Lists.newArrayList(previousDeclarationDto));
    formRPartBDto.setPreviousDeclarationSummary(DEFAULT_PREVIOUS_DECLARATION_SUMMARY);
    formRPartBDto.setHaveCurrentDeclarations(DEFAULT_HAVE_CURRENT_DECLARATIONS);
    formRPartBDto.setCurrentDeclarations(Lists.newArrayList(currentDeclarationDto));
    formRPartBDto.setCurrentDeclarationSummary(DEFAULT_CURRENT_DECLARATION_SUMMARY);
    formRPartBDto.setCompliments(DEFAULT_COMPLIMENTS);
    formRPartBDto.setLifecycleState(DEFAULT_LIFECYLCESTATE);

    formRPartSimpleDto = new FormRPartSimpleDto();
    formRPartSimpleDto.setId(DEFAULT_ID);
    formRPartSimpleDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartSimpleDto.setLifecycleState(DEFAULT_LIFECYLCESTATE);
  }

  /**
   * Set up data for work.
   */
  void setupWorkData() {
    workDto = new WorkDto();
    workDto.setTypeOfWork(DEFAULT_TYPE_OF_WORK);
    workDto.setStartDate(DEFAULT_WORK_START_DATE);
    workDto.setEndDate(DEFAULT_WORk_END_DATE);
    workDto.setTrainingPost(DEFAULT_WORK_TRAINING_POST);
    workDto.setSite(DEFAULT_WORK_SITE);
    workDto.setSiteLocation(DEFAULT_WORK_SITE_LOCATION);
  }

  /**
   * Set up data for previous declaration.
   */
  void setupPreviousDeclarationData() {
    previousDeclarationDto = new DeclarationDto();
    previousDeclarationDto.setDeclarationType(DEFAULT_PREVIOUS_DECLARATION_TYPE);
    previousDeclarationDto.setDateOfEntry(DEFAULT_PREVIOUS_DATE_OF_ENTRY);
  }

  /**
   * Set up data for current declaration.
   */
  void setupCurrentDeclarationData() {
    currentDeclarationDto = new DeclarationDto();
    currentDeclarationDto.setDeclarationType(DEFAULT_CURRENT_DECLARATION_TYPE);
    currentDeclarationDto.setDateOfEntry(DEFAULT_CURRENT_DATE_OF_ENTRY);
  }

  @Test
  void testCreateNewFormRPartBWithExistingId() throws Exception {
    mockMvc.perform(post("/api/formr-partb")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartBDto)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testCreateNewFormRPartBShouldSucceed() throws Exception {
    formRPartBDto.setId(null);
    FormRPartBDto formRPartBDtoReturn = new FormRPartBDto();
    formRPartBDtoReturn.setId(DEFAULT_ID);
    formRPartBDtoReturn.setTraineeTisId(formRPartBDto.getTraineeTisId());
    formRPartBDtoReturn.setForename(formRPartBDto.getForename());
    formRPartBDtoReturn.setSurname(formRPartBDto.getSurname());
    formRPartBDtoReturn.setWork(formRPartBDto.getWork());
    formRPartBDtoReturn.setTotalLeave(formRPartBDto.getTotalLeave());
    formRPartBDtoReturn.setIsHonest(formRPartBDto.getIsHonest());
    formRPartBDtoReturn.setIsHealthy(formRPartBDto.getIsHealthy());
    formRPartBDtoReturn.setHealthStatement(formRPartBDto.getHealthStatement());
    formRPartBDtoReturn.setHavePreviousDeclarations(formRPartBDto.getHavePreviousDeclarations());
    formRPartBDtoReturn.setPreviousDeclarations(formRPartBDto.getPreviousDeclarations());
    formRPartBDtoReturn
        .setPreviousDeclarationSummary(formRPartBDto.getPreviousDeclarationSummary());
    formRPartBDtoReturn.setHaveCurrentDeclarations(formRPartBDto.getHaveCurrentDeclarations());
    formRPartBDtoReturn.setCurrentDeclarations(formRPartBDto.getCurrentDeclarations());
    formRPartBDtoReturn.setCurrentDeclarationSummary(formRPartBDto.getCurrentDeclarationSummary());
    formRPartBDtoReturn.setCompliments(formRPartBDto.getCompliments());

    when(formRPartBServiceMock.save(formRPartBDto)).thenReturn(formRPartBDtoReturn);
    mockMvc.perform(post("/api/formr-partb")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartBDto)))
        .andExpect(status().isCreated());
  }

  @Test
  void testCreateDraftFormRPartBWithDraftExists() throws Exception {
    formRPartBDto.setId(null);
    final String formRPartBDtoName = "FormRPartBDto";

    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(formRPartBDto, formRPartBDtoName);
    bindingResult.addError(new FieldError(formRPartBDtoName, "lifecycleState",
        "Draft form R Part B already exists"));

    Method method = formRPartBValidator.getClass().getMethods()[0];
    doThrow(new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult))
        .when(formRPartBValidator).validate(formRPartBDto);

    mockMvc.perform(post("/api/formr-partb")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartBDto)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void testUpdateFormRPartBShouldSucceed() throws Exception {
    formRPartBDto.setId(DEFAULT_ID);
    FormRPartBDto formRPartBDtoReturn = new FormRPartBDto();
    formRPartBDtoReturn.setId(DEFAULT_ID);
    formRPartBDtoReturn.setLifecycleState(LifecycleState.SUBMITTED);

    when(formRPartBServiceMock.save(formRPartBDto)).thenReturn(formRPartBDtoReturn);

    mockMvc.perform(put("/api/formr-partb")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartBDto)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.lifecycleState")
            .value(LifecycleState.SUBMITTED.name()));
  }

  @Test
  void testGetFormRPartBsByTraineeTisId() throws Exception {
    when(formRPartBServiceMock.getFormRPartBsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(
        Arrays.asList(formRPartSimpleDto));
    mockMvc.perform(get("/api/formr-partbs/" + DEFAULT_TRAINEE_TIS_ID)
        .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").value(hasSize(1)))
        .andExpect(jsonPath("$.[*].id").value(hasItem(DEFAULT_ID)))
        .andExpect(jsonPath("$.[*].lifecycleState").value(hasItem(DEFAULT_LIFECYLCESTATE.name())));
  }

  @Test
  void testGetFormRPartBById() throws Exception {
    when(formRPartBServiceMock.getFormRPartBById(DEFAULT_ID)).thenReturn(formRPartBDto);
    mockMvc.perform(get("/api/formr-partb/" + DEFAULT_ID)
        .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.work[*].typeOfWork").value(DEFAULT_TYPE_OF_WORK))
        .andExpect(jsonPath("$.totalLeave").value(DEFAULT_TOTAL_LEAVE))
        .andExpect(jsonPath("$.isHonest").value(DEFAULT_IS_HONEST))
        .andExpect(jsonPath("$.isHealthy").value(DEFAULT_IS_HEALTHY))
        .andExpect(jsonPath("$.healthStatement").value(DEFAULT_HEALTHY_STATEMENT))
        .andExpect(jsonPath("$.havePreviousDeclarations")
            .value(DEFAULT_HAVE_PREVIOUS_DECLARATIONS))
        .andExpect(jsonPath("$.previousDeclarations[*].declarationType")
            .value(DEFAULT_PREVIOUS_DECLARATION_TYPE))
        .andExpect(jsonPath("$.previousDeclarations[*].dateOfEntry")
            .value(DEFAULT_PREVIOUS_DATE_OF_ENTRY.toString()))
        .andExpect(jsonPath("$.previousDeclarationSummary")
            .value(DEFAULT_PREVIOUS_DECLARATION_SUMMARY))
        .andExpect(jsonPath("$.haveCurrentDeclarations")
            .value(DEFAULT_HAVE_CURRENT_DECLARATIONS))
        .andExpect(jsonPath("$.currentDeclarations[*].declarationType")
            .value(DEFAULT_CURRENT_DECLARATION_TYPE))
        .andExpect(jsonPath("$.currentDeclarations[*].dateOfEntry")
            .value(DEFAULT_CURRENT_DATE_OF_ENTRY.toString()))
        .andExpect(jsonPath("$.currentDeclarationSummary")
            .value(DEFAULT_CURRENT_DECLARATION_SUMMARY))
        .andExpect(jsonPath("$.compliments")
            .value(DEFAULT_COMPLIMENTS))
        .andExpect(jsonPath("$.lifecycleState")
            .value(DEFAULT_LIFECYLCESTATE.name()));
  }
}
