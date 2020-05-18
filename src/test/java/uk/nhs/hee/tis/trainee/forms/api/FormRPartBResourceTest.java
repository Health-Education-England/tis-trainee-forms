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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import org.assertj.core.util.Lists;
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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.model.Work;
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
  private static final String DEFAULT__WORK_SITE_LOCATION = "DEFAULT__WORK_SITE_LOCATION";
  private static final Integer DEFAULT_TOTAL_LEAVE = 10;

  @Autowired
  private MappingJackson2HttpMessageConverter jacksonMessageConverter;

  private MockMvc mockMvc;

  @MockBean
  private FormRPartBService formRPartBServiceMock;

  private FormRPartBDto formRPartBDto;
  private WorkDto workDto;

  /**
   * setup the Mvc test environment.
   */
  @BeforeEach
  public void setup() {
    FormRPartBResource formRPartBResource = new FormRPartBResource(formRPartBServiceMock);
    mockMvc = MockMvcBuilders.standaloneSetup(formRPartBResource)
        .setMessageConverters(jacksonMessageConverter)
        .build();
  }

  /**
   * init test data.
   */
  @BeforeEach
  public void initData() {
    setupWorkData();

    formRPartBDto = new FormRPartBDto();
    formRPartBDto.setId(DEFAULT_ID);
    formRPartBDto.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    formRPartBDto.setForename(DEFAULT_FORENAME);
    formRPartBDto.setSurname(DEFAULT_SURNAME);
    formRPartBDto.setWork(Lists.newArrayList(workDto));
    formRPartBDto.setTotalLeave(DEFAULT_TOTAL_LEAVE);
  }

  /**
   * Set up data for work.
   */
  public void setupWorkData() {
    workDto = new WorkDto();
    workDto.setTypeOfWork(DEFAULT_TYPE_OF_WORK);
    workDto.setStartDate(DEFAULT_WORK_START_DATE);
    workDto.setEndDate(DEFAULT_WORk_END_DATE);
    workDto.setTrainingPost(DEFAULT_WORK_TRAINING_POST);
    workDto.setSite(DEFAULT_WORK_SITE);
    workDto.setSiteLocation(DEFAULT__WORK_SITE_LOCATION);
  }

  @Test
  public void testCreateNewFormRPartBWithExistingId() throws Exception {
    this.mockMvc.perform(post("/api/formr-partb")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartBDto)))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testCreateNewFormRPartBShouldSucceed() throws Exception {
    formRPartBDto.setId(null);
    FormRPartBDto formRPartBDtoReturn = new FormRPartBDto();
    formRPartBDtoReturn.setId(DEFAULT_ID);
    formRPartBDtoReturn.setTraineeTisId(formRPartBDto.getTraineeTisId());
    formRPartBDtoReturn.setForename(formRPartBDto.getForename());
    formRPartBDtoReturn.setSurname(formRPartBDto.getSurname());
    formRPartBDtoReturn.setWork(formRPartBDto.getWork());
    formRPartBDtoReturn.setTotalLeave(formRPartBDto.getTotalLeave());

    when(formRPartBServiceMock.save(formRPartBDto)).thenReturn(formRPartBDtoReturn);
    this.mockMvc.perform(post("/api/formr-partb")
        .contentType(TestUtil.APPLICATION_JSON_UTF8)
        .content(TestUtil.convertObjectToJsonBytes(formRPartBDto)))
        .andExpect(status().isCreated());
  }

  @Test
  public void testGetFormRPartBsByTraineeTisId() throws Exception {
    when(formRPartBServiceMock.getFormRPartBsByTraineeTisId(DEFAULT_TRAINEE_TIS_ID)).thenReturn(
        Arrays.asList(formRPartBDto));
    this.mockMvc.perform(get("/api/formr-partb/" + DEFAULT_TRAINEE_TIS_ID)
        .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").value(hasSize(1)))
        .andExpect(jsonPath("$.[*].id").value(hasItem(DEFAULT_ID)))
        .andExpect(jsonPath("$.[*].work[*].typeOfWork").value(hasItem(DEFAULT_TYPE_OF_WORK)))
        .andExpect(jsonPath("$.[*].totalLeave").value(hasItem(DEFAULT_TOTAL_LEAVE)));
  }
}
