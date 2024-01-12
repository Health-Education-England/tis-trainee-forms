package uk.nhs.hee.tis.trainee.forms.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.s3.AmazonS3;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@ExtendWith(SpringExtension.class)
@SpringBootTest
//@ComponentScan(basePackageClasses = {Config.class})
class FormFieldValidationServiceTest {

  @MockBean
  AmazonS3 amazonS3;

  @Autowired
  private FormFieldValidationService service;

  @Test
  void whenInputIsValidThenThrowsNoException(){
    FormRPartADto input = new FormRPartADto();
    input.setId("a3586ddb-adea-4709-9806-18e5bd200a69");
    input.setForename("David");
    input.setSurname("Short");
    input.setGmcNumber("8999999");
    input.setLocalOfficeName("Health Education England South London");
    input.setDateOfBirth(LocalDate.of(2000,1,1));
    input.setGender("Male");
    input.setImmigrationStatus("British National Overseas");
    input.setQualification("Degree");
    input.setDateAttained(LocalDate.of(1997,10,5));
    input.setMedicalSchool("Sheffield University");
    input.setAddress1("3rd Floor");
    input.setAddress2("3 Piccadilly Place");
    input.setAddress3("Manchester");
    input.setPostCode("M1 3BN");
    input.setTelephoneNumber("0161 625 7379");
    input.setMobileNumber("+445326346346");
    input.setEmail("potato@potato.com");
    input.setDeclarationType("I have been appointed to a programme leading to award of CCT");
    input.setIsLeadingToCct(null);
    input.setProgrammeSpecialty("Geriatric Medicine");
    input.setCctSpecialty1("GP Returner");
    input.setCctSpecialty2("GP Returner");
    input.setCollege("Faculty of Intensive Care Medicine");
    input.setCompletionDate(LocalDate.of(2027,7,3));
    input.setTrainingGrade("Core Training Year 3");
    input.setStartDate(LocalDate.of(2024,1,9));
    input.setProgrammeMembershipType("Military");
    input.setWholeTimeEquivalent("1");
    input.setSubmissionDate(LocalDateTime.of(2024,1,11,16,4,48));
    input.setLastModifiedDate(LocalDateTime.of(2024,1,11,16,4,48));
    input.setOtherImmigrationStatus("");
    input.setLifecycleState(LifecycleState.SUBMITTED);

    service.validateFormRPartA(input);

    // then no exception
  }

  @Test
  void whenInputIsInvalidThenThrowsException(){
    FormRPartADto input = new FormRPartADto();
    input.setForename("0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij");

    assertThrows(ValidationException.class, () -> service.validateFormRPartA(input));
  }
}
