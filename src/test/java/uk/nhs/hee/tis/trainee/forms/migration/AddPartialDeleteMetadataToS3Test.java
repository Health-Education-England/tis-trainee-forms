/*
 * The MIT License (MIT)
 *
 * Copyright 2023 Crown Copyright (Health Education England)
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

package uk.nhs.hee.tis.trainee.forms.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.DeleteType;

class AddPartialDeleteMetadataToS3Test {

  private static final String BUCKET_NAME = "test-bucket";
  private static final String FIXED_FIELDS =
      "id,traineeTisId,lifecycleState,submissionDate,lastModifiedDate";

  private AddPartialDeleteMetadataToS3 migration;
  private ArgumentCaptor<PutObjectRequest> putRequestCaptor;
  private AmazonS3 s3;
  private ObjectListing objects;
  private ObjectListing objects2;
  private S3ObjectSummary objectSummary1;
  private S3ObjectSummary objectSummary2;
  private S3ObjectSummary objectSummary3;
  private S3Object object1;
  private S3Object object2;
  private S3Object object3;
  private InputStream objectContent1;
  private InputStream objectContent2;
  private InputStream objectContent3;
  private ObjectMetadata[] metadatas;
  private ObjectMetadata metadata1;
  private ObjectMetadata metadata2;
  private ObjectMetadata metadata3;

  @BeforeEach
  void setUp() {
    s3 = mock(AmazonS3.class);
    putRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    Environment env = mock(Environment.class);
    when(env.getProperty("application.file-store.bucket")).thenReturn(BUCKET_NAME);
    migration = new AddPartialDeleteMetadataToS3(s3, env);

    // set up ObjectListing
    objectSummary1 = new S3ObjectSummary();
    objectSummary1.setKey("1");
    objectSummary2 = new S3ObjectSummary();
    objectSummary2.setKey("2");
    objectSummary3 = new S3ObjectSummary();
    objectSummary3.setKey("3");

    objects = new ObjectListing();
    objects.setTruncated(true);
    List<S3ObjectSummary> objectSummaries = objects.getObjectSummaries();
    objectSummaries.add(objectSummary1);
    objectSummaries.add(objectSummary2);

    objects2 = new ObjectListing();
    objects2.setTruncated(false);
    List<S3ObjectSummary> objectSummaries2 = objects2.getObjectSummaries();
    objectSummaries2.add(objectSummary3);

    // set up S3Objects
    objectContent1 = new ByteArrayInputStream("{\"id\":\"1\"}".getBytes());
    objectContent2 = new ByteArrayInputStream("{\"id\":\"2\"}".getBytes());
    objectContent3 = new ByteArrayInputStream("{\"id\":\"3\"}".getBytes());

    metadata1 = new ObjectMetadata();
    metadata1.addUserMetadata("metaName1", "mataValue1");
    metadata1.addUserMetadata("metaName2", "mataValue2");

    metadata2 = new ObjectMetadata();
    metadata2.addUserMetadata("metaName1", "mataValue1");
    metadata2.addUserMetadata("metaName2", "mataValue2");

    metadata3 = new ObjectMetadata();
    metadata3.addUserMetadata("metaName1", "mataValue1");
    metadata3.addUserMetadata("metaName2", "mataValue2");

    metadatas = new ObjectMetadata[]{metadata1, metadata2, metadata3};

    object1 = new S3Object();
    object1.setBucketName(BUCKET_NAME);
    object1.setKey("1");
    object1.setObjectContent(objectContent1);
    object1.setObjectMetadata(metadata1);

    object2 = new S3Object();
    object2.setBucketName(BUCKET_NAME);
    object2.setKey("2");
    object2.setObjectContent(objectContent2);
    object2.setObjectMetadata(metadata2);

    object3 = new S3Object();
    object3.setBucketName(BUCKET_NAME);
    object3.setKey("3");
    object3.setObjectContent(objectContent3);
    object3.setObjectMetadata(metadata3);
  }

  @Test
  void shouldAddMetadataToObject() throws IOException {
    when(s3.listObjects(BUCKET_NAME)).thenReturn(objects);
    when(s3.listNextBatchOfObjects(objects)).thenReturn(objects2);
    when(s3.getObject(BUCKET_NAME, "1")).thenReturn(object1);
    when(s3.getObject(BUCKET_NAME, "2")).thenReturn(object2);
    when(s3.getObject(BUCKET_NAME, "3")).thenReturn(object3);

    migration.migrate();

    verify(s3, times(3)).putObject(putRequestCaptor.capture());
    List<PutObjectRequest> putObjectRequests = putRequestCaptor.getAllValues();
    assertThat("Unexpected put object request count.",
        putObjectRequests.size(), CoreMatchers.is(3));

    int assertId = 1;
    for (PutObjectRequest putObjectRequest : putObjectRequests) {
      assertThat("Unexpected bucket.",
          putObjectRequest.getBucketName(), is(BUCKET_NAME));
      assertThat("Unexpected key.",
          putObjectRequest.getKey(), is(Integer.toString(assertId)));
      final InputStream resultInputStream = putObjectRequest.getInputStream();
      ObjectMapper mapper = new ObjectMapper();
      Map<String, Object> resultJsonMap = mapper.readValue(resultInputStream, Map.class);
      assertThat("Unexpected input stream.",
          resultJsonMap.get("id"), is(Integer.toString(assertId)));

      final Map<String, String> resultUserMetadata =
          putObjectRequest.getMetadata().getUserMetadata();
      metadatas[assertId - 1].getUserMetadata().entrySet().stream()
          .forEach(entry -> assertThat(resultUserMetadata.entrySet(), hasItem(entry)));
      assertThat("Unexpected deletetype in object Metadata.",
          resultUserMetadata.get("deletetype"), is(DeleteType.PARTIAL.name()));
      assertThat("Unexpected fixedfields in object Metadata.",
          resultUserMetadata.get("fixedfields"), is(FIXED_FIELDS));

      assertId++;
    }
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(s3);
  }
}
