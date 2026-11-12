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

package uk.nhs.hee.tis.trainee.forms.dto;

import com.fasterxml.jackson.annotation.JsonView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.ResponsibleRole;
import uk.nhs.hee.tis.trainee.forms.dto.validation.Create;
import uk.nhs.hee.tis.trainee.forms.dto.validation.Update;
import uk.nhs.hee.tis.trainee.forms.dto.views.Admin;
import uk.nhs.hee.tis.trainee.forms.dto.views.ReadOnly;
import uk.nhs.hee.tis.trainee.forms.model.ReviewStageStatus;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChangeType;

/**
 * A DTO for transferring LTFT forms.
 *
 * @param id                  The ID of the LTFT form.
 * @param traineeTisId        The trainee's TID ID.
 * @param formRef             The human-readable reference for the form.
 * @param revision            The revision number of the form.
 * @param name                The trainee provided name for the application.
 * @param personalDetails     The trainee's personal details.
 * @param programmeMembership The programme membership linked with the LTFT application.
 * @param declarations        The LTFT declarations.
 * @param discussions         Discussions which took place as part of the LTFT process.
 * @param change              The calculated LTFT change.
 * @param cctSnapshot         A snapshot of a CCT calculation, attached by the trainee.
 * @param reasons             The reasons for applying for LTFT.
 * @param status              The status of the LTFT application, both current and audit history.
 * @param created             When the LTFT application was first created.
 * @param lastModified        When the LTFT application was last modified.
 */
