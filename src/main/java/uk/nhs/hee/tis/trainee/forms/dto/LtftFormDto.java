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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.Null;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import org.bson.types.ObjectId;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.validation.Create;

@Data
public class LtftFormDto {
  @JsonSerialize(using = ToStringSerializer.class)
  @Null(groups = Create.class)
  private ObjectId id;

  private String traineeId;

  private String name;

  private LtftProgrammeMembershipDto programmeMembership;

  private LtftDiscussionDto discussions;

  private LifecycleState status;

  private Instant created;
  private Instant lastModified;

  @Data
  public static class LtftProgrammeMembershipDto {
    private String id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private double wte;
  }

  @Data
  public static class LtftDiscussionDto {
    private String tpdName;
    private String tpdEmail;
    private List<LtftPersonRole> other;
  }

  @Data
  public static class LtftPersonRole {
    private String name;
    private String email;
    private String role;
  }
}
