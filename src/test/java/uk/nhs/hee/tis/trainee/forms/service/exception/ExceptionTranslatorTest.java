/*
 * The MIT License (MIT)
 *
 * Copyright 2020 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.service.exception;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.mongodb.MongoWriteException;
import com.mongodb.WriteError;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.hee.tis.trainee.forms.config.MongoConfiguration;

@ExtendWith(MockitoExtension.class)
class ExceptionTranslatorTest {

  @InjectMocks
  ExceptionTranslator translator;

  @Test
  void shouldReturnFormattedMongoWriteExceptionWhenNotIndexValidation() {
    WriteError writeError = new WriteError(40, "not validation", new BsonDocument());
    MongoWriteException exception = new MongoWriteException(writeError, null);
    ErrorVM error = translator.processMongoWriteError(exception);

    assertThat("Unexpected message.", error.getMessage(), is(ErrorConstants.ERR_PERSISTENCE));
    assertThat("Unexpected description.", error.getDescription(), is("not validation"));
    assertThat("Unexpected field errors.", error.getFieldErrors(), nullValue());
  }

  @Test
  void shouldReturnFormattedMongoWriteExceptionWhenNonSingleDraftIndexValidation() {
    WriteError writeError = new WriteError(11000, "not single draft", new BsonDocument());
    MongoWriteException exception = new MongoWriteException(writeError, null);
    ErrorVM error = translator.processMongoWriteError(exception);

    assertThat("Unexpected message.", error.getMessage(), is(ErrorConstants.ERR_VALIDATION));
    assertThat("Unexpected description.", error.getDescription(), is("not single draft"));
    assertThat("Unexpected field errors.", error.getFieldErrors(), nullValue());
  }

  @Test
  void shouldReturnFormattedMongoWriteExceptionWhenSingleDraftIndexValidation() {
    WriteError writeError = new WriteError(11000, MongoConfiguration.SINGLE_DRAFT_INDEX_NAME,
        new BsonDocument());
    MongoWriteException exception = new MongoWriteException(writeError, null);
    ErrorVM error = translator.processMongoWriteError(exception);

    assertThat("Unexpected message.", error.getMessage(), is(ErrorConstants.ERR_VALIDATION));
    assertThat("Unexpected description.", error.getDescription(),
        is(MongoConfiguration.SINGLE_DRAFT_INDEX_NAME));
    assertThat("Unexpected number of field errors.", error.getFieldErrors().size(), is(1));

    FieldErrorVM fieldError = error.getFieldErrors().get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("form"));
    assertThat("Unexpected field.", fieldError.getField(), is("lifecycleState"));
    assertThat("Unexpected field error message.", fieldError.getMessage(),
        is("Draft form already exists."));
  }
}
