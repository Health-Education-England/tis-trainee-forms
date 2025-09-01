package uk.nhs.hee.tis.trainee.forms.repository;

import static java.util.Map.entry;
import static org.hamcrest.CoreMatchers.both;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.DeleteType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.model.Declaration;
import uk.nhs.hee.tis.trainee.forms.model.FormRPartB;
import uk.nhs.hee.tis.trainee.forms.model.Work;
import uk.nhs.hee.tis.trainee.forms.service.FormRPartBService;
import uk.nhs.hee.tis.trainee.forms.service.exception.ApplicationException;

@ExtendWith(MockitoExtension.class)
class S3FormRPartBRepositoryImplTest {

  private static final String KEY = "object.name";
  private static final UUID DEFAULT_ID = UUID.randomUUID();
  private static final String DEFAULT_ID_STRING = DEFAULT_ID.toString();
  private static final String DEFAULT_TRAINEE_TIS_ID = "1";
  private static final String DEFAULT_FORENAME = "DEFAULT_FORENAME";
  private static final String DEFAULT_SURNAME = "DEFAULT_SURNAME";
  private static final String DEFAULT_TYPE_OF_WORK = "DEFAULT_TYPE_OF_WORK";
  private static final LocalDate DEFAULT_WORK_START_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final LocalDate DEFAULT_WORk_END_DATE = LocalDate.now(ZoneId.systemDefault());
  private static final String DEFAULT_WORK_TRAINING_POST = "DEFAULT_WORK_TRAINING_POST";
  private static final String DEFAULT_WORK_SITE = "DEFAULT_WORK_SITE";
  private static final String DEFAULT_WORK_SITE_LOCATION = "DEFAULT_WORK_SITE_LOCATION";
  private static final String DEFAULT_WORK_SITE_KNOWN_AS = "DEFAULT_WORK_SITE_KNOWN_AS";
  private static final Integer DEFAULT_TOTAL_LEAVE = 10;
  private static final Boolean DEFAULT_IS_HONEST = true;
  private static final Boolean DEFAULT_IS_HEALTHY = true;
  private static final String DEFAULT_HEALTHY_STATEMENT = "DEFAULT_HEALTHY_STATEMENT";
  private static final Boolean DEFAULT_HAVE_PREVIOUS_DECLARATIONS = true;
  private static final String DEFAULT_PREVIOUS_DECLARATION_TYPE = "Signification event";
  private static final Boolean DEFAULT_IS_ARCP = false;
  private static final String DEFAULT_PROGRAMME_MEMBERSHIP_ID = UUID.randomUUID().toString();
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
  private static final LocalDate DATE_FORMAT_SUBMISSION_DATE = LocalDate
      .of(2020, 8, 29);
  private static final LocalDateTime DATE_FORMAT_SUBMISSION_DATE_PARSED =
      DATE_FORMAT_SUBMISSION_DATE.atStartOfDay();
  private static final String DATE_FORMAT_DATE_STRING = DATE_FORMAT_SUBMISSION_DATE.format(
      DateTimeFormatter.ISO_LOCAL_DATE);
  private static final String DEFAULT_FORM_ID = UUID.randomUUID().toString();
  private static final Map<String, String> DEFAULT_UNSUBMITTED_METADATA = Map
      .of("id", DEFAULT_FORM_ID,
          "formtype", "inform",
          "lifecyclestate", LifecycleState.UNSUBMITTED.name(),
          "submissiondate", DEFAULT_SUBMISSION_DATE_STRING,
          "traineeid", DEFAULT_TRAINEE_TIS_ID,
          "programmemembershipid", DEFAULT_PROGRAMME_MEMBERSHIP_ID);
  private static final Map<String, String> UNSUBMITTED_METADATA_DATE_FORMAT_SUBMISSIONDATE = Map
      .of("id", DEFAULT_FORM_ID,
          "formtype", "inform",
          "lifecyclestate",
          LifecycleState.UNSUBMITTED.name(),
          "submissiondate", DATE_FORMAT_DATE_STRING,
          "traineeid", DEFAULT_TRAINEE_TIS_ID,
          "programmemembershipid", DEFAULT_PROGRAMME_MEMBERSHIP_ID);
  private static final Boolean DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS = true;
  private static final Boolean DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS = true;
  private static final String FIXED_FIELDS =
      "id,traineeTisId,lifecycleState,submissionDate,lastModifiedDate";
  private static ObjectMapper objectMapper;
  private S3FormRPartBRepositoryImpl repo;
  @Mock
  private S3Client s3Mock;
  @Mock
  private ListObjectsV2Response s3ListingMock;
  @Captor
  private ArgumentCaptor<PutObjectRequest> putRequestCaptor;
  private FormRPartB entity;
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
    repo = new S3FormRPartBRepositoryImpl(s3Mock, objectMapper, bucketName);
    work = createWork();
    previousDeclaration = createDeclaration(true);
    currentDeclaration = createDeclaration(false);

