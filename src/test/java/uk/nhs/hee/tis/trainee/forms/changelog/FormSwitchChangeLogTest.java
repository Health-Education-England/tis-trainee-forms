/*
 * The MIT License (MIT)
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

package uk.nhs.hee.tis.trainee.forms.changelog;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.decorator.impl.MongockTemplate;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import uk.nhs.hee.tis.trainee.forms.model.FormSwitch;

@ExtendWith(MockitoExtension.class)
class FormSwitchChangeLogTest {

  private FormSwitchChangeLog changeLog;

  @Mock
  private MongockTemplate template;

  @BeforeEach
  void setUp() {
    changeLog = new FormSwitchChangeLog();
  }

  @Test
  void shouldSetCovidDeclarationSwitch() {
    changeLog.setCovidDeclarationSwitch(template);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(template).upsert(queryCaptor.capture(), updateCaptor.capture(), eq(FormSwitch.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected query size.", queryObject.size(), is(1));
    assertThat("Unexpected query value.", queryObject.get("name"), is("COVID"));

    Update update = updateCaptor.getValue();
    assertThat("Unexpected update size.", update.getUpdateObject().size(), is(1));
    assertThat("Unexpected update target.", update.modifies("enabled"), is(true));
  }
}
