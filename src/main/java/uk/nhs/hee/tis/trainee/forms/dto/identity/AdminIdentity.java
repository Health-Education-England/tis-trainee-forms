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

package uk.nhs.hee.tis.trainee.forms.dto.identity;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Identity data for an authenticated TIS Admin user.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminIdentity extends UserIdentity {

  private static final String ROLE = "ADMIN";

  private Set<String> groups;

  /**
   * Whether the admin identity is considered complete based on the populated fields.
   *
   * @return Whether the admin identity is considered complete.
   */
  @Override
  public boolean isComplete() {
    return getEmail() != null && getName() != null && groups != null && !groups.isEmpty();
  }

  @Override
  public String getRole() {
    return ROLE;
  }
}
