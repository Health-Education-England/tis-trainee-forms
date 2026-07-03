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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserIdentityResolverTest {

  private UserIdentityResolver identityResolver;

  private AdminIdentity adminIdentity;
  private TraineeIdentity traineeIdentity;

  @BeforeEach
  void setUp() {
    adminIdentity = new AdminIdentity();
    traineeIdentity = new TraineeIdentity();
    identityResolver = new UserIdentityResolver(adminIdentity, traineeIdentity);
  }

  @Test
  void shouldThrowExceptionWhenNoUser() {
    assertThrows(IllegalStateException.class, () -> identityResolver.getUserIdentity());
  }

  @Test
  void shouldGetUserIdentityWhenAdminUser() {
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@example.com");
    adminIdentity.setGroups(Set.of("Test Group"));

    UserIdentity userIdentity = identityResolver.getUserIdentity();

    assertThat("Unexpected identity.", userIdentity, is(adminIdentity));
  }

  @Test
  void shouldGetUserIdentityWhenTraineeUser() {
    traineeIdentity.setTraineeId("123");

    UserIdentity userIdentity = identityResolver.getUserIdentity();

    assertThat("Unexpected identity.", userIdentity, is(traineeIdentity));
  }

  @Test
  void shouldThrowExceptionWhenAdminRequiredAndTraineeUser() {
    traineeIdentity.setTraineeId("123");

    assertThrows(IllegalArgumentException.class, () -> identityResolver.requireAdminIdentity());
  }

  @Test
  void shouldReturnAdminIdentityWhenAdminRequiredAndAdminUser() {
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@example.com");
    adminIdentity.setGroups(Set.of("Test Group"));

    AdminIdentity requiredIdentity = identityResolver.requireAdminIdentity();

    assertThat("Unexpected identity.", requiredIdentity, is(adminIdentity));
  }

  @Test
  void shouldThrowExceptionWhenTraineeRequiredAndAdminUser() {
    adminIdentity.setName("Ad Min");
    adminIdentity.setEmail("ad.min@example.com");
    adminIdentity.setGroups(Set.of("Test Group"));

    assertThrows(IllegalArgumentException.class, () -> identityResolver.requireTraineeIdentity());
  }

  @Test
  void shouldReturnTraineeIdentityWhenTraineeRequiredAndTraineeUser() {
    traineeIdentity.setTraineeId("123");

    TraineeIdentity requiredIdentity = identityResolver.requireTraineeIdentity();

    assertThat("Unexpected identity.", requiredIdentity, is(traineeIdentity));
  }
}