@Builder
public record LtftFormDto(
    @Null(groups = Create.class)
    UUID id,
    String traineeTisId,

    @JsonView(ReadOnly.class)
    @Null(groups = {Create.class, Update.class})
    String formRef,

    @JsonView(ReadOnly.class)
    @Null(groups = {Create.class, Update.class})
    Integer revision,
    String name,
    PersonalDetailsDto personalDetails,
    ProgrammeMembershipDto programmeMembership,
    DeclarationsDto declarations,
    DiscussionsDto discussions,
    @Valid
    CctChangeDto change,
    @Valid
    CctSnapshotDto cctSnapshot,
    ReasonsDto reasons,
    ExceptionalReasonsDto exceptionalReasons,
    @JsonView(ReadOnly.class)
    @Null(groups = {Create.class, Update.class})
    EmailValidityType tpdEmailStatus,

    @JsonView(ReadOnly.class)
    @Null(groups = {Create.class, Update.class})
    StatusDto status,

    @JsonView(ReadOnly.class)
    @Null(groups = {Create.class, Update.class})
    Instant created,

    @JsonView(ReadOnly.class)
    @Null(groups = {Create.class, Update.class})
    Instant lastModified,
    Boolean shortNotice) {

  /**
   * The calculated LTFT change.
   *
   * @param id            The ID of the CCT change used to start the application.
   * @param calculationId The ID of the CCT calculation used to start the application.
   * @param type          The type of change.
   * @param wte           The whole time equivalent after the change.
   * @param startDate     The start date of the change.
   * @param altStartDate  Alternate start date if the start date is short-dated.
   * @param endDate       The end date of the change.
   */
  @Builder
  public record CctChangeDto(
      UUID id,
      UUID calculationId,
      CctChangeType type,
      @DecimalMax("1.0")
      Double wte,
      LocalDate startDate,
      LocalDate altStartDate,
      LocalDate endDate) {

  }

  /**
   * A snapshot of a CCT calculation.
   *
   * @param calculationId The ID of the CCT calculation.
   * @param calculation   The CCT calculation at the time of the snapshot.
   */
  @Builder
  public record CctSnapshotDto(
      @NotNull
      UUID calculationId,
      @NotNull
      @Valid
      CctCalculationDto calculation) {

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
    public record CctCalculationDto(
        UUID id,
        String name,
        CctProgrammeDetailsDto programmeDetails,
        List<CctCalculatedChangeDto> changes,
        CctResultDto result,
        Instant created,
        @NotNull
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
    public record CctProgrammeDetailsDto(
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
    public record CctCalculatedChangeDto(
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
    public record CctResultDto(
        LocalDate cctDate,
        LocalDate originalProgrammeEndDate,
        LocalDate adjustedProgrammeEndDate,
        Double totalWteMonthsCompleted,
        Double monthsRemaining,
        List<CctAssumedFullTimePeriodDto> assumedFullTimePeriods,
        CctProjectionDto projection) {

    }

    /**
     * A period of training that no change covers, assumed to be full time.
     *
     * @param startDate The start date of the period.
     * @param endDate   The end date of the period, inclusive.
     * @param months    The months in the period, both calendar and whole time equivalent.
     */
    @Builder
    public record CctAssumedFullTimePeriodDto(
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
    public record CctProjectionDto(
        LocalDate startDate,
        Double wte,
        Double calendarMonths,
        Double wteMonths) {

    }
  }

  /**
   * A set of declarations that the trainee must provide.
   *
   * @param discussedWithTpd     That the trainee has discussed with their TPD.
   * @param informationIsCorrect That the trainee has given correct information.
   * @param notGuaranteed        That LTFT approval is not guaranteed
   */
  @Builder
  public record DeclarationsDto(
      Boolean discussedWithTpd,
      Boolean informationIsCorrect,
      Boolean notGuaranteed) {

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
  public record ProgrammeMembershipDto(
      UUID id,
      String name,
      String programmeNumber,
      String designatedBodyCode,
      String managingDeanery,
      LocalDate startDate,
      LocalDate endDate,
      Double wte) {

  }

  /**
   * Reasons for applying for LTFT.
   *
   * @param selected              A list of selected reasons.
   * @param otherDetail           Additional details if "Other" reason was selected.
   * @param supportingInformation Supporting information for the application.
   */
  @Builder
  public record ReasonsDto(
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
  public record ExceptionalReasonsDto(
      Boolean exceptional,
      String supportingInformation,
      LocalDate startDate) {

  }

  /**
   * The form status.
   *
   * @param current   The information for the current form status.
   * @param submitted When the form was last submitted.
   * @param history   A list of form status history.
   */
  @Builder
  public record StatusDto(

      StatusInfoDto current,
      Instant submitted,
      List<StatusInfoDto> history) {

    /**
     * Form status information.
     *
     * @param state            The lifecycle state of the form.
     * @param detail           Status reason detail.
     * @param assignedAdmin    The admin who is assigned to process the form.
     * @param assignedReviewer The reviewer who is assigned to review the form.
     * @param modifiedBy       The Person who made this status change.
     * @param timestamp        The timestamp of the status change.
     * @param revision         The revision number associated with this status change.
     * @param reviewStage      The review stage associated with this status change, if any.
     * @param reviewOutcome    The review outcome recorded by the reviewer, if any.
     * @param responsibleRole  The role responsible for progressing the form at this status, if
     *                         any. Derived from the form, so ignored when provided.
     */
    @Builder
    public record StatusInfoDto(

        LifecycleState state,
        LftfStatusInfoDetailDto detail,
        @JsonView(Admin.Read.class)
        RedactedPersonDto assignedAdmin,
        @JsonView(Admin.Read.class)
        RedactedPersonDto assignedReviewer,
        RedactedPersonDto modifiedBy,
        Instant timestamp,
        Integer revision,
        ReviewStageStatus reviewStage,
        @JsonView(Admin.Read.class)
        LtftReviewOutcomeDto reviewOutcome,
        @JsonView(Admin.Read.class)
        ResponsibleRole responsibleRole
    ) {

    }

    /**
     * A DTO for state change details.
     *
     * @param reason  The reason for the state change.
     * @param message A message associated with the state change.
     */
    @Builder
    public record LftfStatusInfoDetailDto(

        String reason,
        String message) {

    }
  }

  /**
   * Discussions that the trainee has had about their LTFT needs.
   *
   * @param tpdName  The name of their TPD.
   * @param tpdEmail The contact email for their TPD.
   * @param other    Other people they have discussed with e.g. Education Supervisor.
   */
  @Builder
  public record DiscussionsDto(
      String tpdName,
      String tpdEmail,
      List<PersonDto> other) {

  }
}
