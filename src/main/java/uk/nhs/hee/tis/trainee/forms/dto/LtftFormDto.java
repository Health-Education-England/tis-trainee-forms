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

import jakarta.validation.constraints.Null;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.validation.Create;
import uk.nhs.hee.tis.trainee.forms.model.Person;

/**
 * A DTO for transferring LTFT forms.
 */
@Data
public class LtftFormDto {

  @Null(groups = Create.class)
  private UUID id;

  private String traineeTisId;

  private String name;

  private LtftProgrammeMembershipDto programmeMembership;

  private LtftStatusDto status;
  private LtftDiscussionDto discussions;

  private Instant created;
  private Instant lastModified;

  /**
   * A DTO for LTFT programme membership details.
   */
  @Data
  public static class LtftProgrammeMembershipDto {

    private UUID id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private double wte;
  }

  /**
   * A DTO for LTFT form lifecycle state.
   */
  @Data
  public static class LtftStatusDto {

    private LifecycleState current;
    private List<LtftStatusInfoDto> history;
  }

  /**
   * A DTO for LTFT form lifecycle state details.
   */
  @Data
  public static class LtftStatusInfoDto {

    private LifecycleState state;
    private LftfStatusInfoDetailDto detail;
    private Person modifiedBy;
    private Instant timestamp;
    private Integer revision;

    /**
     * A DTO for state change details.
     */
    @Data
    public static class LftfStatusInfoDetailDto {

      private String reason;
      private String message;
    }
  }

  /**
   * A DTO for LTFT discussions.
   */
  @Data
  public static class LtftDiscussionDto {

    private String tpdName;
    private String tpdEmail;
    private List<LtftPersonRole> other;
  }

  /**
   * A DTO for LTFT form discussion non-TPD people.
   */
  @Data
  public static class LtftPersonRole {

    private String name;
    private String email;
    private String role;
  }
}
