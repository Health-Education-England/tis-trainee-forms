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

package uk.nhs.hee.tis.trainee.forms.model.content;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.With;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType;
import uk.nhs.hee.tis.trainee.forms.model.Person;

/**
 * The content of an LTFT application form.
 *
 * @param name                The trainee provided name for the application.
 * @param personalDetails     The trainee's personal details.
 * @param programmeMembership The programme membership linked with the LTFT application.
 * @param declarations        The LTFT declarations.
 * @param discussions         Discussions which took place as part of the LTFT process.
 * @param change              The calculated LTFT change.
 * @param cctSnapshot         A snapshot of a CCT calculation, attached by the trainee.
 * @param reasons             The reasons for applying for LTFT.
 * @param exceptionalReasons  Additional reasons for exceptional applications.
 * @param tpdEmailValidity    The validity of the email address for the TPD, if applicable.
 */
@Builder
public record LtftContent(
    String name,
    PersonalDetails personalDetails,
    ProgrammeMembership programmeMembership,
    Declarations declarations,
    Discussions discussions,
    @With
    CctChange change,
    CctSnapshot cctSnapshot,
    Reasons reasons,
    ExceptionalReasons exceptionalReasons,
    @With
    EmailValidityType tpdEmailValidity) implements FormContent {

  /**
   * The trainee's personal details.
   *
   * @param title                   The trainee's title.
   * @param forenames               The trainee's forenames or given name.
   * @param surname                 The trainee's surname or family name.
   * @param email                   The trainee's email address.
   * @param telephoneNumber         The trainee's contact telephone number.
   * @param mobileNumber            The trainee's contact mobile number.
   * @param gmcNumber               The trainee's GMC registration number.
   * @param gdcNumber               The trainee's GDC registration number.
   * @param skilledWorkerVisaHolder Whether the trainee holds a skilled worker visa.
   */
  @Builder
  public record PersonalDetails(
      String title,
      @Indexed
      String forenames,
      @Indexed
      String surname,
      String email,
      String telephoneNumber,
      String mobileNumber,
      @Indexed
      String gmcNumber,
      @Indexed
      String gdcNumber,
      Boolean skilledWorkerVisaHolder) {

  }

  /**
   * Programme membership data for a calculation.
   *
   * @param id                 The ID of the programme membership.
   * @param name               The name of the programme.
   * @param programmeNumber    The programme number of the associated programme.
   * @param designatedBodyCode The designated body code for the programme.
   * @param startDate          The start date of the programme.
   * @param endDate            The end date of the programme.
   * @param wte                The whole time equivalent of the programme membership.
   */
  @Builder
  public record ProgrammeMembership(
      @Indexed
      @Field("id")
      UUID id,
      @Indexed
      String name,
      @Indexed
      String programmeNumber,
      @Indexed(name = "dbc")
      String designatedBodyCode,
      String managingDeanery,
      LocalDate startDate,
      LocalDate endDate,
      Double wte) {

  }

  /**
   * A set of declarations that the trainee must provide.
   *
   * @param discussedWithTpd     That the trainee has discussed with their TPD.
   * @param informationIsCorrect That the trainee has given correct information.
   * @param notGuaranteed        That LTFT approval is not guaranteed
   */
  @Builder
  public record Declarations(
      Boolean discussedWithTpd,
      Boolean informationIsCorrect,
      Boolean notGuaranteed) {

  }

  /**
   * Discussions that the trainee has had about their LTFT needs.
   *
   * @param tpdName  The name of their TPD.
   * @param tpdEmail The contact email for their TPD.
   * @param other    Other people they have discussed with e.g. Education Supervisor.
   */
  @Builder
  public record Discussions(
      String tpdName,
      String tpdEmail,
      List<Person> other) {

  }

  /**
   * A snapshot of a CCT calculation.
   *
   * @param calculationId   The ID of the CCT calculation.
   * @param snapshotTakenAt When the snapshot was taken.
   * @param calculation     The CCT calculation at the time of the snapshot.
   */
  @Builder
  public record CctSnapshot(
      UUID calculationId,
      Instant snapshotTakenAt,
      CctCalculation calculation) {

    /**
     * A CCT calculation.
     *
     * @param id               The ID of the calculation.
     * @param name             The trainee provided name for the calculation.
     * @param programmeDetails The details of the programme the calculation is for.
     * @param changes          The changes to the programme, with the months they credit.
     * @param result           The result of the calculation.
     * @param created          When the calculation was first created.
     * @param lastModified     When the calculation was last modified.
     */
    @Builder
    public record CctCalculation(
        @Field("id")
        UUID id,
        String name,
        CctProgrammeDetails programmeDetails,
        List<CctCalculatedChange> changes,
        CctResult result,
        Instant created,
        Instant lastModified) {

    }

    /**
     * The details of the programme a calculation is for.
     *
     * @param specialty                    The name of the programme's specialty.
     * @param startDate                    The start date of the programme.
     * @param lengthMonths                 The length of the programme, in months.
     * @param lengthOverrideNotes          The reason the length differs from the specialty's.
     * @param startGrade                   The grade the programme starts at.
     * @param startGradeOverrideNotes      The reason the start grade differs from the specialty's.
     * @param additionalMonths             The additional training time, in months.
     * @param additionalMonthsNotes        The reason for the additional training time.
     * @param acceleratedMonths            The accelerated training time, in months.
     * @param acceleratedMonthsNotes       The reason for the accelerated training time.
     * @param eighteenMonthFinalGrade      The grade with an 18-month final year, if any.
     * @param eighteenMonthFinalGradeNotes The reason for the 18-month final year.
     * @param skippedGrade                 The grade year that is skipped, if any.
     * @param skippedGradeNotes            The reason for skipping the grade year.
     */
    @Builder
    public record CctProgrammeDetails(
        String specialty,
        LocalDate startDate,
        Integer lengthMonths,
        String lengthOverrideNotes,
        String startGrade,
        String startGradeOverrideNotes,
        Integer additionalMonths,
        String additionalMonthsNotes,
        Integer acceleratedMonths,
        String acceleratedMonthsNotes,
        String eighteenMonthFinalGrade,
        String eighteenMonthFinalGradeNotes,
        String skippedGrade,
        String skippedGradeNotes) {

    }

    /**
     * A change to the programme a calculation is for, with the months it credits.
     *
     * @param type                    The type of change.
     * @param startDate               The start date of the change.
     * @param endDate                 The end date of the change, inclusive, or null for an LTFT
     *                                change that lasts for the rest of training.
     * @param wte                     The whole time equivalent of the change, as a fraction.
     * @param countsTowardsCompletion Whether the change counts towards completion of training.
     * @param notes                   The trainee's notes about the change.
     * @param calendarMonths          The calendar months in the change.
     * @param wteMonths               The whole time equivalent months the change credits.
     */
    @Builder
    public record CctCalculatedChange(
        String type,
        LocalDate startDate,
        LocalDate endDate,
        Double wte,
        boolean countsTowardsCompletion,
        String notes,
        Double calendarMonths,
        Double wteMonths) {

    }

    /**
     * The result of a calculation.
     *
     * @param cctDate                  The calculated CCT date.
     * @param originalProgrammeEndDate The end date of the programme, before additional and
     *                                 accelerated training time.
     * @param adjustedProgrammeEndDate The end date of the programme, after additional and
     *                                 accelerated training time.
     * @param totalWteMonthsCompleted  The whole time equivalent months completed before the
     *                                 projection.
     * @param monthsRemaining          The months of training remaining.
     * @param assumedFullTimePeriods   The periods before the projection that no change covers.
     * @param projection               The projection of the remaining training to the CCT date.
     */
    @Builder
    public record CctResult(
        LocalDate cctDate,
        LocalDate originalProgrammeEndDate,
        LocalDate adjustedProgrammeEndDate,
        Double totalWteMonthsCompleted,
        Double monthsRemaining,
        List<CctAssumedFullTimePeriod> assumedFullTimePeriods,
        CctProjection projection) {

    }

    /**
     * A period of training that no change covers, assumed to be full time.
     *
     * @param startDate The start date of the period.
     * @param endDate   The end date of the period, inclusive.
     * @param months    The months in the period, both calendar and whole time equivalent.
     */
    @Builder
    public record CctAssumedFullTimePeriod(
        LocalDate startDate,
        LocalDate endDate,
        Double months) {

    }

    /**
     * The projection of the remaining training to the CCT date.
     *
     * @param startDate      The start date of the projection.
     * @param wte            The whole time equivalent of the projection, as a fraction.
     * @param calendarMonths The calendar months the remaining training takes.
     * @param wteMonths      The whole time equivalent months of training remaining.
     */
    @Builder
    public record CctProjection(
        LocalDate startDate,
        Double wte,
        Double calendarMonths,
        Double wteMonths) {

    }
  }

  /**
   * Reasons for applying for LTFT.
   *
   * @param selected              A list of selected reasons.
   * @param otherDetail           Additional details if "Other" reason was selected.
   * @param supportingInformation Supporting information for the application.
   */
  @Builder
  public record Reasons(
      List<String> selected,
      String otherDetail,
      String supportingInformation) {

  }

  /**
   * Additional reasons for exceptional applications.
   *
   * @param exceptional           Whether the application is exceptional.
   * @param supportingInformation The supporting information for the exception.
   * @param startDate             The proposed start date for the exception.
   */
  @Builder
  public record ExceptionalReasons(
      Boolean exceptional,
      String supportingInformation,
      LocalDate startDate) {

  }
}
