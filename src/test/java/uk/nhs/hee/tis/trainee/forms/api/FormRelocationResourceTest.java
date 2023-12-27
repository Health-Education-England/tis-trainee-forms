/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.nhs.hee.tis.trainee.forms.service.FormRelocateService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = FormRelocateResource.class)
class FormRelocationResourceTest {

  private static final String FORM_ID = "FORM_ID";
  private static final String TARGET_TRAINEE = "TARGET_TRAINEE";

  private MockMvc mockMvc;

  @MockBean
  private FormRelocateService service;

  /**
   * setup the Mvc test environment.
   */
  @BeforeEach
  void setup() {
    FormRelocateResource formRelocateResource = new FormRelocateResource(service);
    mockMvc = MockMvcBuilders.standaloneSetup(formRelocateResource)
        .build();
  }

  @Test
  void shouldRelocateForm() throws Exception {
    mockMvc.perform(patch("/api/form-relocate/" + FORM_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .param("targetTrainee",TARGET_TRAINEE))
            .andExpect(status().isNoContent());
  }

  @Test
  void shouldNotRelocateFormWhenNoTargetTrainee() throws Exception {
    mockMvc.perform(patch("/api/form-relocate/" + FORM_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .param("targetTrainee",""))
            .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void shouldNotRelocateFormWhenExceptionOccurs() throws Exception {
    ApplicationException applicationException = new ApplicationException("");
    doThrow(applicationException).when(service).relocateFormR(FORM_ID, TARGET_TRAINEE);

    mockMvc.perform(patch("/api/form-relocate/" + FORM_ID)
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .param("targetTrainee",TARGET_TRAINEE))
            .andExpect(status().isBadRequest());
  }
}
