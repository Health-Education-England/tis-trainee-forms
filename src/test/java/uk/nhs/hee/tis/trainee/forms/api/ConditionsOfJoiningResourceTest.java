/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoining;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoiningPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.GoldGuideVersion;
import uk.nhs.hee.tis.trainee.forms.service.PdfService;

@WebMvcTest(controllers = ConditionsOfJoiningResource.class)
@AutoConfigureMockMvc
class ConditionsOfJoiningResourceTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private PdfService service;

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void putShouldReturnBadRequestGeneratingCojPdfWhenNoTraineeId(GoldGuideVersion version)
      throws Exception {
    UUID programmeMembershipId = UUID.randomUUID();
    Instant signedAt = Instant.now();

    String body = """
        {
          "programmeMembershipId": "%s",
          "programmeName": "Test Programme",
          "conditionsOfJoining": {
            "version": "%s",
            "signedAt": "%s"
          }
        }
        """.formatted(programmeMembershipId, version, signedAt);

    mockMvc.perform(put("/api/coj")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void putShouldReturnBadRequestGeneratingCojPdfWhenNoPmId(GoldGuideVersion version)
      throws Exception {
    Instant signedAt = Instant.now();

    String body = """
        {
          "traineeId": "40",
          "programmeName": "Test Programme",
          "conditionsOfJoining": {
            "version": "%s",
            "signedAt": "%s"
          }
        }
        """.formatted(version, signedAt);

    mockMvc.perform(put("/api/coj")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void putShouldReturnBadRequestGeneratingCojPdfWhenNoProgrammeName(GoldGuideVersion version)
      throws Exception {
    UUID programmeMembershipId = UUID.randomUUID();
    Instant signedAt = Instant.now();

    String body = """
        {
          "traineeId": "40",
          "programmeMembershipId": "%s",
          "conditionsOfJoining": {
            "version": "%s",
            "signedAt": "%s"
          }
        }
        """.formatted(programmeMembershipId, version, signedAt);

    mockMvc.perform(put("/api/coj")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void putShouldReturnBadRequestGeneratingCojPdfWhenNoCoj() throws Exception {
    UUID programmeMembershipId = UUID.randomUUID();

    String body = """
        {
          "traineeId": "40",
          "programmeMembershipId": "%s",
          "programmeName": "Test Programme",
        }
        """.formatted(programmeMembershipId);

    mockMvc.perform(put("/api/coj")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void putShouldReturnPreviousPdfWhenUploadedPdfExists(GoldGuideVersion version) throws Exception {
    UUID programmeMembershipId = UUID.randomUUID();
    Instant signedAt = Instant.now();

    String body = """
        {
          "traineeId": "40",
          "programmeMembershipId": "%s",
          "programmeName": "Test Programme",
          "conditionsOfJoining": {
            "version": "%s",
            "signedAt": "%s"
          }
        }
        """.formatted(programmeMembershipId, version, signedAt);

    byte[] response = "response content".getBytes();
    Resource resource = mock(Resource.class);
    when(resource.getContentAsByteArray()).thenReturn(response);
    when(service.getUploadedPdf("40/forms/coj/" + programmeMembershipId + ".pdf")).thenReturn(
        Optional.of(resource));

    mockMvc.perform(put("/api/coj")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(response));

    verify(service, never()).generateConditionsOfJoining(any(), anyBoolean());
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void putShouldReturnGeneratePdfWhenUploadedPdfNotExists(GoldGuideVersion version)
      throws Exception {
    UUID programmeMembershipId = UUID.randomUUID();
    Instant signedAt = Instant.now();

    String body = """
        {
          "traineeId": "40",
          "programmeMembershipId": "%s",
          "programmeName": "Test Programme",
          "conditionsOfJoining": {
            "version": "%s",
            "signedAt": "%s"
          }
        }
        """.formatted(programmeMembershipId, version, signedAt);

    when(service.getUploadedPdf("40/forms/coj/" + programmeMembershipId + ".pdf")).thenReturn(
        Optional.empty());

    byte[] response = "response content".getBytes();
    Resource resource = mock(Resource.class);
    when(resource.getContentAsByteArray()).thenReturn(response);
    when(service.generateConditionsOfJoining(any(), anyBoolean())).thenReturn(resource);

    mockMvc.perform(put("/api/coj")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(response));
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void putShouldUseProvidedDataWhenGeneratingNewPdf(GoldGuideVersion version) throws Exception {
    UUID programmeMembershipId = UUID.randomUUID();
    Instant signedAt = Instant.now();

    String body = """
        {
          "traineeId": "40",
          "programmeMembershipId": "%s",
          "programmeName": "Test Programme",
          "conditionsOfJoining": {
            "version": "%s",
            "signedAt": "%s"
          }
        }
        """.formatted(programmeMembershipId, version, signedAt);

    String key = "40/forms/coj/" + programmeMembershipId + ".pdf";
    when(service.getUploadedPdf(key)).thenReturn(Optional.empty());

    byte[] response = "response content".getBytes();
    Resource resource = mock(Resource.class);
    when(resource.getContentAsByteArray()).thenReturn(response);
    ArgumentCaptor<ConditionsOfJoiningPdfRequestDto> requestCaptor = ArgumentCaptor.captor();
    when(service.generateConditionsOfJoining(requestCaptor.capture(), anyBoolean())).thenReturn(
        resource);

    mockMvc.perform(put("/api/coj")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(body))
        .andExpect(status().isOk());

    ConditionsOfJoiningPdfRequestDto request = requestCaptor.getValue();
    assertThat("Unexpected trainee ID.", request.traineeId(), is("40"));
    assertThat("Unexpected programme membership ID.", request.programmeMembershipId(),
        is(programmeMembershipId));
    assertThat("Unexpected programme name.", request.programmeName(), is("Test Programme"));

    ConditionsOfJoining conditionsOfJoining = request.conditionsOfJoining();
    assertThat("Unexpected version.", conditionsOfJoining.version(), is(version));
    assertThat("Unexpected signed at.", conditionsOfJoining.signedAt(), is(signedAt));
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void putShouldNotPublishPdfWhenGeneratingNewPdf(GoldGuideVersion version) throws Exception {
    UUID programmeMembershipId = UUID.randomUUID();
    Instant signedAt = Instant.now();

    String body = """
        {
          "traineeId": "40",
          "programmeMembershipId": "%s",
          "programmeName": "Test Programme",
          "conditionsOfJoining": {
            "version": "%s",
            "signedAt": "%s"
          }
        }
        """.formatted(programmeMembershipId, version, signedAt);

    String key = "40/forms/coj/" + programmeMembershipId + ".pdf";
    when(service.getUploadedPdf(key)).thenReturn(Optional.empty());

    byte[] response = "response content".getBytes();
    Resource resource = mock(Resource.class);
    when(resource.getContentAsByteArray()).thenReturn(response);
    when(service.generateConditionsOfJoining(any(), eq(false))).thenReturn(resource);

    mockMvc.perform(put("/api/coj")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(body))
        .andExpect(status().isOk());
  }
}
