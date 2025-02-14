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

package uk.nhs.hee.tis.trainee.forms.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

/**
 * A repository for LTFT forms.
 */
@Repository
public interface LtftFormRepository extends MongoRepository<LtftForm, UUID> {

  /**
   * Find all LTFT forms belonging to the given trainee, ordered by last modified.
   *
   * @param traineeId The ID of the trainee.
   * @return A list of found LTFT forms.
   */
  List<LtftForm> findByTraineeTisIdOrderByLastModified(String traineeId);

  /**
   * Count all LTFT forms with one of the given current states.
   *
   * @param states The states to include in the count.
   * @return The number of found LTFT forms.
   */
  long countByStatus_Current_StateIn(Set<LifecycleState> states);

  /**
   * Find the LTFT form with the given id that belongs to the given trainee.
   *
   * @param traineeId The ID of the trainee.
   * @param id        The ID of the form.
   * @return The LTFT form, or optional empty if not found (or does not belong to trainee).
   */
  Optional<LtftForm> findByTraineeTisIdAndId(String traineeId, UUID id);

}
