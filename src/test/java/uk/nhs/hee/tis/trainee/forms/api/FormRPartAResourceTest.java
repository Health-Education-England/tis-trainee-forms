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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartSimpleDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = FormRPartAResource.class)
public class FormRPartAResourceTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final LifecycleState DEFAULT_LIFECYCLESTATE = LifecycleState.DRAFT;

  @Autowired
  private MappingJackson2HttpMessageConverter jacksonMessageConverter;

  private MockMvc mockMvc;

  @MockBean
  private FormRPartAService formRPartAServiceMock;

  private FormRPartADto formRPartADto;
  private FormRPartSimpleDto formRPartSimpleDto;

  /**
   * setup the Mvc test environment.
   */
  @BeforeEach
  public void setup() {
    FormRPartAResource formRPartAResource = new FormRPartAResource(formRPartAServiceMock);
    mockMvc = MockMvcBuilders.standaloneSetup(formRPartAResource)
        .setMessageConverters(jacksonMessageConverter)
        .build();
  }

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    formRPartADto = new FormRPartADto();
    formRPartADto.setId(DEFAULT_ID);
    formRPartADto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartADto.setForename(DEFAULT_FORENAME);
    formRPartADto.setSurname(DEFAULT_SURNAME);
    formRPartADto.setLifecycleState(DEFAULT_LIFECYCLESTATE);

    formRPartSimpleDto = new FormRPartSimpleDto();
    formRPartSimpleDto.setId(DEFAULT_ID);
    formRPartSimpleDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartSimpleDto.setLifecycleState(DEFAULT_LIFECYCLESTATE);
  }

  @Test
  public void testCreateNewFormRPartAWithExistingId() throws Exception {
    this.mockMvc.perform(post("/api/formr-parta")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartADto)))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateNewFormRPartAShouldSucceed() throws Exception {
    formRPartADto.setId(null);
    FormRPartADto formRPartADtoReturn = new FormRPartADto();
    formRPartADtoReturn.setId(DEFAULT_ID);
    formRPartADtoReturn.setTraineeTisId(formRPartADto.getTraineeTisId());
    formRPartADtoReturn.setForename(formRPartADto.getForename());
    formRPartADtoReturn.setSurname(formRPartADto.getSurname());

    when(formRPartAServiceMock.save(formRPartADto)).thenReturn(formRPartADtoReturn);
    this.mockMvc.perform(post("/api/formr-parta")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartADto)))
        .andExpect(status().isCreated());
  }

  @Test
  public void testUpdateFormRPartBShouldSucceed() throws Exception {
    formRPartADto.setId(DEFAULT_ID);
    FormRPartADto formRPartADtoReturn = new FormRPartADto();
    formRPartADtoReturn.setId(DEFAULT_ID);
    formRPartADtoReturn.setLifecycleState(LifecycleState.SUBMITTED);

    when(formRPartAServiceMock.save(formRPartADto)).thenReturn(formRPartADtoReturn);

    this.mockMvc.perform(put("/api/formr-parta")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartADto)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.lifecycleState")
            .value(LifecycleState.SUBMITTED.name()));
  }

  @Test
  public void testGetFormRPartAsByTraineeTisId() throws Exception {
    when(formRPartAServiceMock.getFormRPartAsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(
        Arrays.asList(formRPartSimpleDto));
    this.mockMvc.perform(get("/api/formr-partas/" + DEFAULT_TRAINEE_TIS_ID)
        .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").value(hasSize(1)))
        .andExpect(jsonPath("$.[*].id").value(hasItem(DEFAULT_ID)))
        .andExpect(jsonPath("$.[*].lifecycleState")
            .value(hasItem(DEFAULT_LIFECYCLESTATE.name())));
  }

  @Test
  public void testGetFormRPartBById() throws Exception {
    when(formRPartAServiceMock.getFormRPartAById(DEFAULT_ID)).thenReturn(formRPartADto);
    this.mockMvc.perform(get("/api/formr-parta/" + DEFAULT_ID)
        .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(DEFAULT_ID))
        .andExpect(jsonPath("$.traineeTisId").value(DEFAULT_TRAINEE_TIS_ID))
        .andExpect(jsonPath("$.forename").value(DEFAULT_FORENAME))
        .andExpect(jsonPath("$.surname").value(DEFAULT_SURNAME))
        .andExpect(jsonPath("$.lifecycleState")
            .value(DEFAULT_LIFECYCLESTATE.name()));
  }
}
