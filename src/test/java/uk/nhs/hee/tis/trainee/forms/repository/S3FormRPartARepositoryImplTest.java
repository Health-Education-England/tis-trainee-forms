package uk.nhs.hee.tis.trainee.forms.repository;

import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.Declaration;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartA;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartAService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@ExtendWith(MockitoExtension.class)
class S3FormRPartARepositoryImplTest {

  private static final String KEY = "object.name";
  private static final UUID DEFAULT_ID = UUID.randomUUID();
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final String DEFAULT_TYPE_OF_WORK = "DEFAULT_TYPE_OF_WORK";
  private static final LocalDate DEFAULT_WORK_START_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final LocalDate DEFAULT_WORk_END_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final String DEFAULT_WORK_TRAINING_POST = "DEFAULT_WORK_TRAINING_POST";
  private static final String DEFAULT_WORK_SITE = "DEFAULT_WORK_SITE";
  private static final String DEFAULT_WORK_SITE_LOCATION = "DEFAULT_WORK_SITE_LOCATION";
  private static final Integer DEFAULT_TOTAL_LEAVE = 10;
  private static final Boolean DEFAULT_IS_HONEST = true;
  private static final Boolean DEFAULT_IS_HEALTHY = true;
  private static final String DEFAULT_HEALTHY_STATEMENT = "DEFAULT_HEALTHY_STATEMENT";
  private static final Boolean DEFAULT_HAVE_PREVIOUS_DECLARATIONS = true;
  private static final String DEFAULT_PREVIOUS_DECLARATION_TYPE = "Signification event";
  private static final LocalDate DEFAULT_PREVIOUS_DATE_OF_ENTRY = LocalDate
      .now(ZoneId.systemDefault());
  private static final String DEFAULT_PREVIOUS_DECLARATION_SUMMARY =
      "DEFAULT_PREVIOUS_DECLARATION_SUMMARY";
  private static final Boolean DEFAULT_HAVE_CURRENT_DECLARATIONS = true;
  private static final String DEFAULT_CURRENT_DECLARATION_TYPE = "Signification event";
  private static final LocalDate DEFAULT_CURRENT_DATE_OF_ENTRY = LocalDate
      .now(ZoneId.systemDefault());
  private static final String DEFAULT_CURRENT_DECLARATION_SUMMARY =
      "DEFAULT_CURRENT_DECLARATION_SUMMARY";
  private static final LocalDateTime DEFAULT_SUBMISSION_DATE = LocalDateTime.now();
  private static final String DEFAULT_SUBMISSION_DATE_STRING = DEFAULT_SUBMISSION_DATE.format(
      DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  private static final String DEFAULT_FORM_ID = "my-first-cloud-object-id";
  private static final Map<String, String> DEFAULT_UNSUBMITTED_METADATA = Map
      .of("id", DEFAULT_FORM_ID, "formtype", "inform", "lifecyclestate",
          LifecycleState.UNSUBMITTED.name(), "submissiondate",
          DEFAULT_SUBMISSION_DATE_STRING, "traineeid",
          DEFAULT_TRAINEE_TIS_ID);
  private static ObjectMapper objectMapper;
  private S3FormRPartARepositoryImpl repo;
  @Mock
  private AmazonS3 s3Mock;
  @Mock
  private ObjectListing s3ListingMock;
  @Captor
  private ArgumentCaptor<PutObjectRequest> putRequestCaptor;
  private FormRPartA entity;
  private Work work;
  private Declaration previousDeclaration;
  private Declaration currentDeclaration;
  private String bucketName;

  @BeforeAll
  static void beforeAll() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
  }

  @BeforeEach
  void setup() {
    bucketName = "whole-in-bucket";
    repo = new S3FormRPartARepositoryImpl(s3Mock, objectMapper, bucketName);

    entity = createEntity();
  }

  /**
   * Set up an FormRPartA.
   *
   * @return form with all default values
   */
  FormRPartA createEntity() {
    FormRPartA entity = new FormRPartA();
    entity.setId(DEFAULT_ID);
    entity.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    entity.setForename(DEFAULT_FORENAME);
    entity.setSurname(DEFAULT_SURNAME);
    entity.setLifecycleState(LifecycleState.DRAFT);
    return entity;
  }

