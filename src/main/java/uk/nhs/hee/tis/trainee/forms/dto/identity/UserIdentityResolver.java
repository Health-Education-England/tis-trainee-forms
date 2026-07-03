/*
 * The MIT License (MIT)
 *
 * Copyright 2026 Crown Copyright (Health Education England)
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
 *
 */

package uk.nhs.hee.tis.trainee.forms.dto.identity;

import org.springframework.stereotype.Component;

/**
 * A resolver for determining the identity of the user making a request, whether they are an admin
 * or a trainee.
 */
@Component
public class UserIdentityResolver {

  private final AdminIdentity adminIdentity;
  private final TraineeIdentity traineeIdentity;

  /**
   * Constructor for UserIdentityResolver.
   *
   * @param adminIdentity   The {@link AdminIdentity} of the current request's user.
   * @param traineeIdentity The {@link TraineeIdentity} of the current request's user.
   */
  public UserIdentityResolver(AdminIdentity adminIdentity, TraineeIdentity traineeIdentity) {
    this.adminIdentity = adminIdentity;
    this.traineeIdentity = traineeIdentity;
  }

  /**
   * Get the {@link UserIdentity} of the current request's user.
   *
   * @return {@link AdminIdentity} if an admin, or {@link TraineeIdentity} if a trainee.
   */
  public UserIdentity getUserIdentity() {
    if (adminIdentity.isComplete()) {
      return adminIdentity;
    }
    if (traineeIdentity.isComplete()) {
      return traineeIdentity;
    }
    throw new IllegalStateException(
        "No complete user identity has been populated for this request.");
  }

  /**
   * Get a {@link TraineeIdentity} if the current request's user is a trainee, otherwise throw an
   * exception.
   *
   * @return The {@link TraineeIdentity} of the current request's user.
   * @throws IllegalArgumentException If the request user is not a trainee.
   */
  public TraineeIdentity requireTraineeIdentity() {
    if (!traineeIdentity.isComplete()) {
      throw new IllegalArgumentException("Action performed by wrong user type, must be a trainee.");
    }
    return traineeIdentity;
  }

  /**
   * Get a {@link AdminIdentity} if the current request's user is an admin, otherwise throw an
   * exception.
   *
   * @return The {@link AdminIdentity} of the current request's user.
   * @throws IllegalArgumentException If the request user is not an admin.
   */
  public AdminIdentity requireAdminIdentity() {
    if (!adminIdentity.isComplete()) {
      throw new IllegalArgumentException("Action performed by wrong user type, must be an admin.");
    }
    return adminIdentity;
  }
}
