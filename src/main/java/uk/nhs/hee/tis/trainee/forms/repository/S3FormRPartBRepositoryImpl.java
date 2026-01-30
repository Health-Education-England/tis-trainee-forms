/*
 * The MIT License (MIT)
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

package uk.nhs.hee.tis.trainee.forms.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.s3.S3Client;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;

/**
 * A cloud repository for Form-R Part B.
 *
 * @deprecated Form-R Part B uploads are for backwards compatibility only.
 */
@Slf4j
@Repository
@Deprecated(since = "0.62.0")
public class S3FormRPartBRepositoryImpl extends AbstractCloudRepository<FormRPartB> {

  /**
   * Instantiate an object repository.
   *
   * @param s3Client     client for S3 service
   * @param objectMapper mapper handles Json (de)serialisation
   * @param bucketName   the bucket that this repository provides persistence with
   */
  public S3FormRPartBRepositoryImpl(S3Client s3Client,
      ObjectMapper objectMapper, @Value("${application.file-store.bucket}") String bucketName) {
    super(s3Client, objectMapper, bucketName);
  }

  @Override
  protected String getObjectKeyTemplate() {
    return "%s/forms/formr-b/%s.json";
  }

  @Override
  protected String getObjectPrefixTemplate() {
    return "%s/forms/formr-b";
  }

}
