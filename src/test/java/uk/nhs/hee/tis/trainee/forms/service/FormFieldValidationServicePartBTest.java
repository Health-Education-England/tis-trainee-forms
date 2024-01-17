package uk.nhs.hee.tis.trainee.forms.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.services.s3.AmazonS3;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.WorkDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class FormFieldValidationServicePartBTest {

  private static final String STRING_9_CHARS = "012345678";
  private static final String STRING_21_CHARS = "0123456789abcdefghij0";
  private static final String STRING_120_CHARS = "0123456789abcdefghij0123456789abcdefghij"
      + "0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij0123456789abcdefghij";
  private static final String STRING_240_CHARS = STRING_120_CHARS + STRING_120_CHARS;

  @MockBean
  AmazonS3 amazonS3;

  @Autowired
  private FormFieldValidationService service;

  @Test
  void whenInputIsValidThenThrowsNoException() {
    FormRPartBDto input = validForm();

    service.validateFormRPartB(input);

    // then no exception
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenForenameIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setForename(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {STRING_120_CHARS})
  void whenSurnameIsInvalidThenThrowsException(String str) {
    FormRPartBDto input = validForm();
    input.setSurname(str);

    assertThrows(ValidationException.class, () -> service.validateFormRPartB(input));
  }


  /**
   * Helper function to return a validly completed FormR PartB.
   *
   * @return the FormR PartB DTO.
   */
  FormRPartBDto validForm() {
    FormRPartBDto input = new FormRPartBDto();

    input.setId("a3586ddb-adea-4709-9806-18e5bd200a69");
    input.setForename("David");
    input.setSurname("Short");
    input.setGmcNumber("8999999");
    input.setEmail("potato@potato.com");
    input.setLocalOfficeName("Health Education England South London");
    input.setCurrRevalDate(LocalDate.now().plusYears(1L));
    input.setProgrammeSpecialty("Geriatric Medicine");

    WorkDto workDto = new WorkDto();
    workDto.setTypeOfWork("some type of work");
    workDto.setTrainingPost("a training post");
    workDto.setSite("the site");
    workDto.setSiteLocation("site location");
    workDto.setStartDate(LocalDate.now());
    workDto.setEndDate(LocalDate.now().plusYears(1L));

    input.setWork(List.of(workDto));

    input.setSicknessAbsence(1);
    input.setParentalLeave(0);
    input.setCareerBreaks(0);
    input.setPaidLeave(0);
    input.setUnauthorisedLeave(0);
    input.setOtherLeave(0);
    input.setTotalLeave(1);

    input.setIsHonest(true);
    input.setIsHealthy(true);
    input.setIsWarned(false);
    input.setIsComplying(true);

    input.setHavePreviousDeclarations(false);
    input.setHaveCurrentDeclarations(false);
    input.setHaveCovidDeclarations(false);
    input.setHaveCurrentUnresolvedDeclarations(false);
    input.setHavePreviousUnresolvedDeclarations(false);

    input.setSubmissionDate(LocalDateTime.now());
    input.setLastModifiedDate(LocalDateTime.now());

    input.setLifecycleState(LifecycleState.SUBMITTED);

    return input;
  }
}
