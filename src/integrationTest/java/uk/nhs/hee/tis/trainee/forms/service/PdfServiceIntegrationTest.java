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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.apache.pdfbox.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.nhs.hee.tis.trainee.forms.DockerImageNames;
import uk.nhs.hee.tis.trainee.forms.dto.CovidDeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.DeclarationDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
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
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
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

  @MockBean
  private JwtDecoder jwtDecoder;

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
        .created(LocalDate.of(2021, 2, 3).atTime(4, 5)
            .atZone(zoneId).toInstant())
        .lastModified(LocalDate.of(2026, 7, 8).atTime(9, 10)
            .atZone(zoneId).toInstant())
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
    dto.setProgrammeName("some programme name");
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

    LocalDateTime submissionDate = LocalDate.of(2014, 10, 11).atTime(12, 13);
    ZoneOffset submissionOffset = ZonedDateTime.of(submissionDate, ZoneId.systemDefault()).getOffset();
    dto.setSubmissionDate(submissionDate.plusSeconds(submissionOffset.getTotalSeconds()));
    LocalDateTime lastModifiedDate = LocalDate.of(2014, 11, 12).atTime(13, 14);
    ZoneOffset lastModifiedOffset = ZonedDateTime.of(lastModifiedDate, ZoneId.systemDefault()).getOffset();
    dto.setLastModifiedDate(lastModifiedDate.plusSeconds(lastModifiedOffset.getTotalSeconds()));

    dto.setOtherImmigrationStatus("other immigration status");
    dto.setLifecycleState(LifecycleState.SUBMITTED);

    byte[] pdf = service.generatePdf(dto);

    int problems = compareGeneratedPdf("formr-parta", pdf);
    assertThat("Unexpected PDF comparison problem count.", problems, is(0));
  }

  @Test
  void shouldMatchEmptyFormRPartBPdfWhenDtoEmpty() throws IOException {
    FormRPartBDto dto = new FormRPartBDto();
    byte[] pdf = service.generatePdf(dto);

    int problems = compareGeneratedPdf("formr-partb-empty", pdf);
    assertThat("Unexpected PDF comparison problem count.", problems, is(0));
  }

  @Test
  void shouldMatchFullFormRPartBPdfWhenDtoPopulated() throws IOException {
    FormRPartBDto dto = new FormRPartBDto();
    dto.setId("form-id-12345");
    dto.setTraineeTisId("47165");
    dto.setProgrammeMembershipId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
    dto.setIsArcp(true);
    dto.setProgrammeName("some programme name");
    dto.setForename("Anthony");
    dto.setSurname("Gilliam");
    dto.setGmcNumber("1234567");
    dto.setEmail("test@testy.com");
    dto.setLocalOfficeName("London");
    dto.setPrevRevalBody("prev reval body");
    dto.setPrevRevalBodyOther("prev reval body other");
    dto.setCurrRevalDate(LocalDate.of(2024, 4, 5));
    dto.setPrevRevalDate(LocalDate.of(2021, 3, 4));
    dto.setProgrammeSpecialty("programme specialty");
    dto.setDualSpecialty("dual specialty");

    WorkDto work1 = new WorkDto();
    work1.setTypeOfWork("type of work 1");
    work1.setStartDate(LocalDate.of(2021, 9, 10));
    work1.setEndDate(LocalDate.of(2022, 9, 10));
    work1.setTrainingPost("post held 1");
    work1.setSite("site 1");
    work1.setSiteLocation("location 1");
    work1.setSiteKnownAs("known as 1");
    WorkDto work2 = new WorkDto();
    work2.setTypeOfWork("type of work 2");
    work2.setStartDate(LocalDate.of(2022, 9, 10));
    work2.setEndDate(LocalDate.of(2023, 9, 10));
    work2.setTrainingPost("post held 2");
    work2.setSite("site 2");
    work2.setSiteLocation("location 2");
    work2.setSiteKnownAs("known as 2");
    WorkDto work3 = new WorkDto();
    work3.setTypeOfWork("In Post ST3 General Practice");
    work3.setStartDate(LocalDate.of(2024, 11, 6));
    work3.setEndDate(LocalDate.of(2025, 10, 30));
    work3.setTrainingPost("Yes");
    work3.setSite("Cranleigh Gardens Medical Centre");
    work3.setSiteLocation("(until 28/02/2011 Brent House Surgery) Cranleigh Gardens Bridgwater Somerset");
    work3.setSiteKnownAs("Cranleigh Gardens Medical Centre (L85025)");
    dto.setWork(List.of(work1, work2, work3));

    dto.setSicknessAbsence(10);
    dto.setParentalLeave(0);
    dto.setCareerBreaks(5);
    dto.setPaidLeave(8);
    dto.setUnauthorisedLeave(1);
    dto.setOtherLeave(1);
    dto.setTotalLeave(25);

    dto.setIsHonest(true);
    dto.setIsHealthy(false);
    dto.setIsWarned(true);
    dto.setIsComplying(true);
    dto.setHealthStatement("health statement");

    dto.setHavePreviousDeclarations(true);
    DeclarationDto prevDeclaration1 = new DeclarationDto();
    prevDeclaration1.setDeclarationType("declaration type 1");
    prevDeclaration1.setDateOfEntry(LocalDate.of(2020, 1, 2));
    prevDeclaration1.setTitle("title 1");
    prevDeclaration1.setLocationOfEntry("location 1");
    DeclarationDto prevDeclaration2 = new DeclarationDto();
    prevDeclaration1.setDeclarationType("declaration type 2");
    prevDeclaration1.setDateOfEntry(LocalDate.of(2022, 2, 14));
    prevDeclaration1.setTitle("title 2");
    prevDeclaration1.setLocationOfEntry("location 2");
    dto.setPreviousDeclarations(List.of(prevDeclaration1, prevDeclaration2));

    dto.setHavePreviousUnresolvedDeclarations(true);
    dto.setPreviousDeclarationSummary("Previous declaration summary which could be a fairly long " +
        "piece of text to cover the various points that need to be made.");

    dto.setHaveCurrentDeclarations(true);
    DeclarationDto curDeclaration1 = new DeclarationDto();
    curDeclaration1.setDeclarationType("declaration type 11");
    curDeclaration1.setDateOfEntry(LocalDate.of(2024, 1, 2));
    curDeclaration1.setTitle("title 11");
    curDeclaration1.setLocationOfEntry("location 11");
    DeclarationDto curDeclaration2 = new DeclarationDto();
    curDeclaration1.setDeclarationType("declaration type 22");
    curDeclaration1.setDateOfEntry(LocalDate.of(2025, 2, 14));
    curDeclaration1.setTitle("title 22");
    curDeclaration1.setLocationOfEntry("location 22");
    dto.setCurrentDeclarations(List.of(curDeclaration1, curDeclaration2));

    dto.setHaveCurrentUnresolvedDeclarations(true);
    dto.setCurrentDeclarationSummary("Current declaration summary which could be a fairly long " +
        "piece of text to cover the various points that need to be made, such as this one is.");

    dto.setCompliments("some compliments text");

    dto.setHaveCovidDeclarations(true);
    CovidDeclarationDto covid = new CovidDeclarationDto();
    covid.setSelfRateForCovid("Self rate for covid");
    covid.setReasonOfSelfRate("Reason for self rate");
    covid.setOtherInformationForPanel("Other information for panel");
    covid.setDiscussWithSupervisorChecked(true);
    covid.setDiscussWithSomeoneChecked(false);
    covid.setHaveChangesToPlacement(true);
    covid.setChangeCircumstances("Change circumstances");
    covid.setChangeCircumstanceOther("Change circumstance other");
    covid.setHowPlacementAdjusted("How placement adjusted");
    covid.setEducationSupervisorName("Ed Super");
    covid.setEducationSupervisorEmail("super@ed.com");
    dto.setCovidDeclarationDto(covid);

    LocalDateTime submissionDate = LocalDate.of(2014, 10, 11).atTime(12, 13);
    ZoneOffset submissionOffset = ZonedDateTime.of(submissionDate, ZoneId.systemDefault()).getOffset();
    dto.setSubmissionDate(submissionDate.plusSeconds(submissionOffset.getTotalSeconds()));
    LocalDateTime lastModifiedDate = LocalDate.of(2014, 11, 12).atTime(13, 14);
    ZoneOffset lastModifiedOffset = ZonedDateTime.of(lastModifiedDate, ZoneId.systemDefault()).getOffset();
    dto.setLastModifiedDate(lastModifiedDate.plusSeconds(lastModifiedOffset.getTotalSeconds()));

    dto.setLifecycleState(LifecycleState.SUBMITTED);

    byte[] pdf = service.generatePdf(dto);

    int problems = compareGeneratedPdf("formr-partb", pdf);
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
