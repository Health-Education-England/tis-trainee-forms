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
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.DeleteType;

class AddPartialDeleteMetadataToS3Test {

  private static final String BUCKET_NAME = "test-bucket";
  private static final String FIXED_FIELDS =
      "id,traineeTisId,lifecycleState,submissionDate,lastModifiedDate";

  private AddPartialDeleteMetadataToS3 migration;
  private S3Client s3;
  private S3Object object1;
  private S3Object object2;
  private S3Object object3;
  private S3Object object4;
  private S3Object object5;
  private S3Object object6;
  private ResponseInputStream<GetObjectResponse> objectResponse1;
  private ResponseInputStream<GetObjectResponse> objectResponse2;
  private ResponseInputStream<GetObjectResponse> objectResponse3;
  private ResponseInputStream<GetObjectResponse> objectResponse4;
  private ResponseInputStream<GetObjectResponse> objectResponse5;
  private ResponseInputStream<GetObjectResponse> objectResponse6;

  @BeforeEach
  void setUp() {
    s3 = mock(S3Client.class);
    Environment env = mock(Environment.class);
    when(env.getProperty("application.file-store.bucket")).thenReturn(BUCKET_NAME);
    migration = new AddPartialDeleteMetadataToS3(s3, env);

    // set up S3Objects
    InputStream objectContent1 = new ByteArrayInputStream("{\"id\":\"1\"}".getBytes());
    InputStream objectContent2 = new ByteArrayInputStream("{\"id\":\"2\"}".getBytes());
    InputStream objectContent3 = new ByteArrayInputStream("{\"id\":\"3\"}".getBytes());
    InputStream objectContent4 = new ByteArrayInputStream("{\"id\":\"4\"}".getBytes());
    InputStream objectContent5 = new ByteArrayInputStream("{\"id\":\"5\"}".getBytes());
    InputStream objectContent6 = new ByteArrayInputStream("{\"id\":\"6\"}".getBytes());

    Map<String, String> metadata1 = Map.of(
        "metaName1", "metaValue1",
        "metaName2", "metaValue2"
    );

    Map<String, String> metadata2 = Map.of(
        "metaName1", "metaValue1",
        "metaName2", "metaValue2",
        "deletetype", DeleteType.HARD.name(),
        "fixedfields", FIXED_FIELDS
    );

    Map<String, String> metadata3 = Map.of(
        "metaName1", "metaValue1",
        "metaName2", "metaValue2",
        "deletetype", DeleteType.PARTIAL.name(),
        "fixedfields", ""
    );

    Map<String, String> metadata4 = Map.of(
        "metaName1", "metaValue1",
        "metaName2", "metaValue2",
        "deletetype", DeleteType.PARTIAL.name()
    );

    Map<String, String> metadata5 = Map.of(
        "metaName1", "metaValue1",
        "metaName2", "metaValue2",
        "fixedfields", FIXED_FIELDS
    );

    Map<String, String> metadata6 = Map.of(
        "metaName1", "metaValue1",
        "metaName2", "metaValue2",
        "deletetype", DeleteType.PARTIAL.name(),
        "fixedfields", FIXED_FIELDS
    );

    object1 = S3Object.builder().key("1").build();
    objectResponse1 = new ResponseInputStream<>(GetObjectResponse.builder().metadata(metadata1).build(),
        objectContent1);

    object2 = S3Object.builder().key("2").build();
    objectResponse2 = new ResponseInputStream<>(GetObjectResponse.builder().metadata(metadata2).build(),
        objectContent2);

    object3 = S3Object.builder().key("3").build();
    objectResponse3 = new ResponseInputStream<>(GetObjectResponse.builder().metadata(metadata3).build(),
        objectContent3);

    object4 = S3Object.builder().key("4").build();
    objectResponse4 = new ResponseInputStream<>(GetObjectResponse.builder().metadata(metadata4).build(),
        objectContent4);

    object5 = S3Object.builder().key("5").build();
    objectResponse5 = new ResponseInputStream<>(GetObjectResponse.builder().metadata(metadata5).build(),
        objectContent5);

    object6 = S3Object.builder().key("6").build();
    objectResponse6 = new ResponseInputStream<>(GetObjectResponse.builder().metadata(metadata6).build(),
        objectContent6);
  }

  @Test
  void shouldAddMetadataToObject() throws IOException {
    ListObjectsV2Iterable objectsIterable = mock(ListObjectsV2Iterable.class);
    when(s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(BUCKET_NAME).build())).thenReturn(objectsIterable);

    ListObjectsV2Response objects1 = ListObjectsV2Response.builder()
        .contents(object1, object2, object3).build();
    ListObjectsV2Response objects2 = ListObjectsV2Response.builder()
        .contents(object4, object5, object6).build();
    when(objectsIterable.stream()).thenReturn(Stream.of(objects1, objects2));

    when(s3.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key("1").build())).thenReturn(objectResponse1);
    when(s3.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key("2").build())).thenReturn(objectResponse2);
    when(s3.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key("3").build())).thenReturn(objectResponse3);
    when(s3.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key("4").build())).thenReturn(objectResponse4);
    when(s3.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key("5").build())).thenReturn(objectResponse5);
    when(s3.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key("6").build())).thenReturn(objectResponse6);

    migration.migrate();

    ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<RequestBody> requestBodyCaptor = ArgumentCaptor.captor();
    verify(s3, times(5)).putObject(requestCaptor.capture(), requestBodyCaptor.capture());
    List<PutObjectRequest> putObjectRequests = requestCaptor.getAllValues();
    assertThat("Unexpected put object request count.",
        putObjectRequests.size(), CoreMatchers.is(5));

    int assertId = 1;
    for (PutObjectRequest putObjectRequest : putObjectRequests) {
      assertThat("Unexpected bucket.", putObjectRequest.bucket(), is(BUCKET_NAME));
      assertThat("Unexpected key.", putObjectRequest.key(), is(Integer.toString(assertId)));
      InputStream resultInputStream = requestBodyCaptor.getAllValues().get(assertId - 1).contentStreamProvider().newStream();
      ObjectMapper mapper = new ObjectMapper();
      Map<String, Object> resultJsonMap = mapper.readValue(resultInputStream, Map.class);
      assertThat("Unexpected input stream.", resultJsonMap.get("id"), is(Integer.toString(assertId)));

      final Map<String, String> resultUserMetadata = putObjectRequest.metadata();
      assertThat("Unexpected metadata count.", resultUserMetadata.size(), is(4));
      assertThat("Unexpected metadata.", resultUserMetadata.get("metaName1"), is("metaValue1"));
      assertThat("Unexpected metadata.", resultUserMetadata.get("metaName2"), is("metaValue2"));
      assertThat("Unexpected deletetype in object Metadata.", resultUserMetadata.get("deletetype"), is(DeleteType.PARTIAL.name()));
      assertThat("Unexpected fixedfields in object Metadata.", resultUserMetadata.get("fixedfields"), is(FIXED_FIELDS));

      assertId++;
    }
  }

  @Test
  void shouldNotAttemptRollback() {
    migration.rollback();
    verifyNoInteractions(s3);
  }
}