  @Test
  void shouldSaveSubmittedFormRPartA() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);

    FormRPartA actual = repo.save(entity);
    assertThat("Unexpected form ID.", actual.getId(), notNullValue());
    verify(s3Mock).putObject(putRequestCaptor.capture());
    PutObjectRequest actualRequest = putRequestCaptor.getValue();
    assertThat("Unexpected Bucket Name.", actualRequest.getBucketName(), is(bucketName));
    assertThat("Unexpected Object Key.", actualRequest.getKey(),
        is(String.join("/", DEFAULT_TRAINEE_TIS_ID, "forms", FormRPartAService.FORM_TYPE,
            entity.getId() + ".json")));
    Map<String, String> expectedMetadata = Map
        .of("id", entity.getId().toString(), "name", entity.getId() + ".json", "type", "json",
            "formtype", FormRPartAService.FORM_TYPE, "lifecyclestate", LifecycleState.SUBMITTED.name(),
            "submissiondate", DEFAULT_SUBMISSION_DATE_STRING, "traineeid", DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected metadata.", actualRequest.getMetadata().getUserMetadata().entrySet(),
        containsInAnyOrder(expectedMetadata.entrySet().toArray(new Entry[0])));
  }

  @Test
  void shouldThrowExceptionWhenFormRPartANotSaved() {
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    when(s3Mock.putObject(any())).thenThrow(new AmazonServiceException("Expected Exception"));

    Exception actual = assertThrows(RuntimeException.class, () -> repo.save(entity));
    assertThat("Unexpected exception type.", actual instanceof ApplicationException);
  }

  @Test
  void shouldGetFormRPartAsByTraineeTisId() {
    when(s3Mock.listObjects(bucketName, DEFAULT_TRAINEE_TIS_ID + "/forms/formr-a"))
        .thenReturn(
            s3ListingMock);
    S3ObjectSummary s3Summary = new S3ObjectSummary();
    s3Summary.setKey(KEY);
    String otherKey = KEY + "w/error";
    S3ObjectSummary errorSummary = new S3ObjectSummary();
    errorSummary.setKey(otherKey);
    List<S3ObjectSummary> cloudStoredEntities = List.of(s3Summary, errorSummary);
    when(s3ListingMock.getObjectSummaries()).thenReturn(cloudStoredEntities);
    ObjectMetadata metadata = new ObjectMetadata();
    metadata.setUserMetadata(DEFAULT_UNSUBMITTED_METADATA);
    when(s3Mock.getObjectMetadata(bucketName, KEY)).thenReturn(metadata);
    when(s3Mock.getObjectMetadata(bucketName, otherKey))
        .thenThrow(new AmazonServiceException("Expected Exception"));

    List<FormRPartA> entities = repo.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected numbers of forms.", entities.size(), is(1));

    FormRPartA entity = entities.get(0);
    assertThat("Unexpected form ID.", entity.getId(), is(DEFAULT_FORM_ID));
    assertThat("Unexpected trainee ID.", entity.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected submitted date.", entity.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycle state.", entity.getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));
  }

  @Test
  void shouldGetFormRPartAFromCloudStorageById() {
    InputStream jsonFormRPartA = getClass().getResourceAsStream("/forms/testFormRPartA.json");
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(jsonFormRPartA);
    when(s3Mock.getObject(bucketName,
        DEFAULT_TRAINEE_TIS_ID + "/forms/formr-a/" + DEFAULT_ID + ".json"))
        .thenReturn(s3Object);

    Optional<FormRPartA> actual = repo.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected empty optional.", actual.isPresent());
    FormRPartA entity = actual.get();
    assertThat("Unexpected form ID.", entity.getId(), both(not(DEFAULT_ID)).and(notNullValue()));
    assertThat("Unexpected trainee ID.", entity.getTraineeTisId(),
        both(not(DEFAULT_TRAINEE_TIS_ID)).and(notNullValue()));
    assertThat("Unexpected forename.", entity.getForename(),
        both(not(DEFAULT_FORENAME)).and(notNullValue()));
    assertThat("Unexpected surname.", entity.getSurname(),
        both(not(DEFAULT_SURNAME)).and(notNullValue()));
    assertThat("Unexpected status.", entity.getLifecycleState(), is(SUBMITTED));
  }

  @Test
  void findByIdAndTraineeIdShouldReturnEmpty() {
    AmazonServiceException awsException = new AmazonServiceException("Expected Exception");
    awsException.setStatusCode(404);
    when(s3Mock.getObject(bucketName, "1/forms/formr-a/DEFAULT_ID.json"))
        .thenThrow(awsException);
    assertThat("Unexpected Optional content.",
        repo.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(classes = {AmazonServiceException.class, SdkClientException.class})
  void findByIdAndTraineeIdShouldThrowException(Class clazz) throws Exception {
    when(s3Mock.getObject(bucketName, "1/forms/formr-a/DEFAULT_ID.json"))
        .thenThrow((Exception) clazz.getDeclaredConstructor(String.class).newInstance("Expected"));
    assertThrows(ApplicationException.class,
        () -> repo.findByIdAndTraineeTisId(DEFAULT_ID, DEFAULT_TRAINEE_TIS_ID));
  }

}