    entity = createEntity();
  }

  /**
   * Set up an FormRPartB.
   *
   * @return form with all default values
   */
  FormRPartB createEntity() {
    FormRPartB entity = new FormRPartB();
    entity.setId(DEFAULT_ID);
    entity.setTraineeTisId(DEFAULT_TRAINEE_TIS_ID);
    entity.setForename(DEFAULT_FORENAME);
    entity.setSurname(DEFAULT_SURNAME);
    entity.setWork(Collections.singletonList(work));
    entity.setTotalLeave(DEFAULT_TOTAL_LEAVE);
    entity.setIsHonest(DEFAULT_IS_HONEST);
    entity.setIsHealthy(DEFAULT_IS_HEALTHY);
    entity.setHealthStatement(DEFAULT_HEALTHY_STATEMENT);
    entity.setHavePreviousDeclarations(DEFAULT_HAVE_PREVIOUS_DECLARATIONS);
    entity.setPreviousDeclarations(Collections.singletonList(previousDeclaration));
    entity.setPreviousDeclarationSummary(DEFAULT_PREVIOUS_DECLARATION_SUMMARY);
    entity.setHaveCurrentDeclarations(DEFAULT_HAVE_CURRENT_DECLARATIONS);
    entity.setCurrentDeclarations(Collections.singletonList(currentDeclaration));
    entity.setCurrentDeclarationSummary(DEFAULT_CURRENT_DECLARATION_SUMMARY);
    entity.setLifecycleState(LifecycleState.DRAFT);
    entity.setHaveCurrentUnresolvedDeclarations(DEFAULT_HAVE_CURRENT_UNRESOLVED_DECLARATIONS);
    entity.setHavePreviousUnresolvedDeclarations(DEFAULT_HAVE_PREVIOUS_UNRESOLVED_DECLARATIONS);
    return entity;
  }

  /**
   * Set up data for work.
   *
   * @return work with default values
   */
  Work createWork() {
    Work work = new Work();
    work.setTypeOfWork(DEFAULT_TYPE_OF_WORK);
    work.setStartDate(DEFAULT_WORK_START_DATE);
    work.setEndDate(DEFAULT_WORk_END_DATE);
    work.setTrainingPost(DEFAULT_WORK_TRAINING_POST);
    work.setSite(DEFAULT_WORK_SITE);
    work.setSiteLocation(DEFAULT_WORK_SITE_LOCATION);
    work.setSiteKnownAs(DEFAULT_WORK_SITE_KNOWN_AS);
    return work;
  }

  /**
   * Set up data for previous declaration.
   *
   * @param isPrevious indicates whether to use previous values
   * @return declaration with default values
   */
  Declaration createDeclaration(boolean isPrevious) {
    Declaration declaration = new Declaration();
    if (isPrevious) {
      declaration.setDeclarationType(DEFAULT_PREVIOUS_DECLARATION_TYPE);
      declaration.setDateOfEntry(DEFAULT_PREVIOUS_DATE_OF_ENTRY);
    } else {
      declaration.setDeclarationType(DEFAULT_CURRENT_DECLARATION_TYPE);
      declaration.setDateOfEntry(DEFAULT_CURRENT_DATE_OF_ENTRY);
    }
    return declaration;
  }

  @Test
  void shouldSaveSubmittedFormRPartB() {
    entity.setId(null);
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    entity.setIsArcp(DEFAULT_IS_ARCP);
    entity.setProgrammeMembershipId(UUID.fromString(DEFAULT_PROGRAMME_MEMBERSHIP_ID));

    FormRPartB actual = repo.save(entity);
    assertThat("Unexpected form ID.", actual.getId(), notNullValue());
    verify(s3Mock).putObject(putRequestCaptor.capture(), any(RequestBody.class));
    PutObjectRequest actualRequest = putRequestCaptor.getValue();
    assertThat("Unexpected Bucket Name.", actualRequest.bucket(), is(bucketName));
    assertThat("Unexpected Object Key.", actualRequest.key(),
        is(String.join("/", DEFAULT_TRAINEE_TIS_ID, "forms", FormRPartBService.FORM_TYPE,
            entity.getId() + ".json")));
    Map<String, String> expectedMetadata = Map.ofEntries(
        entry("id", entity.getId().toString()),
        entry("name", entity.getId() + ".json"),
        entry("type", "json"),
        entry("isarcp", DEFAULT_IS_ARCP.toString()),
        entry("programmemembershipid", DEFAULT_PROGRAMME_MEMBERSHIP_ID.toString()),
        entry("formtype", FormRPartBService.FORM_TYPE),
        entry("lifecyclestate", LifecycleState.SUBMITTED.name()),
        entry("submissiondate", DEFAULT_SUBMISSION_DATE_STRING),
        entry("traineeid", DEFAULT_TRAINEE_TIS_ID),
        entry("deletetype", DeleteType.PARTIAL.name()),
        entry("fixedfields", FIXED_FIELDS)
    );

    assertThat("Unexpected metadata.", actualRequest.metadata().entrySet(),
        containsInAnyOrder(expectedMetadata.entrySet().toArray(new Entry[0])));
  }

  @Test
  void shouldThrowExceptionWhenFormRPartBNotSaved() {
    entity.setLifecycleState(LifecycleState.SUBMITTED);
    entity.setSubmissionDate(DEFAULT_SUBMISSION_DATE);
    entity.setIsArcp(DEFAULT_IS_ARCP);
    entity.setProgrammeMembershipId(UUID.fromString(DEFAULT_PROGRAMME_MEMBERSHIP_ID));
    when(s3Mock.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(
        new AmazonServiceException("Expected Exception"));

    Exception actual = assertThrows(RuntimeException.class, () -> repo.save(entity));
    assertThat("Unexpected exception type.", actual instanceof ApplicationException);
  }

  @Test
  void shouldGetFormRPartBsByTraineeTisId() {
    when(s3Mock.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName)
        .prefix(DEFAULT_TRAINEE_TIS_ID + "/forms/formr-b").build())).thenReturn(s3ListingMock);
    String otherKey = KEY + "w/error";
    List<S3Object> s3Objects = List.of(
        S3Object.builder().key(KEY).build(),
        S3Object.builder().key(otherKey).build()
    );
    when(s3ListingMock.contents()).thenReturn(s3Objects);

    HeadObjectResponse metadataResponse = HeadObjectResponse.builder()
        .metadata(DEFAULT_UNSUBMITTED_METADATA).build();
    when(s3Mock.headObject(
        HeadObjectRequest.builder().bucket(bucketName).key(KEY).build())).thenReturn(
        metadataResponse);
    when(s3Mock.headObject(HeadObjectRequest.builder().bucket(bucketName).key(otherKey).build()))
        .thenThrow(new AmazonServiceException("Expected Exception"));

    List<FormRPartB> entities = repo.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected numbers of forms.", entities.size(), is(1));

    FormRPartB entity = entities.get(0);
    assertThat("Unexpected form ID.", entity.getId(), is(UUID.fromString(DEFAULT_FORM_ID)));
    assertThat("Unexpected trainee ID.", entity.getTraineeTisId(), is(DEFAULT_TRAINEE_TIS_ID));
    assertThat("Unexpected submitted date.", entity.getSubmissionDate(),
        is(DEFAULT_SUBMISSION_DATE));
    assertThat("Unexpected lifecycle state.", entity.getLifecycleState(),
        is(LifecycleState.UNSUBMITTED));
    assertThat("Unexpected programme membership ID.", entity.getProgrammeMembershipId(),
        is(UUID.fromString(DEFAULT_PROGRAMME_MEMBERSHIP_ID)));
  }

  @Test
  void shouldParseSubmissionDateWhenInDateFormat() {
    when(s3Mock.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName)
        .prefix(DEFAULT_TRAINEE_TIS_ID + "/forms/formr-b").build())).thenReturn(s3ListingMock);
    List<S3Object> s3Objects = List.of(S3Object.builder().key(KEY).build());
    when(s3ListingMock.contents()).thenReturn(s3Objects);

    HeadObjectResponse metadataResponse = HeadObjectResponse.builder()
        .metadata(UNSUBMITTED_METADATA_DATE_FORMAT_SUBMISSIONDATE).build();
    when(s3Mock.headObject(
        HeadObjectRequest.builder().bucket(bucketName).key(KEY).build())).thenReturn(
        metadataResponse);

    List<FormRPartB> entities = repo.findByTraineeTisId(DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected numbers of forms.", entities.size(), is(1));

    FormRPartB entity = entities.get(0);

    assertThat("Unexpected submitted date.", entity.getSubmissionDate(),
        is(DATE_FORMAT_SUBMISSION_DATE_PARSED));
  }

  @Test
  void shouldGetFormRPartBFromCloudStorageById() {
    InputStream jsonFormRPartB = getClass().getResourceAsStream("/forms/testFormRPartB.json");
    ResponseInputStream<GetObjectResponse> objectResponse = new ResponseInputStream<>(
        GetObjectResponse.builder().build(), jsonFormRPartB);
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName)
        .key(DEFAULT_TRAINEE_TIS_ID + "/forms/formr-b/" + DEFAULT_ID + ".json")
        .build())).thenReturn(objectResponse);

    Optional<FormRPartB> actual = repo.findByIdAndTraineeTisId(DEFAULT_ID_STRING,
        DEFAULT_TRAINEE_TIS_ID);

    assertThat("Unexpected empty optional.", actual.isPresent());
    FormRPartB entity = actual.get();
    assertThat("Unexpected form ID.", entity.getId(), both(not(DEFAULT_ID)).and(notNullValue()));
    assertThat("Unexpected trainee ID.", entity.getTraineeTisId(),
        both(not(DEFAULT_TRAINEE_TIS_ID)).and(notNullValue()));
    assertThat("Unexpected forename.", entity.getForename(),
        both(not(DEFAULT_FORENAME)).and(notNullValue()));
    assertThat("Unexpected surname.", entity.getSurname(),
        both(not(DEFAULT_SURNAME)).and(notNullValue()));
    assertThat("Unexpected work.", entity.getWork(),
        both(not(Collections.singletonList(work))).and(notNullValue()));
    assertThat("Unexpected total leave.", entity.getTotalLeave(),
        both(not(DEFAULT_TOTAL_LEAVE)).and(notNullValue()));
    assertThat("Unexpected isHonest flag.", entity.getIsHonest(),
        both(not(DEFAULT_IS_HONEST)).and(notNullValue()));
    assertThat("Unexpected isHealthy flag.", entity.getIsHealthy(),
        both(not(DEFAULT_IS_HEALTHY)).and(notNullValue()));
    assertThat("Unexpected health statement.", entity.getHealthStatement(), is(""));
    assertThat("Unexpected havePreviousDeclarations flag.", entity.getHavePreviousDeclarations(),
        is(false));
    assertThat("Unexpected previous declarations.", entity.getPreviousDeclarations(), empty());
    assertThat("Unexpected previous declaration summary.", entity.getPreviousDeclarationSummary(),
        nullValue());
    assertThat("Unexpected haveCurrentDeclarations flag.", entity.getHaveCurrentDeclarations(),
        is(false));
    assertThat("Unexpected current declarations.", entity.getCurrentDeclarations(), empty());
    assertThat("Unexpected current declaration summary.", entity.getCurrentDeclarationSummary(),
        nullValue());
    assertThat("Unexpected haveCurrentUnresolvedDeclarations flag.",
        entity.getHaveCurrentUnresolvedDeclarations(), is(false));
    assertThat("Unexpected havePreviousUnresolvedDeclarations flag.",
        entity.getHavePreviousUnresolvedDeclarations(), is(false));
    assertThat("Unexpected status.", entity.getLifecycleState(), is(SUBMITTED));
  }

  @Test
  void findByIdAndTraineeIdShouldReturnEmpty() {
    AmazonServiceException awsException = new AmazonServiceException("Expected Exception");
    awsException.setStatusCode(404);
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName)
        .key(String.format("1/forms/formr-b/%s.json", DEFAULT_ID)).build()))
        .thenThrow(awsException);
    assertThat("Unexpected Optional content.",
        repo.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(classes = {AmazonServiceException.class, SdkClientException.class})
  void findByIdAndTraineeIdShouldThrowException(Class clazz) throws Exception {
    when(s3Mock.getObject(GetObjectRequest.builder().bucket(bucketName)
        .key(String.format("1/forms/formr-b/%s.json", DEFAULT_ID)).build()))
        .thenThrow((Exception) clazz.getDeclaredConstructor(String.class).newInstance("Expected"));
    assertThrows(ApplicationException.class,
        () -> repo.findByIdAndTraineeTisId(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID));
  }

  @Test
  void shouldDeleteFormRPartBFromCloudStorage() {
    repo.delete(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID);
    verify(s3Mock).deleteObject(DeleteObjectRequest.builder().bucket(bucketName)
        .key(String.format("1/forms/formr-b/%s.json", DEFAULT_ID)).build());
  }

  @Test
  void shouldThrowExceptionWhenFormRPartBDeleteFailed() {
    doThrow(new ApplicationException("Expected Exception"))
        .when(s3Mock).deleteObject(any(Consumer.class));

    assertThrows(ApplicationException.class, () ->
        repo.delete(DEFAULT_ID_STRING, DEFAULT_TRAINEE_TIS_ID));
  }
}
