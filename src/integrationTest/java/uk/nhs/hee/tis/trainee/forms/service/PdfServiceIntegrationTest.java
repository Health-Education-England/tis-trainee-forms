/*
 * The MIT License (MIT)
 *
 * Copyright 2025 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.openhtmltopdf.pdfboxout.visualtester.PdfVisualTester;
import com.openhtmltopdf.pdfboxout.visualtester.PdfVisualTester.PdfCompareResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.pdfbox.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctChangeDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.DeclarationsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.DiscussionsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.ProgrammeMembershipDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.ReasonsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.StatusInfoDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonalDetailsDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PdfServiceIntegrationTest {

  // Not ideal having a hardcoded path, but we want to be able to upload the results.
  private static final String TEST_OUTPUT_PATH = "build/reports/pdf-regression";

  @Container
  @ServiceConnection
  private static final MongoDBContainer mongoContainer = new MongoDBContainer(
      DockerImageNames.MONGO);

  @Autowired
  private PdfService service;

  @Value("${application.timezone}")
  private ZoneId zoneId;

  @Test
  void shouldMatchEmptyLtftPdfWhenDtoEmpty() throws IOException {
    LtftFormDto dto = LtftFormDto.builder().build();

    byte[] generatedBytes = service.generatePdf(dto, "admin");

    int problems = compareGeneratedPdf("ltft-admin-empty", generatedBytes);
    assertThat("Unexpected PDF comparison problem count.", problems, is(0));
  }

  @Test
  void shouldMatchFullLtftPdfWhenDtoPopulated() throws IOException {
    LtftFormDto dto = LtftFormDto.builder()
        .formRef("ltft_47165_040")
        .created(LocalDate.of(2021, 2, 3).atTime(4, 5).atZone(zoneId).toInstant())
        .lastModified(LocalDate.of(2026, 7, 8).atTime(9, 10).atZone(zoneId).toInstant())
        .status(StatusDto.builder()
            .current(StatusInfoDto.builder()
                .state(LifecycleState.SUBMITTED)
                .build())
            .build())
        .personalDetails(PersonalDetailsDto.builder()
            .title("Dr")
            .forenames("Anthony")
            .surname("Gilliam")
            .email("anthony.gilliam@example.com")
            .telephoneNumber("07700 900000")
            .mobileNumber("07700 900000")
            .gmcNumber("1234567")
            .gdcNumber("D123456")
            .skilledWorkerVisaHolder(false)
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .name("General Practice")
            .startDate(LocalDate.of(2024, 3, 1))
            .endDate(LocalDate.of(2027, 3, 1))
            .wte(0.8)
            .build())
        .change(CctChangeDto.builder()
            .wte(0.6)
            .startDate(LocalDate.of(2026, 3, 1))
            .endDate(LocalDate.of(2027, 3, 1))
            .cctDate(LocalDate.of(2028, 3, 1))
            .build())
        .reasons(ReasonsDto.builder()
            .selected(List.of("Caring Responsibilities", "Other"))
            .otherDetail("A trainee described reason detail")
            .supportingInformation("Trainee supplied supporting information")
            .build())
        .discussions(DiscussionsDto.builder()
            .tpdName("Tee Pee-Dee")
            .tpdEmail("tpd@example.com")
            .other(List.of(
                PersonDto.builder()
                    .name("Ed Super")
                    .email("ed.super@example.com")
                    .role("Educational Supervisor")
                    .build(),
                PersonDto.builder()
                    .name("Person Two")
                    .email("person.2@example.com")
                    .role("Test Person")
                    .build()
            ))
            .build())
        .declarations(DeclarationsDto.builder()
            .informationIsCorrect(true)
            .discussedWithTpd(false)
            .notGuaranteed(true)
            .build())
        .build();

    byte[] generatedBytes = service.generatePdf(dto, "admin");

    int problems = compareGeneratedPdf("ltft-admin", generatedBytes);
    assertThat("Unexpected PDF comparison problem count.", problems, is(0));
  }

  @Test
  void shouldMatchEmptyFormRPartAPdfWhenDtoEmpty() throws IOException {
    FormRPartADto dto = new FormRPartADto();
    byte[] pdf = service.generatePdf(dto);

    int problems = compareGeneratedPdf("formr-parta-empty", pdf);
    assertThat("Unexpected PDF comparison problem count.", problems, is(0));
  }

  @Test
  void shouldMatchFullFormRPartAPdfWhenDtoPopulated() throws IOException {
    FormRPartADto dto = new FormRPartADto();
    dto.setId("form-id-12345");
    dto.setTraineeTisId("47165");
    dto.setProgrammeMembershipId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
    dto.setIsArcp(true);
    dto.setForename("Anthony");
    dto.setSurname("Gilliam");
    dto.setGmcNumber("1234567");
    dto.setLocalOfficeName("London");
    dto.setDateOfBirth(LocalDate.of(1980, 4, 5));
    dto.setGender("gender");
    dto.setImmigrationStatus("immigration status");
    dto.setQualification("qualification");
    dto.setDateAttained(LocalDate.of(2004, 6, 7));
    dto.setMedicalSchool("medical school");
    dto.setAddress1("address line 1");
    dto.setAddress2("address line 2");
    dto.setAddress3("address line 3");
    dto.setAddress4("address line 4");
    dto.setPostCode("AB12 3CD");
    dto.setTelephoneNumber("+441200900000");
    dto.setMobileNumber("+447700900000");
    dto.setEmail("test@testy.com");
    dto.setDeclarationType("I have been appointed to a programme leading to award of CCT");
    dto.setIsLeadingToCct(true);
    dto.setProgrammeSpecialty("programme specialty");
    dto.setCctSpecialty1("cct specialty 1");
    dto.setCctSpecialty2("cct specialty 2");
    dto.setCollege("college");
    dto.setCompletionDate(LocalDate.of(2024, 8, 9));
    dto.setTrainingGrade("training grade");
    dto.setStartDate(LocalDate.of(2021, 9, 10));
    dto.setProgrammeMembershipType("Substantive");
    dto.setWholeTimeEquivalent("0.5");
    dto.setSubmissionDate(LocalDate.of(2024, 10, 11).atTime(12, 13));
    dto.setLastModifiedDate(LocalDate.of(2024, 11, 12).atTime(13, 14));
    dto.setOtherImmigrationStatus("other immigration status");
    dto.setLifecycleState(LifecycleState.SUBMITTED);

    byte[] pdf = service.generatePdf(dto);

    int problems = compareGeneratedPdf("formr-parta", pdf);
    assertThat("Unexpected PDF comparison problem count.", problems, is(0));
  }

  /**
   * Compare the bytes of a generated PDF against an existing example.
   *
   * @param resource          The filename of the existing PDF to check against, without extension.
   * @param generatedPdfBytes The generated bytes.
   * @return The number of discrepancies between the generation and expected results.
   * @throws IOException If output files could not be created, or reading the bytes fails.
   */
  private int compareGeneratedPdf(String resource, byte[] generatedPdfBytes) throws IOException {
    Files.createDirectories(Paths.get(TEST_OUTPUT_PATH));

    byte[] expectedPdfBytes;

    try (InputStream expectedIs = getClass().getResourceAsStream("/pdf/" + resource + ".pdf")) {
      assert expectedIs != null;
      expectedPdfBytes = IOUtils.toByteArray(expectedIs);
    }

    // Get a list of results.
    List<PdfCompareResult> problems = PdfVisualTester.comparePdfDocuments(expectedPdfBytes,
        generatedPdfBytes, resource, false);

    if (!problems.isEmpty()) {
      System.err.println("Found problems with test case (" + resource + "):");
      System.err.println(problems.stream()
          .map(p -> p.logMessage)
          .collect(Collectors.joining("\n    ", "[\n    ", "\n]")));

      System.err.println("For test case (" + resource + ") writing failure artefacts to '"
          + TEST_OUTPUT_PATH + "'");
      File generatedPdf = new File(TEST_OUTPUT_PATH, resource + "---actual.pdf");
      Files.write(generatedPdf.toPath(), generatedPdfBytes);
    }

    for (PdfCompareResult result : problems) {
      if (result.testImages != null) {
        File output = new File(TEST_OUTPUT_PATH,
            resource + "---" + result.pageNumber + "---diff.png");
        ImageIO.write(result.testImages.createDiff(), "png", output);

        output = new File(TEST_OUTPUT_PATH, resource + "---" + result.pageNumber + "---actual.png");
        ImageIO.write(result.testImages.getActual(), "png", output);

        output = new File(TEST_OUTPUT_PATH,
            resource + "---" + result.pageNumber + "---expected.png");
        ImageIO.write(result.testImages.getExpected(), "png", output);
      }
    }

    return problems.size();
  }
}
