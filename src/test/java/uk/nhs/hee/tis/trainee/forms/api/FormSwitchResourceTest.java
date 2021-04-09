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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.nhs.hee.tis.trainee.forms.dto.FormSwitchDto;
import uk.nhs.hee.tis.trainee.forms.service.FormSwitchService;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = FormSwitchResource.class)
class FormSwitchResourceTest {

  private static final String DEFAULT_ID = "DEFAULT_ID";
  private static final String DEFAULT_FORM_NAME = "DEFAULT_FORM_NAME";

  @Autowired
  private MappingJackson2HttpMessageConverter jacksonMessageConverter;

  private MockMvc mockMvc;

  @MockBean
  private FormSwitchService formSwitchServiceMock;

  private FormSwitchDto formSwitchDto;

  /**
   * setup the Mvc test environment.
   */
  @BeforeEach
  void setup() {
    FormSwitchResource formSwitchResource = new FormSwitchResource(formSwitchServiceMock);
    mockMvc = MockMvcBuilders.standaloneSetup(formSwitchResource)
        .setMessageConverters(jacksonMessageConverter)
        .build();
  }

  @BeforeEach
  void initData() {
    formSwitchDto = new FormSwitchDto();
    formSwitchDto.setId(DEFAULT_ID);
    formSwitchDto.setEnabled(true);
    formSwitchDto.setName(DEFAULT_FORM_NAME);
  }

  @Test
  void testGetFormSwitches() throws Exception {
    when(formSwitchServiceMock.getFormSwitches())
        .thenReturn(Collections.singletonList(formSwitchDto));
    mockMvc.perform(get("/api/form-switches")
        .contentType(TestUtil.APPLICATION_JSON_UTF8))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(hasSize(1)))
        .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_FORM_NAME)))
        .andExpect(jsonPath("$.[*].enabled").value(hasItem(true)));
  }
}
