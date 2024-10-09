/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoining;

/**
 * An event for when Conditions of Joining are signed.
 *
 * @param traineeId             The trainee/person ID who signed the form.
 * @param programmeMembershipId The programme membership that COJ relates to.
 * @param programmeName         The name of the related programme.
 * @param conditionsOfJoining   The Conditions of Joining detail.
 */
public record ConditionsOfJoiningSignedEvent(

    @JsonAlias("personId")
    String traineeId,

    @JsonAlias("tisId")
    UUID programmeMembershipId,

    String programmeName,
    ConditionsOfJoining conditionsOfJoining) {

}
