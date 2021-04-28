/*
 * The MIT License (MIT)
 *
 * Copyright 2021 Crown Copyright (Health Education England)
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

import javax.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import uk.nhs.hee.tis.trainee.forms.config.FeatureConfigurationProperties.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;

@Configuration
public class MongoConfiguration {

  public static final String SINGLE_DRAFT_INDEX_NAME = "singleDraftPerTrainee";

  private static final String TRAINEE_ID_FIELD = "traineeTisId";

  private final MongoTemplate template;

  MongoConfiguration(MongoTemplate template) {
    this.template = template;
  }

  /**
   * Add custom indexes to the Mongo collections.
   */
  @PostConstruct
  public void initIndexes() {
    PartialIndexFilter draftPartial = PartialIndexFilter
        .of(new Document("lifecycleState", "DRAFT"));
    Index singleDraftIndex = new Index()
        .named(SINGLE_DRAFT_INDEX_NAME)
        .on(TRAINEE_ID_FIELD, Direction.ASC)
        .partial(draftPartial)
        .unique();

    template.indexOps(FormRPartA.class).ensureIndex(singleDraftIndex);
    template.indexOps(FormRPartB.class).ensureIndex(singleDraftIndex);
  }
}
