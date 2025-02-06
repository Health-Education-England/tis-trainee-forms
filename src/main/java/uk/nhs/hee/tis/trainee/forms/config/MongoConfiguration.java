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

package uk.nhs.hee.tis.trainee.forms.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;

/**
 * Configuration for the Mongo database.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfiguration {

  private final MongoTemplate template;

  MongoConfiguration(MongoTemplate template) {
    this.template = template;
  }

  /**
   * Add custom indexes to the Mongo collections.
   */
  @PostConstruct
  public void initIndexes() {
    IndexOperations formRpartAindexOps = template.indexOps(FormRPartA.class);
    formRpartAindexOps.ensureIndex(new Index().on("traineeTisId", Sort.Direction.ASC));
    formRpartAindexOps.ensureIndex(new Index().on("lifecycleState", Sort.Direction.ASC));

    IndexOperations formRpartBindexOps = template.indexOps(FormRPartB.class);
    formRpartBindexOps.ensureIndex(new Index().on("traineeTisId", Sort.Direction.ASC));
    formRpartBindexOps.ensureIndex(new Index().on("lifecycleState", Sort.Direction.ASC));

    IndexOperations ltftFormIndexOps = template.indexOps(LtftForm.class);
    ltftFormIndexOps.ensureIndex(new Index().on("traineeId", Sort.Direction.ASC));
  }
}
