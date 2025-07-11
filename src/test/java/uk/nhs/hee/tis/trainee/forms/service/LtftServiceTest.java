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

package uk.nhs.hee.tis.trainee.forms.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.INVALID;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.UNKNOWN;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType.VALID;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.APPROVED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.DRAFT;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.REJECTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.SUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.UNSUBMITTED;
import static uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState.WITHDRAWN;
import static uk.nhs.hee.tis.trainee.forms.service.LtftService.FORM_ATTRIBUTE_FORM_STATUS;
import static uk.nhs.hee.tis.trainee.forms.service.LtftService.FORM_ATTRIBUTE_TPD_STATUS;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import uk.nhs.hee.tis.trainee.forms.dto.FeaturesDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftAdminSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.CctChangeDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.DeclarationsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.DiscussionsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.ProgrammeMembershipDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.ReasonsDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.LftfStatusInfoDetailDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto.StatusDto.StatusInfoDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftSummaryDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonalDetailsDto;
import uk.nhs.hee.tis.trainee.forms.dto.RedactedPersonDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.EmailValidityType;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;
import uk.nhs.hee.tis.trainee.forms.dto.identity.AdminIdentity;
import uk.nhs.hee.tis.trainee.forms.dto.identity.TraineeIdentity;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapper;
import uk.nhs.hee.tis.trainee.forms.mapper.LtftMapperImpl;
import uk.nhs.hee.tis.trainee.forms.mapper.TemporalMapperImpl;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusDetail;
import uk.nhs.hee.tis.trainee.forms.model.AbstractAuditedForm.Status.StatusInfo;
import uk.nhs.hee.tis.trainee.forms.model.LtftForm;
import uk.nhs.hee.tis.trainee.forms.model.Person;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChange;
import uk.nhs.hee.tis.trainee.forms.model.content.CctChangeType;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Declarations;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Discussions;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.PersonalDetails;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.ProgrammeMembership;
import uk.nhs.hee.tis.trainee.forms.model.content.LtftContent.Reasons;
import uk.nhs.hee.tis.trainee.forms.repository.LtftFormRepository;

class LtftServiceTest {

  private static final String TRAINEE_ID = "40";
  private static final String TRAINEE_EMAIL = "email";
  private static final String TRAINEE_NAME = "name";
  private static final String TRAINEE_ROLE = "TRAINEE";
  private static final String FORM_REF = "formRef";

  private static final String ADMIN_NAME = "Ad Min";
  private static final String ADMIN_EMAIL = "ad.min@example.com";
  private static final String ADMIN_GROUP = "abc-123";
  private static final UUID ID = UUID.randomUUID();

  private static final String LTFT_ASSIGNMENT_UPDATE_TOPIC = "update/topic/assignment";
  private static final String LTFT_STATUS_UPDATE_TOPIC = "update/topic/status";
  private static final UUID PM_UUID = UUID.randomUUID();

  private LtftService service;
  private LtftFormRepository repository;
  private MongoTemplate mongoTemplate;
  private LtftMapper mapper;
  private EventBroadcastService eventBroadcastService;
  private LtftSubmissionHistoryService ltftSubmissionHistoryService;

  @BeforeEach
  void setUp() {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setName(ADMIN_NAME);
    adminIdentity.setEmail(ADMIN_EMAIL);
    adminIdentity.setGroups(Set.of(ADMIN_GROUP));

    TraineeIdentity traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(TRAINEE_ID);
    traineeIdentity.setEmail(TRAINEE_EMAIL);
    traineeIdentity.setName(TRAINEE_NAME);
    traineeIdentity.setFeatures(FeaturesDto.builder()
        .ltft(true)
        .ltftProgrammes(List.of(PM_UUID.toString()))
        .build());

    repository = mock(LtftFormRepository.class);
    mongoTemplate = mock(MongoTemplate.class);
    eventBroadcastService = mock(EventBroadcastService.class);
    ltftSubmissionHistoryService = mock(LtftSubmissionHistoryService.class);

    mapper = new LtftMapperImpl(new TemporalMapperImpl());
    service = new LtftService(adminIdentity, traineeIdentity, repository, mongoTemplate, mapper,
        eventBroadcastService, LTFT_ASSIGNMENT_UPDATE_TOPIC, LTFT_STATUS_UPDATE_TOPIC,
        ltftSubmissionHistoryService);
  }

  @Test
  void shouldReturnEmptyGettingLtftFormSummariesWhenNotFound() {
    when(repository.findByTraineeTisIdOrderByLastModified(TRAINEE_ID)).thenReturn(List.of());

    List<LtftSummaryDto> result = service.getLtftSummaries();

    assertThat("Unexpected LTFT summaries count.", result.size(), is(0));
  }

  @Test
  void shouldGetLtftFormSummariesWhenFound() {
    UUID ltftId1 = UUID.randomUUID();
    UUID pmId1 = UUID.randomUUID();
    Instant created1 = Instant.now().minus(Duration.ofDays(1));
    Instant lastModified1 = Instant.now().plus(Duration.ofDays(1));

    LtftForm entity1 = new LtftForm();
    entity1.setId(ltftId1);
    entity1.setTraineeTisId(TRAINEE_ID);

    LtftContent content1 = LtftContent.builder()
        .name("Test LTFT form 1")
        .programmeMembership(ProgrammeMembership.builder()
            .id(pmId1)
            .build())
        .discussions(Discussions.builder()
            .tpdName("tpd")
            .other(List.of(Person.builder()
                .name("other")
                .build()))
            .build())
        .build();
    entity1.setContent(content1);

    Status status1 = Status.builder()
        .current(StatusInfo.builder()
            .state(UNSUBMITTED)
            .detail(StatusDetail.builder()
                .reason("Unsubmit reason")
                .message("Unsubmit message")
                .build())
            .modifiedBy(Person.builder()
                .role(ADMIN_NAME)
                .build())
            .build())
        .build();
    entity1.setStatus(status1);
    entity1.setFormRef(FORM_REF);
    entity1.setCreated(created1);
    entity1.setLastModified(lastModified1);

    UUID ltftId2 = UUID.randomUUID();
    UUID pmId2 = UUID.randomUUID();
    Instant created2 = Instant.now().minus(Duration.ofDays(2));
    Instant lastModified2 = Instant.now().plus(Duration.ofDays(2));

    LtftForm entity2 = new LtftForm();
    entity2.setId(ltftId2);
    entity2.setTraineeTisId(TRAINEE_ID);

    LtftContent content2 = LtftContent.builder()
        .name("Test LTFT form 2")
        .programmeMembership(ProgrammeMembership.builder()
            .id(pmId2)
            .build())
        .build();
    entity2.setContent(content2);

    Status status2 = Status.builder()
        .current(StatusInfo.builder()
            .state(SUBMITTED)
            .build())
        .build();
    entity2.setStatus(status2);
    entity2.setCreated(created2);
    entity2.setLastModified(lastModified2);

    when(repository.findByTraineeTisIdOrderByLastModified(TRAINEE_ID)).thenReturn(
        List.of(entity1, entity2));

    List<LtftSummaryDto> result = service.getLtftSummaries();

    assertThat("Unexpected LTFT form summary count.", result.size(), is(2));

    LtftSummaryDto dto1 = result.get(0);
    assertThat("Unexpected LTFT form ID.", dto1.id(), is(ltftId1));
    assertThat("Unexpected LTFT form ref.", dto1.formRef(), is(FORM_REF));
    assertThat("Unexpected LTFT name.", dto1.name(), is("Test LTFT form 1"));
    assertThat("Unexpected PM ID.", dto1.programmeMembershipId(), is(pmId1));
    assertThat("Unexpected status.", dto1.status(), is(UNSUBMITTED));
    assertThat("Unexpected status reason.", dto1.statusReason(), is("Unsubmit reason"));
    assertThat("Unexpected status message.", dto1.statusMessage(), is("Unsubmit message"));
    assertThat("Unexpected status modified by role.", dto1.modifiedByRole(), is(ADMIN_NAME));
    assertThat("Unexpected created timestamp.", dto1.created(), is(created1));
    assertThat("Unexpected last modified timestamp.", dto1.lastModified(), is(lastModified1));

    LtftSummaryDto dto2 = result.get(1);
    assertThat("Unexpected LTFT form ID.", dto2.id(), is(ltftId2));
    assertThat("Unexpected LTFT name.", dto2.name(), is("Test LTFT form 2"));
    assertThat("Unexpected PM ID.", dto2.programmeMembershipId(), is(pmId2));
    assertThat("Unexpected status.", dto2.status(), is(SUBMITTED));
    assertThat("Unexpected created timestamp.", dto2.created(), is(created2));
    assertThat("Unexpected last modified timestamp.", dto2.lastModified(), is(lastModified2));
  }

  @Test
  void shouldCountAdminLtfts() {
    when(mongoTemplate.count(any(), eq(LtftForm.class))).thenReturn(40L);

    long count = service.getAdminLtftCount(Map.of());

    assertThat("Unexpected count.", count, is(40L));
  }

  @Test
  void shouldNotApplyPagingOrSortingWhenCountingAdminLtfts() {
    service.getAdminLtftCount(Map.of());

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    assertThat("Unexpected sorted flag.", query.isSorted(), is(false));
    assertThat("Unexpected limited flag.", query.isLimited(), is(false));
  }

  @Test
  void shouldExcludeNonSubmittedWhenCountingAdminLtfts() {
    service.getAdminLtftCount(Map.of());

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));

    Document submittedFilter = queryObject.get("status.submitted", Document.class);
    assertThat("Unexpected filter key count.", submittedFilter.keySet(), hasSize(1));
    assertThat("Unexpected filter key.", submittedFilter.keySet(), hasItem("$ne"));
    assertThat("Unexpected filter value.", submittedFilter.get("$ne"), nullValue());
  }

  @Test
  void shouldFilterByDbcWhenCountingAdminLtfts() {
    service.getAdminLtftCount(Map.of());

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));

    Document dbcFilter = queryObject.get("content.programmeMembership.designatedBodyCode",
        Document.class);
    assertThat("Unexpected filter key count.", dbcFilter.keySet(), hasSize(1));
    assertThat("Unexpected filter key.", dbcFilter.keySet(), hasItem("$in"));

    Set<String> filteredDbcs = dbcFilter.get("$in", Set.class);
    assertThat("Unexpected filter value count.", filteredDbcs, hasSize(1));
    assertThat("Unexpected filter value.", filteredDbcs, hasItem(ADMIN_GROUP));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formRef | formRef
      assignedAdmin.name | status.current.assignedAdmin.name
      assignedAdmin.email | status.current.assignedAdmin.email
      personalDetails.forenames | content.personalDetails.forenames
      personalDetails.gdcNumber | content.personalDetails.gdcNumber
      personalDetails.gmcNumber | content.personalDetails.gmcNumber
      personalDetails.surname | content.personalDetails.surname
      programmeName | content.programmeMembership.name
      status | status.current.state
      traineeId | traineeTisId
      """)
  void shouldApplySingleValueUserFiltersWhenCountingAdminLtfts(String external, String internal) {
    service.getAdminLtftCount(Map.of(external, "filterValue"));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(3));

    String userFilter = queryObject.getString(internal);
    assertThat("Unexpected filter value.", userFilter, is("filterValue"));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formRef | formRef
      assignedAdmin.name | status.current.assignedAdmin.name
      assignedAdmin.email | status.current.assignedAdmin.email
      personalDetails.forenames | content.personalDetails.forenames
      personalDetails.gdcNumber | content.personalDetails.gdcNumber
      personalDetails.gmcNumber | content.personalDetails.gmcNumber
      personalDetails.surname | content.personalDetails.surname
      programmeName | content.programmeMembership.name
      status | status.current.state
      traineeId | traineeTisId
      """)
  void shouldApplyMultiValueUserFiltersWhenCountingAdminLtfts(String external, String internal) {
    service.getAdminLtftCount(Map.of(external, "filterValue1,filterValue2"));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(3));

    Document userFilter = queryObject.get(internal, Document.class);
    assertThat("Unexpected filter key count.", userFilter.keySet(), hasSize(1));
    assertThat("Unexpected filter key.", userFilter.keySet(), hasItem("$in"));

    List<String> filteredDbcs = userFilter.get("$in", List.class);
    assertThat("Unexpected filter value count.", filteredDbcs, hasSize(2));
    assertThat("Unexpected filter value.", filteredDbcs, hasItems("filterValue1", "filterValue2"));
  }

  @Test
  void shouldApplyMultipleUserFiltersWhenCountingAdminLtfts() {
    service.getAdminLtftCount(Map.of(
        "programmeName", "filterValue1",
        "status", "filterValue2"
    ));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(4));

    String programmeName = queryObject.getString("content.programmeMembership.name");
    assertThat("Unexpected filter value.", programmeName, is("filterValue1"));

    String status = queryObject.getString("status.current.state");
    assertThat("Unexpected filter value.", status, is("filterValue2"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldExcludeEmptyUserFiltersWhenCountingAdminLtfts(String filterValue) {
    Map<String, String> filterParams = new HashMap<>();
    filterParams.put("formRef", filterValue);
    service.getAdminLtftCount(filterParams);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));
    assertThat("Unexpected filter key.", queryObject.keySet(), not(hasItem("formRef")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "shortNotice", "daysToStart", "reason"})
  void shouldExcludeUnsupportedUserFiltersWhenCountingAdminLtfts(String field) {
    service.getAdminLtftCount(Map.of(field, "filterValue"));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    Document queryObject = query.getQueryObject();
    assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));
    assertThat("Unexpected filter key.", queryObject.keySet(), not(hasItem(field)));
  }

  @Test
  void shouldGetAdminLtftSummaries() {
    LtftForm entity1 = new LtftForm();
    UUID id1 = UUID.randomUUID();
    entity1.setId(id1);

    LtftForm entity2 = new LtftForm();
    UUID id2 = UUID.randomUUID();
    entity2.setId(id2);

    when(mongoTemplate.find(any(), eq(LtftForm.class))).thenReturn(List.of(entity1, entity2));
    when(mongoTemplate.count(any(), eq(LtftForm.class))).thenReturn(2L);

    Page<LtftAdminSummaryDto> dtos = service.getAdminLtftSummaries(Map.of(), PageRequest.of(1, 1));

    List<LtftAdminSummaryDto> content = dtos.getContent();
    assertThat("Unexpected ID.", content.get(0).id(), is(id1));
    assertThat("Unexpected ID.", content.get(1).id(), is(id2));
  }

  @Test
  void shouldGetPagedAdminLtftSummaries() {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    when(mongoTemplate.find(queryCaptor.capture(), eq(LtftForm.class))).thenReturn(
        List.of(new LtftForm(), new LtftForm()));
    when(mongoTemplate.count(queryCaptor.capture(), eq(LtftForm.class))).thenReturn(2L);

    Page<LtftAdminSummaryDto> dtos = service.getAdminLtftSummaries(Map.of(), PageRequest.of(1, 1));

    assertThat("Unexpected total elements.", dtos.getTotalElements(), is(2L));
    assertThat("Unexpected total pages.", dtos.getTotalPages(), is(2));
    assertThat("Unexpected pageable.", dtos.getPageable().isPaged(), is(true));
    assertThat("Unexpected page number.", dtos.getPageable().getPageNumber(), is(1));
    assertThat("Unexpected page size.", dtos.getPageable().getPageSize(), is(1));

    List<Query> queries = queryCaptor.getAllValues();
    assertThat("Unexpected limited flag.", queries.get(0).isLimited(), is(true));
    assertThat("Unexpected limit.", queries.get(0).getLimit(), is(1));
    assertThat("Unexpected skip.", queries.get(0).getSkip(), is(1L));

    // The second query is the count, which is unpaged.
    assertThat("Unexpected limited flag.", queries.get(1).isLimited(), is(false));
    assertThat("Unexpected limit.", queries.get(1).getLimit(), is(0));
    assertThat("Unexpected skip.", queries.get(1).getSkip(), is(-1L));
  }

  @Test
  void shouldGetUnpagedAdminLtftSummaries() {
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    when(mongoTemplate.find(queryCaptor.capture(), eq(LtftForm.class))).thenReturn(
        List.of(new LtftForm(), new LtftForm()));

    Page<LtftAdminSummaryDto> dtos = service.getAdminLtftSummaries(Map.of(), Pageable.unpaged());

    assertThat("Unexpected total elements.", dtos.getTotalElements(), is(2L));
    assertThat("Unexpected total pages.", dtos.getTotalPages(), is(1));
    assertThat("Unexpected pageable.", dtos.getPageable().isPaged(), is(false));

    Query query = queryCaptor.getValue();
    assertThat("Unexpected limited flag.", query.isLimited(), is(false));
    assertThat("Unexpected limit.", query.getLimit(), is(0));
    assertThat("Unexpected skip.", query.getSkip(), is(0L));

    verify(mongoTemplate, never()).count(any(), eq(LtftForm.class));
  }

  @Test
  void shouldExcludeNonSubmittedWhenGettingAdminLtftSummaries() {
    service.getAdminLtftSummaries(Map.of(), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));

      Document submittedFilter = queryObject.get("status.submitted", Document.class);
      assertThat("Unexpected filter key count.", submittedFilter.keySet(), hasSize(1));
      assertThat("Unexpected filter key.", submittedFilter.keySet(), hasItem("$ne"));
      assertThat("Unexpected filter value.", submittedFilter.get("$ne"), nullValue());
    });
  }

  @Test
  void shouldFilterByDbcWhenGettingAdminLtftSummaries() {
    service.getAdminLtftSummaries(Map.of(), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));

      Document dbcFilter = queryObject.get("content.programmeMembership.designatedBodyCode",
          Document.class);
      assertThat("Unexpected filter key count.", dbcFilter.keySet(), hasSize(1));
      assertThat("Unexpected filter key.", dbcFilter.keySet(), hasItem("$in"));

      Set<String> filteredDbcs = dbcFilter.get("$in", Set.class);
      assertThat("Unexpected filter value count.", filteredDbcs, hasSize(1));
      assertThat("Unexpected filter value.", filteredDbcs, hasItem(ADMIN_GROUP));
    });
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formRef | formRef
      assignedAdmin.name | status.current.assignedAdmin.name
      assignedAdmin.email | status.current.assignedAdmin.email
      personalDetails.forenames | content.personalDetails.forenames
      personalDetails.gdcNumber | content.personalDetails.gdcNumber
      personalDetails.gmcNumber | content.personalDetails.gmcNumber
      personalDetails.surname | content.personalDetails.surname
      programmeName | content.programmeMembership.name
      status | status.current.state
      traineeId | traineeTisId
      """)
  void shouldApplySingleValueUserFiltersWhenGettingAdminLtftSummaries(String external,
      String internal) {
    service.getAdminLtftSummaries(Map.of(external, "filterValue"), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(3));

      String userFilter = queryObject.getString(internal);
      assertThat("Unexpected filter value.", userFilter, is("filterValue"));
    });
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formRef | formRef
      assignedAdmin.name | status.current.assignedAdmin.name
      assignedAdmin.email | status.current.assignedAdmin.email
      personalDetails.forenames | content.personalDetails.forenames
      personalDetails.gdcNumber | content.personalDetails.gdcNumber
      personalDetails.gmcNumber | content.personalDetails.gmcNumber
      personalDetails.surname | content.personalDetails.surname
      programmeName | content.programmeMembership.name
      status | status.current.state
      traineeId | traineeTisId
      """)
  void shouldApplyMultiValueUserFiltersWhenGettingAdminLtftSummaries(String external,
      String internal) {
    service.getAdminLtftSummaries(Map.of(
        external, "filterValue1,filterValue2"
    ), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(3));

      Document userFilter = queryObject.get(internal, Document.class);
      assertThat("Unexpected filter key count.", userFilter.keySet(), hasSize(1));
      assertThat("Unexpected filter key.", userFilter.keySet(), hasItem("$in"));

      List<String> filteredDbcs = userFilter.get("$in", List.class);
      assertThat("Unexpected filter value count.", filteredDbcs, hasSize(2));
      assertThat("Unexpected filter value.", filteredDbcs,
          hasItems("filterValue1", "filterValue2"));
    });
  }

  @Test
  void shouldApplyMultipleUserFiltersWhenGettingAdminLtftSummaries() {
    service.getAdminLtftSummaries(Map.of(
        "programmeName", "filterValue1",
        "status", "filterValue2"
    ), PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(4));

      String programmeName = queryObject.getString("content.programmeMembership.name");
      assertThat("Unexpected filter value.", programmeName, is("filterValue1"));

      String status = queryObject.getString("status.current.state");
      assertThat("Unexpected filter value.", status, is("filterValue2"));
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "shortNotice", "daysToStart", "reason"})
  void shouldExcludeUnsupportedUserFiltersWhenGettingAdminLtftSummaries(String field) {
    service.getAdminLtftSummaries(Map.of(field, "filterValue1,filterValue2"),
        PageRequest.of(1, 1));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues().forEach(query -> {
      Document queryObject = query.getQueryObject();
      assertThat("Unexpected filter count.", queryObject.keySet(), hasSize(2));
      assertThat("Unexpected filter key.", queryObject.keySet(), not(hasItem(field)));
    });
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formRef | formRef
      daysToStart | content.change.startDate
      proposedStartDate | content.change.startDate
      submissionDate | status.submitted
      """)
  void shouldAppendIdToUserSortWhenGettingPagedAdminLtftSummaries(String external,
      String internal) {
    service.getAdminLtftSummaries(Map.of(), PageRequest.of(1, 1, Sort.by(Order.asc(external))));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues().forEach(query -> {
      assertThat("Unexpected sorted flag.", query.isSorted(), is(true));

      Document sortObject = query.getSortObject();
      assertThat("Unexpected sort count.", sortObject.keySet(), hasSize(2));

      Iterator<String> sortKeyIterator = sortObject.keySet().iterator();
      assertThat("Unexpected sort key.", sortKeyIterator.next(), is(internal));
      assertThat("Unexpected sort direction.", sortObject.get(internal), is(1));
      assertThat("Unexpected sort key.", sortKeyIterator.next(), is("id"));
      assertThat("Unexpected sort direction.", sortObject.get("id"), is(1));
    });
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      formRef | formRef
      daysToStart | content.change.startDate
      proposedStartDate | content.change.startDate
      submissionDate | status.submitted
      """)
  void shouldApplyUserSortWhenGettingUnpagedAdminLtftSummaries(String external, String internal) {
    service.getAdminLtftSummaries(Map.of(), Pageable.unpaged(Sort.by(Order.asc(external))));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));

    Query query = queryCaptor.getValue();
    assertThat("Unexpected sorted flag.", query.isSorted(), is(true));

    Document sortObject = query.getSortObject();
    assertThat("Unexpected sort count.", sortObject.keySet(), hasSize(1));
    assertThat("Unexpected sort direction.", sortObject.get(internal), is(1));

    verify(mongoTemplate, never()).count(any(), eq(LtftForm.class));
  }

  @Test
  void shouldApplyMultipleUserSortsWhenGettingPagedAdminLtftSummaries() {
    PageRequest pageRequest = PageRequest.of(1, 1, Sort.by(
        Order.asc("formRef"),
        Order.desc("submissionDate")
    ));

    service.getAdminLtftSummaries(Map.of(), pageRequest);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));
    verify(mongoTemplate).count(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues().forEach(query -> {
      assertThat("Unexpected sorted flag.", query.isSorted(), is(true));

      Document sortObject = query.getSortObject();
      assertThat("Unexpected sort count.", sortObject.keySet(), hasSize(3));

      Iterator<String> sortKeyIterator = sortObject.keySet().iterator();
      assertThat("Unexpected sort key.", sortKeyIterator.next(), is("formRef"));
      assertThat("Unexpected sort direction.", sortObject.get("formRef"), is(1));

      assertThat("Unexpected sort key.", sortKeyIterator.next(), is("status.submitted"));
      assertThat("Unexpected sort direction.", sortObject.get("status.submitted"), is(-1));

      assertThat("Unexpected sort key.", sortKeyIterator.next(), is("id"));
      assertThat("Unexpected sort direction.", sortObject.get("id"), is(1));
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "programmeName", "status"})
  void shouldExcludeUnsupportedUserSortWhenGettingUnpagedAdminLtftSummaries(String field) {
    service.getAdminLtftSummaries(Map.of(), Pageable.unpaged(Sort.by(Order.asc(field))));

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.captor();
    verify(mongoTemplate).find(queryCaptor.capture(), eq(LtftForm.class));

    queryCaptor.getAllValues()
        .forEach(query -> assertThat("Unexpected sorted flag.", query.isSorted(), is(false)));
  }

  @Test
  void shouldGetAdminLtftDetailWithFormId() {
    service.getAdminLtftDetail(ID);

    verify(repository)
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            eq(ID), any(), any());
  }

  @Test
  void shouldGetAdminLtftDetailWithDraftExcluded() {
    service.getAdminLtftDetail(ID);

    verify(repository)
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), eq(Set.of(DRAFT)), any());
  }

  @Test
  void shouldGetAdminLtftDetailWithAdminDbcs() {
    service.getAdminLtftDetail(ID);

    verify(repository)
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), eq(Set.of(ADMIN_GROUP)));
  }

  @Test
  void shouldGetEmptyAdminLtftDetailWhenFormNotFound() {
    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.empty());

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(false));
  }

  @Test
  void shouldGetAdminLtftDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setTraineeTisId(TRAINEE_ID);
    entity.setFormRef("LTFT_test_ref");
    entity.setRevision(1);
    entity.setCreated(Instant.MIN);
    entity.setLastModified(Instant.MAX);

    LtftContent content = LtftContent.builder()
        .name("Test LTFT")
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    assertThat("Unexpected ID.", dto.id(), is(ID));
    assertThat("Unexpected trainee ID.", dto.traineeTisId(), is(TRAINEE_ID));
    assertThat("Unexpected form ref.", dto.formRef(), is("LTFT_test_ref"));
    assertThat("Unexpected revision.", dto.revision(), is(1));
    assertThat("Unexpected name.", dto.name(), is("Test LTFT"));
    assertThat("Unexpected created.", dto.created(), is(Instant.MIN));
    assertThat("Unexpected lastModified.", dto.lastModified(), is(Instant.MAX));
  }

  @Test
  void shouldGetAdminLtftDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setContent(LtftContent.builder().build());

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    assertThat("Unexpected ID.", dto.id(), is(ID));
    assertThat("Unexpected trainee ID.", dto.traineeTisId(), nullValue());
    assertThat("Unexpected form ref.", dto.formRef(), nullValue());
    assertThat("Unexpected revision.", dto.revision(), is(0));
    assertThat("Unexpected name.", dto.name(), nullValue());
    assertThat("Unexpected created.", dto.created(), nullValue());
    assertThat("Unexpected lastModified.", dto.lastModified(), nullValue());
  }

  @Test
  void shouldGetAdminLtftPersonalDetailsDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setTraineeTisId(TRAINEE_ID);

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder()
            .title("Dr")
            .forenames("Anthony")
            .surname("Gilliam")
            .email("anthony.gilliam@example.com")
            .telephoneNumber("07700900000")
            .mobileNumber("07700900001")
            .gmcNumber("1234567")
            .gdcNumber("D123456")
            .skilledWorkerVisaHolder(true)
            .build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    PersonalDetailsDto personalDetails = dto.personalDetails();
    assertThat("Unexpected ID.", personalDetails.id(), is(TRAINEE_ID));
    assertThat("Unexpected title.", personalDetails.title(), is("Dr"));
    assertThat("Unexpected forenames.", personalDetails.forenames(), is("Anthony"));
    assertThat("Unexpected surname.", personalDetails.surname(), is("Gilliam"));
    assertThat("Unexpected email.", personalDetails.email(), is("anthony.gilliam@example.com"));
    assertThat("Unexpected telephone number.", personalDetails.telephoneNumber(),
        is("07700900000"));
    assertThat("Unexpected mobile number.", personalDetails.mobileNumber(), is("07700900001"));
    assertThat("Unexpected GMC number.", personalDetails.gmcNumber(), is("1234567"));
    assertThat("Unexpected GDC number.", personalDetails.gdcNumber(), is("D123456"));
    assertThat("Unexpected visa flag.", personalDetails.skilledWorkerVisaHolder(), is(true));
  }

  @Test
  void shouldGetAdminLtftPersonalDetailsDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .personalDetails(PersonalDetails.builder().build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    PersonalDetailsDto personalDetails = dto.personalDetails();
    assertThat("Unexpected ID.", personalDetails.id(), nullValue());
    assertThat("Unexpected title.", personalDetails.title(), nullValue());
    assertThat("Unexpected forenames.", personalDetails.forenames(), nullValue());
    assertThat("Unexpected surname.", personalDetails.surname(), nullValue());
    assertThat("Unexpected email.", personalDetails.email(), nullValue());
    assertThat("Unexpected telephone number.", personalDetails.telephoneNumber(), nullValue());
    assertThat("Unexpected mobile number.", personalDetails.mobileNumber(), nullValue());
    assertThat("Unexpected GMC number.", personalDetails.gmcNumber(), nullValue());
    assertThat("Unexpected GDC number.", personalDetails.gdcNumber(), nullValue());
    assertThat("Unexpected visa flag.", personalDetails.skilledWorkerVisaHolder(), nullValue());
  }

  @Test
  void shouldGetAdminLtftProgrammeMembershipDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    UUID pmId = UUID.randomUUID();

    LtftContent content = LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .id(pmId)
            .name("Test PM")
            .designatedBodyCode("1-1DBC")
            .managingDeanery("Test Deanery")
            .startDate(LocalDate.MIN)
            .endDate(LocalDate.MAX)
            .wte(0.75)
            .build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    ProgrammeMembershipDto programmeMembership = dto.programmeMembership();
    assertThat("Unexpected PM ID.", programmeMembership.id(), is(pmId));
    assertThat("Unexpected PM name.", programmeMembership.name(), is("Test PM"));
    assertThat("Unexpected PM DBC.", programmeMembership.designatedBodyCode(), is("1-1DBC"));
    assertThat("Unexpected PM deanery.", programmeMembership.managingDeanery(), is("Test Deanery"));
    assertThat("Unexpected PM start date.", programmeMembership.startDate(), is(LocalDate.MIN));
    assertThat("Unexpected PM end date.", programmeMembership.endDate(), is(LocalDate.MAX));
    assertThat("Unexpected PM wte.", programmeMembership.wte(), is(0.75));
  }

  @Test
  void shouldGetAdminLtftProgrammeMembershipDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder().build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    ProgrammeMembershipDto programmeMembership = dto.programmeMembership();
    assertThat("Unexpected PM ID.", programmeMembership.id(), nullValue());
    assertThat("Unexpected PM name.", programmeMembership.name(), nullValue());
    assertThat("Unexpected PM DBC.", programmeMembership.designatedBodyCode(), nullValue());
    assertThat("Unexpected PM deanery.", programmeMembership.managingDeanery(), nullValue());
    assertThat("Unexpected PM start date.", programmeMembership.startDate(), nullValue());
    assertThat("Unexpected PM end date.", programmeMembership.endDate(), nullValue());
    assertThat("Unexpected PM wte.", programmeMembership.wte(), nullValue());
  }

  @Test
  void shouldGetAdminLtftDeclarationsDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .declarations(Declarations.builder()
            .discussedWithTpd(true)
            .notGuaranteed(false)
            .informationIsCorrect(true)
            .build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    DeclarationsDto declarations = dto.declarations();
    assertThat("Unexpected declaration.", declarations.discussedWithTpd(), is(true));
    assertThat("Unexpected declaration.", declarations.notGuaranteed(), is(false));
    assertThat("Unexpected declaration.", declarations.informationIsCorrect(), is(true));
  }

  @Test
  void shouldGetAdminLtftDeclarationsDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .declarations(Declarations.builder().build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    DeclarationsDto declarations = dto.declarations();
    assertThat("Unexpected declaration.", declarations.discussedWithTpd(), nullValue());
    assertThat("Unexpected declaration.", declarations.notGuaranteed(), nullValue());
    assertThat("Unexpected declaration.", declarations.informationIsCorrect(), nullValue());
  }

  @Test
  void shouldGetAdminLtftDiscussionDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .discussions(Discussions.builder()
            .tpdName("Tee Pee-Dee")
            .tpdEmail("t.pd@example.com")
            .other(List.of(
                Person.builder().name("Other 1").email("other.1@example.com").role("Role 1")
                    .build(),
                Person.builder().name("Other 2").email("other.2@example.com").role("Role 2").build()
            ))
            .build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    DiscussionsDto discussions = dto.discussions();
    assertThat("Unexpected TPD name.", discussions.tpdName(), is("Tee Pee-Dee"));
    assertThat("Unexpected TPD email.", discussions.tpdEmail(), is("t.pd@example.com"));
    assertThat("Unexpected other discussion count.", discussions.other(), hasSize(2));

    PersonDto discussion1 = discussions.other().get(0);
    assertThat("Unexpected discussion name.", discussion1.name(), is("Other 1"));
    assertThat("Unexpected discussion email.", discussion1.email(), is("other.1@example.com"));
    assertThat("Unexpected discussion role.", discussion1.role(), is("Role 1"));

    PersonDto discussion2 = discussions.other().get(1);
    assertThat("Unexpected discussion name.", discussion2.name(), is("Other 2"));
    assertThat("Unexpected discussion email.", discussion2.email(), is("other.2@example.com"));
    assertThat("Unexpected discussion role.", discussion2.role(), is("Role 2"));
  }

  @Test
  void shouldGetAdminLtftDiscussionDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .discussions(Discussions.builder().build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    DiscussionsDto discussions = dto.discussions();
    assertThat("Unexpected TPD name.", discussions.tpdName(), nullValue());
    assertThat("Unexpected TPD email.", discussions.tpdEmail(), nullValue());
    assertThat("Unexpected other discussion count.", discussions.other(), nullValue());
  }

  @Test
  void shouldGetAdminLtftChangeDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    UUID changeId = UUID.randomUUID();
    UUID calculationId = UUID.randomUUID();

    LtftContent content = LtftContent.builder()
        .change(CctChange.builder()
            .id(changeId)
            .calculationId(calculationId)
            .type(CctChangeType.LTFT)
            .wte(0.5)
            .startDate(LocalDate.MIN)
            .endDate(LocalDate.EPOCH)
            .cctDate(LocalDate.MAX)
            .build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    CctChangeDto change = dto.change();
    assertThat("Unexpected ID.", change.id(), is(changeId));
    assertThat("Unexpected calculation ID.", change.calculationId(), is(calculationId));
    assertThat("Unexpected type.", change.type(), is(CctChangeType.LTFT));
    assertThat("Unexpected WTE.", change.wte(), is(0.5));
    assertThat("Unexpected start date.", change.startDate(), is(LocalDate.MIN));
    assertThat("Unexpected end date.", change.endDate(), is(LocalDate.EPOCH));
    assertThat("Unexpected CCT date.", change.cctDate(), is(LocalDate.MAX));
  }

  @Test
  void shouldGetAdminLtftChangeDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .change(CctChange.builder().build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    CctChangeDto change = dto.change();
    assertThat("Unexpected ID.", change.id(), nullValue());
    assertThat("Unexpected calculation ID.", change.calculationId(), nullValue());
    assertThat("Unexpected type.", change.type(), nullValue());
    assertThat("Unexpected WTE.", change.wte(), nullValue());
    assertThat("Unexpected start date.", change.startDate(), nullValue());
    assertThat("Unexpected end date.", change.endDate(), nullValue());
    assertThat("Unexpected CCT date.", change.cctDate(), nullValue());
  }

  @Test
  void shouldGetAdminLtftReasonsDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .reasons(Reasons.builder()
            .selected(List.of("Test Reason 2", "Test Reason 1", "Test Reason 3"))
            .otherDetail("Other Detail")
            .supportingInformation("Supporting Information")
            .build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    ReasonsDto reasons = dto.reasons();
    assertThat("Unexpected reason count.", reasons.selected(), hasSize(3));
    assertThat("Unexpected reason.", reasons.selected().get(0), is("Test Reason 2"));
    assertThat("Unexpected reason.", reasons.selected().get(1), is("Test Reason 1"));
    assertThat("Unexpected reason.", reasons.selected().get(2), is("Test Reason 3"));
    assertThat("Unexpected other detail.", reasons.otherDetail(), is("Other Detail"));
    assertThat("Unexpected supporting information.", reasons.supportingInformation(),
        is("Supporting Information"));
  }

  @Test
  void shouldGetAdminLtftReasonsDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);

    LtftContent content = LtftContent.builder()
        .reasons(Reasons.builder().build())
        .build();
    entity.setContent(content);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    ReasonsDto reasons = dto.reasons();
    assertThat("Unexpected reason count.", reasons.selected(), nullValue());
    assertThat("Unexpected other detail.", reasons.otherDetail(), nullValue());
    assertThat("Unexpected supporting information detail.", reasons.supportingInformation(),
        nullValue());
  }

  @Test
  void shouldGetAdminLtftAssignedAdminDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setAssignedAdmin(
        Person.builder()
            .name(ADMIN_NAME)
            .email(ADMIN_EMAIL)
            .role("ADMIN")
            .build(),
        Person.builder().build()
    );

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    RedactedPersonDto assignedAdmin = dto.status().current().assignedAdmin();
    assertThat("Unexpected admin name.", assignedAdmin.name(), is(ADMIN_NAME));
    assertThat("Unexpected admin email.", assignedAdmin.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected admin role.", assignedAdmin.role(), is("ADMIN"));
  }

  @Test
  void shouldGetAdminLtftAssignedAdminDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setAssignedAdmin(Person.builder().build(), null);

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    RedactedPersonDto assignedAdmin = dto.status().current().assignedAdmin();
    assertThat("Unexpected admin name.", assignedAdmin.name(), nullValue());
    assertThat("Unexpected admin email.", assignedAdmin.email(), nullValue());
    assertThat("Unexpected admin role.", assignedAdmin.role(), nullValue());
  }

  @Test
  void shouldGetAdminLtftStatusDetailWhenFormFound() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setContent(LtftContent.builder().build());

    entity.setStatus(Status.builder()
        .current(StatusInfo.builder()
            .state(SUBMITTED)
            .detail(AbstractAuditedForm.Status.StatusDetail.builder()
                .reason("Submitted Detail")
                .build())
            .timestamp(Instant.EPOCH)
            .revision(1)
            .modifiedBy(Person.builder()
                .name("Anthony Gilliam")
                .email("anthony.gilliam@example.com")
                .role("TRAINEE")
                .build())
            .build())
        .history(List.of(
            StatusInfo.builder()
                .state(DRAFT)
                .detail(AbstractAuditedForm.Status.StatusDetail.builder()
                    .reason("Draft Detail")
                    .build())
                .timestamp(Instant.MIN)
                .revision(0)
                .modifiedBy(Person.builder()
                    .name("Anthony Gilliam")
                    .email("anthony.gilliam@example.com")
                    .role("TRAINEE")
                    .build())
                .build(),
            StatusInfo.builder()
                .state(SUBMITTED)
                .detail(AbstractAuditedForm.Status.StatusDetail.builder()
                    .reason("Submitted Detail")
                    .build())
                .timestamp(Instant.EPOCH)
                .revision(1)
                .modifiedBy(Person.builder()
                    .name("Anthony Gilliam")
                    .email("anthony.gilliam@example.com")
                    .role("TRAINEE")
                    .build())
                .build()
        ))
        .build());

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    StatusDto status = dto.status();
    StatusInfoDto currentStatus = status.current();
    assertThat("Unexpected state.", currentStatus.state(), is(SUBMITTED));
    assertThat("Unexpected detail.", currentStatus.detail().reason(), is("Submitted Detail"));
    assertThat("Unexpected timestamp.", currentStatus.timestamp(), is(Instant.EPOCH));
    assertThat("Unexpected revision.", currentStatus.revision(), is(1));

    RedactedPersonDto modifiedBy = currentStatus.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is("Anthony Gilliam"));
    assertThat("Unexpected modified email.", modifiedBy.email(), is("anthony.gilliam@example.com"));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("TRAINEE"));

    List<StatusInfoDto> statusHistory = status.history();
    assertThat("Unexpected history count.", statusHistory, hasSize(2));

    StatusInfoDto history1 = statusHistory.get(0);
    assertThat("Unexpected state.", history1.state(), is(DRAFT));
    assertThat("Unexpected detail.", history1.detail().reason(), is("Draft Detail"));
    assertThat("Unexpected timestamp.", history1.timestamp(), is(Instant.MIN));
    assertThat("Unexpected revision.", history1.revision(), is(0));

    modifiedBy = history1.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is("Anthony Gilliam"));
    assertThat("Unexpected modified email.", modifiedBy.email(), is("anthony.gilliam@example.com"));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("TRAINEE"));

    StatusInfoDto history2 = statusHistory.get(1);
    assertThat("Unexpected state.", history2.state(), is(SUBMITTED));
    assertThat("Unexpected detail.", history2.detail().reason(), is("Submitted Detail"));
    assertThat("Unexpected timestamp.", history2.timestamp(), is(Instant.EPOCH));
    assertThat("Unexpected revision.", history2.revision(), is(1));

    modifiedBy = history2.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is("Anthony Gilliam"));
    assertThat("Unexpected modified email.", modifiedBy.email(), is("anthony.gilliam@example.com"));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("TRAINEE"));
  }

  @Test
  void shouldGetAdminLtftStatusDetailWithDefaultValuesWhenFormFoundWithNullValues() {
    LtftForm entity = new LtftForm();
    entity.setId(ID);
    entity.setContent(LtftContent.builder().build());

    entity.setStatus(Status.builder().build());

    when(repository
        .findByIdAndStatus_Current_StateNotInAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
            any(), any(), any())).thenReturn(Optional.of(entity));

    Optional<LtftFormDto> optionalDto = service.getAdminLtftDetail(ID);

    assertThat("Unexpected dto presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    StatusDto status = dto.status();
    assertThat("Unexpected state.", status.current(), nullValue());
    assertThat("Unexpected history count.", status.history(), nullValue());
  }

  @Test
  void shouldReturnEmptyAssigningAdminWhenFormNotFound() {
    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.empty());

    Optional<LtftFormDto> form = service.assignAdmin(ID, PersonDto.builder().build());

    assertThat("Unexpected form presence.", form.isPresent(), is(false));
  }

  @Test
  void shouldReturnAssignedFormWhenFormFoundAndNoPreviousAdmin() {
    LtftForm form = new LtftForm();
    form.setAssignedAdmin(null, null);
    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PersonDto admin = PersonDto.builder()
        .name("Ad Min")
        .email("ad.min@example.com")
        .build();

    Optional<LtftFormDto> optionalForm = service.assignAdmin(ID, admin);

    assertThat("Unexpected form presence.", optionalForm.isPresent(), is(true));

    RedactedPersonDto assignedAdmin = optionalForm.get().status().current().assignedAdmin();
    assertThat("Unexpected admin name.", assignedAdmin.name(), is("Ad Min"));
    assertThat("Unexpected admin email.", assignedAdmin.email(), is("ad.min@example.com"));
    assertThat("Unexpected admin role.", assignedAdmin.role(), is("ADMIN"));
  }

  @Test
  void shouldReturnAssignedFormWhenFormFoundAndHasPreviousAdmin() {
    LtftForm form = new LtftForm();
    form.setAssignedAdmin(
        Person.builder()
            .name(ADMIN_NAME)
            .email(ADMIN_EMAIL)
            .role("ADMIN")
            .build(),
        null
    );
    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PersonDto admin = PersonDto.builder()
        .name("new admin")
        .email("new.admin@example.com")
        .role("new role")
        .build();

    Optional<LtftFormDto> optionalForm = service.assignAdmin(ID, admin);

    assertThat("Unexpected form presence.", optionalForm.isPresent(), is(true));

    RedactedPersonDto assignedAdmin = optionalForm.get().status().current().assignedAdmin();
    assertThat("Unexpected admin name.", assignedAdmin.name(), is("new admin"));
    assertThat("Unexpected admin email.", assignedAdmin.email(), is("new.admin@example.com"));
    assertThat("Unexpected admin role.", assignedAdmin.role(), is("ADMIN"));
  }

  @Test
  void shouldReturnExistingFormWhenFormFoundAndAdminAlreadyAssigned() {
    LtftForm form = new LtftForm();
    form.setAssignedAdmin(
        Person.builder()
            .name(ADMIN_NAME)
            .email(ADMIN_EMAIL)
            .role("ADMIN")
            .build(),
        null
    );
    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PersonDto admin = PersonDto.builder()
        .name(ADMIN_NAME)
        .email(ADMIN_EMAIL)
        .role("ADMIN")
        .build();

    Optional<LtftFormDto> optionalForm = service.assignAdmin(ID, admin);

    assertThat("Unexpected form presence.", optionalForm.isPresent(), is(true));

    RedactedPersonDto assignedAdmin = optionalForm.get().status().current().assignedAdmin();
    assertThat("Unexpected admin name.", assignedAdmin.name(), is(ADMIN_NAME));
    assertThat("Unexpected admin email.", assignedAdmin.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected admin role.", assignedAdmin.role(), is("ADMIN"));

    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(LifecycleState.class)
  void shouldReturnEmptyUpdatingStatusWhenFormNotFound(LifecycleState state)
      throws MethodArgumentNotValidException {
    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.empty());

    Optional<LtftFormDto> form = service.updateStatusAsAdmin(ID, state, null);

    assertThat("Unexpected form presence.", form.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = "SUBMITTED")
  void shouldThrowExceptionUpdatingStatusWhenTransitionFromDraftInvalid(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(DRAFT);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState, null));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("LtftForm"));
    assertThat("Unexpected field.", fieldError.getField(), is("status.current.state"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("can not be transitioned to " + targetState.name()));

    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = "SUBMITTED")
  void shouldUpdateStatusWhenTransitionFromDraftValid(
      LifecycleState targetState) throws MethodArgumentNotValidException {
    LtftForm entity = new LtftForm();
    entity.setTraineeTisId(TRAINEE_ID);
    entity.setLifecycleState(DRAFT);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(entity));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> optionalDto = service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder()
            .reason("detail reason")
            .message("detail message")
            .build()
    );

    assertThat("Unexpected form presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    assertThat("Unexpected form ref.", dto.formRef(), is("ltft_" + TRAINEE_ID + "_001"));

    StatusInfoDto current = dto.status().current();
    assertThat("Unexpected current state.", current.state(), is(targetState));
    assertThat("Unexpected current revision.", current.revision(), is(0));
    assertThat("Unexpected current timestamp.", current.timestamp(), notNullValue());

    LftfStatusInfoDetailDto detail = current.detail();
    assertThat("Unexpected current reason.", detail.reason(), is("detail reason"));
    assertThat("Unexpected current message.", detail.message(), is("detail message"));

    RedactedPersonDto modifiedBy = current.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is(ADMIN_NAME));
    assertThat("Unexpected modified email.", modifiedBy.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfoDto> history = dto.status().history();
    assertThat("Unexpected history count.", history, hasSize(2));
    assertThat("Unexpected history state.", history.get(0).state(), is(DRAFT));
    assertThat("Unexpected history state.", history.get(1).state(), is(targetState));

    verify(ltftSubmissionHistoryService).takeSnapshot(entity);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"APPROVED", "REJECTED",
      "UNSUBMITTED", "WITHDRAWN"})
  void shouldThrowExceptionUpdatingStatusWhenTransitionFromSubmittedInvalid(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(SUBMITTED);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState, null));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("LtftForm"));
    assertThat("Unexpected field.", fieldError.getField(), is("status.current.state"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("can not be transitioned to " + targetState.name()));

    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"APPROVED", "REJECTED",
      "UNSUBMITTED", "WITHDRAWN"})
  void shouldUpdateStatusWhenTransitionFromSubmittedValid(
      LifecycleState targetState) throws MethodArgumentNotValidException {
    LtftForm entity = new LtftForm();
    entity.setLifecycleState(SUBMITTED);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(entity));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> optionalDto = service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder()
            .reason("detail reason")
            .message("detail message")
            .build()
    );

    assertThat("Unexpected form presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    assertThat("Unexpected form ref.", dto.formRef(), nullValue());

    StatusInfoDto current = dto.status().current();
    assertThat("Unexpected current state.", current.state(), is(targetState));
    assertThat("Unexpected current timestamp.", current.timestamp(), notNullValue());

    LftfStatusInfoDetailDto detail = current.detail();
    assertThat("Unexpected current reason.", detail.reason(), is("detail reason"));
    assertThat("Unexpected current message.", detail.message(), is("detail message"));

    RedactedPersonDto modifiedBy = current.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is(ADMIN_NAME));
    assertThat("Unexpected modified email.", modifiedBy.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfoDto> history = dto.status().history();
    assertThat("Unexpected history count.", history, hasSize(2));
    assertThat("Unexpected history state.", history.get(0).state(), is(SUBMITTED));
    assertThat("Unexpected history state.", history.get(1).state(), is(targetState));

    verifyNoInteractions(ltftSubmissionHistoryService);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"SUBMITTED", "WITHDRAWN"})
  void shouldThrowExceptionUpdatingStatusWhenTransitionFromUnsubmittedInvalid(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(UNSUBMITTED);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState, null));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("LtftForm"));
    assertThat("Unexpected field.", fieldError.getField(), is("status.current.state"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("can not be transitioned to " + targetState.name()));

    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"SUBMITTED", "WITHDRAWN"})
  void shouldUpdateStatusWhenTransitionFromUnsubmittedValid(
      LifecycleState targetState) throws MethodArgumentNotValidException {
    LtftForm entity = new LtftForm();
    entity.setFormRef("formRef_123");
    entity.setLifecycleState(UNSUBMITTED);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(entity));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> optionalDto = service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder()
            .reason("detail reason")
            .message("detail message")
            .build()
    );

    assertThat("Unexpected form presence.", optionalDto.isPresent(), is(true));

    LtftFormDto dto = optionalDto.get();
    assertThat("Unexpected form ref.", dto.formRef(), is("formRef_123"));

    StatusInfoDto current = dto.status().current();
    assertThat("Unexpected current state.", current.state(), is(targetState));
    assertThat("Unexpected current revision.", current.revision(), is(0));
    assertThat("Unexpected current timestamp.", current.timestamp(), notNullValue());

    LftfStatusInfoDetailDto detail = current.detail();
    assertThat("Unexpected current reason.", detail.reason(), is("detail reason"));
    assertThat("Unexpected current message.", detail.message(), is("detail message"));

    RedactedPersonDto modifiedBy = current.modifiedBy();
    assertThat("Unexpected modified name.", modifiedBy.name(), is(ADMIN_NAME));
    assertThat("Unexpected modified email.", modifiedBy.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected modified role.", modifiedBy.role(), is("ADMIN"));

    List<StatusInfoDto> history = dto.status().history();
    assertThat("Unexpected history count.", history, hasSize(2));
    assertThat("Unexpected history state.", history.get(0).state(), is(UNSUBMITTED));
    assertThat("Unexpected history state.", history.get(1), is(current));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"DRAFT", "SUBMITTED",
      "UNSUBMITTED"})
  void shouldThrowExceptionUpdatingStatusWhenTransitionFromFinalState(LifecycleState currentState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(currentState);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    Arrays.stream(LifecycleState.values()).forEach(targetState -> {
      MethodArgumentNotValidException exception = assertThrows(
          MethodArgumentNotValidException.class,
          () -> service.updateStatusAsAdmin(ID, targetState, LftfStatusInfoDetailDto.builder()
              .reason("detail reason")
              .message("detail message")
              .build()
          ));

      List<FieldError> fieldErrors = exception.getFieldErrors();
      assertThat("Unexpected error count.", fieldErrors, hasSize(1));

      FieldError fieldError = fieldErrors.get(0);
      assertThat("Unexpected object name.", fieldError.getObjectName(), is("LtftForm"));
      assertThat("Unexpected field.", fieldError.getField(), is("status.current.state"));
      assertThat("Unexpected message.", fieldError.getDefaultMessage(),
          is("can not be transitioned to " + targetState.name()));
    });

    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      DRAFT | SUBMITTED
      SUBMITTED | APPROVED
      """)
  void shouldNotThrowExceptionUpdatingStatusWhenOptionalStatusDetailNull(
      LifecycleState currentState, LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(currentState);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertDoesNotThrow(() -> service.updateStatusAsAdmin(ID, targetState, null));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      DRAFT | SUBMITTED
      SUBMITTED | APPROVED
      """)
  void shouldNotThrowExceptionUpdatingStatusWhenOptionalStatusDetailReasonNull(
      LifecycleState currentState, LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(currentState);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertDoesNotThrow(() -> service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder().build()));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"REJECTED", "UNSUBMITTED",
      "WITHDRAWN"})
  void shouldThrowExceptionUpdatingStatusWhenRequiredStatusDetailNull(LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(SUBMITTED);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState, null));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("StatusInfo"));
    assertThat("Unexpected field.", fieldError.getField(), is("detail"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("must not be null when transitioning to " + targetState.name()));

    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"REJECTED", "UNSUBMITTED",
      "WITHDRAWN"})
  void shouldThrowExceptionUpdatingStatusWhenRequiredStatusDetailReasonNull(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setLifecycleState(SUBMITTED);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));

    MethodArgumentNotValidException exception = assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, targetState,
            LftfStatusInfoDetailDto.builder().build()));

    List<FieldError> fieldErrors = exception.getFieldErrors();
    assertThat("Unexpected error count.", fieldErrors, hasSize(1));

    FieldError fieldError = fieldErrors.get(0);
    assertThat("Unexpected object name.", fieldError.getObjectName(), is("StatusInfo"));
    assertThat("Unexpected field.", fieldError.getField(), is("detail.reason"));
    assertThat("Unexpected message.", fieldError.getDefaultMessage(),
        is("must not be null when transitioning to " + targetState.name()));

    verify(repository, never()).save(any());
  }

  @Test
  void shouldReturnEmptyIfLtftFormNotFound() {
    when(repository.findByTraineeTisIdAndId(any(), any())).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verify(repository).findByTraineeTisIdAndId(any(), eq(ID));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void shouldReturnEmptyIfLtftFormForTraineeNotFound() {
    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected form returned.", formDtoOptional, is(Optional.empty()));
    verify(repository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void shouldReturnDtoIfLtftFormForTraineeFound() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setContent(LtftContent.builder().name("test").build());
    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID))
        .thenReturn(Optional.of(form));

    Optional<LtftFormDto> formDtoOptional = service.getLtftForm(ID);

    assertThat("Unexpected empty form returned.", formDtoOptional.isPresent(), is(true));
    verify(repository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    LtftFormDto returnedFormDto = formDtoOptional.get();
    assertThat("Unexpected returned LTFT form.", returnedFormDto, is(mapper.toDto(form)));
  }

  @Test
  void shouldNotSaveIfNewLtftFormNotForTrainee() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId("another trainee")
        .build();

    Optional<LtftFormDto> formDtoOptional = service.createLtftForm(dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldSaveIfNewLtftFormForTraineeAndLtftProgrammeMembership() {
    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setContent(LtftContent.builder().name("test").build());
    when(repository.save(any())).thenReturn(existingForm);

    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.createLtftForm(dtoToSave);

    verify(repository).save(any());
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));
  }

  @Test
  void shouldNotSaveIfNewLtftFormForTraineeIfNoFeaturesSet() {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setName(ADMIN_NAME);
    adminIdentity.setEmail(ADMIN_EMAIL);
    adminIdentity.setGroups(Set.of(ADMIN_GROUP));

    TraineeIdentity traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(TRAINEE_ID);

    service = new LtftService(adminIdentity, traineeIdentity, repository, mongoTemplate,
        mapper, eventBroadcastService, LTFT_ASSIGNMENT_UPDATE_TOPIC, LTFT_STATUS_UPDATE_TOPIC,
        ltftSubmissionHistoryService);

    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.createLtftForm(dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotSaveIfNewLtftFormForTraineeIfFeaturesLtftNotTrue() {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setName(ADMIN_NAME);
    adminIdentity.setEmail(ADMIN_EMAIL);
    adminIdentity.setGroups(Set.of(ADMIN_GROUP));

    TraineeIdentity traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(TRAINEE_ID);
    traineeIdentity.setFeatures(FeaturesDto.builder()
        .ltft(false)
        .ltftProgrammes(List.of(PM_UUID.toString()))
        .build());

    service = new LtftService(adminIdentity, traineeIdentity, repository, mongoTemplate,
        mapper, eventBroadcastService, LTFT_ASSIGNMENT_UPDATE_TOPIC, LTFT_STATUS_UPDATE_TOPIC,
        ltftSubmissionHistoryService);

    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.createLtftForm(dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotSaveIfNewLtftFormForTraineeIfNoFeatureLtftProgrammes() {
    AdminIdentity adminIdentity = new AdminIdentity();
    adminIdentity.setName(ADMIN_NAME);
    adminIdentity.setEmail(ADMIN_EMAIL);
    adminIdentity.setGroups(Set.of(ADMIN_GROUP));

    TraineeIdentity traineeIdentity = new TraineeIdentity();
    traineeIdentity.setTraineeId(TRAINEE_ID);
    traineeIdentity.setFeatures(FeaturesDto.builder()
        .ltft(true)
        .build());

    service = new LtftService(adminIdentity, traineeIdentity, repository, mongoTemplate, mapper,
        eventBroadcastService, LTFT_ASSIGNMENT_UPDATE_TOPIC, LTFT_STATUS_UPDATE_TOPIC,
        ltftSubmissionHistoryService);

    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.createLtftForm(dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "1042b0f8-3169-4216-8707-65ea1854b6ac")
  void shouldNotSaveIfNewLtftFormForTraineeButNotLtftProgrammeMembership(String otherPmId) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(otherPmId == null ? null : UUID.fromString(otherPmId))
            .build())
        .build();

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.createLtftForm(dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotUpdateFormIfWithoutId() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .traineeTisId(TRAINEE_ID)
        .build();

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotUpdateFormIfIdDoesNotMatchPathParameter() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();

    Optional<LtftFormDto> formDtoOptional
        = service.updateLtftForm(UUID.randomUUID(), dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotUpdateFormIfTraineeDoesNotMatchLoggedInUser() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId("another trainee")
        .build();

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verifyNoInteractions(repository);
  }

  @Test
  void shouldNotUpdateFormIfExistingFormNotFound() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verify(repository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(repository);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = EXCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateFormIfNotEditableState(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder().name("test").build());
    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verify(repository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(repository);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldSaveIfUpdatingEditableLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder().name("test").build());
    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    verify(repository).save(existingForm);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));
  }

  @Test
  void shouldNotSaveIfUpdatingEditableLtftFormForTraineeWithNonLtftPm() {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(UUID.randomUUID())
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(DRAFT);
    existingForm.setContent(LtftContent.builder().name("test").build());
    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);

    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(false));
    verify(repository).findByTraineeTisIdAndId(TRAINEE_ID, ID);
    verifyNoMoreInteractions(repository);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateFormRefWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setFormRef("ltft_id_001");

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .formRef("new ref")
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();
    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();

    assertThat("Unexpected form reference.", formDto.formRef(), is("ltft_id_001"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateRevisionWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .revision(2)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setRevision(1);

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();

    assertThat("Unexpected revision.", formDto.revision(), is(1));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldUpdateNameWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .name("new name")
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder()
        .name("existing name")
        .build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();

    assertThat("Unexpected name.", formDto.name(), is("new name"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldUpdatePersonalDetailsWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .personalDetails(PersonalDetailsDto.builder()
            .title("Rev")
            .forenames("Trey")
            .surname("Knee")
            .email("trey.knee@example.com")
            .gmcNumber("7654321")
            .gdcNumber("D654321")
            .telephoneNumber("07700 900999")
            .mobileNumber("07700 900998")
            .skilledWorkerVisaHolder(true)
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder()
        .personalDetails(PersonalDetails.builder()
            .title("Dr")
            .forenames("Anthony")
            .surname("Gilliam")
            .email("anthony.gilliam@example.com")
            .gmcNumber("1234567")
            .gdcNumber("D123456")
            .telephoneNumber("07700 900000")
            .mobileNumber("07700 900001")
            .skilledWorkerVisaHolder(false)
            .build())
        .build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    PersonalDetailsDto personalDetails = formDto.personalDetails();

    assertThat("Unexpected title.", personalDetails.title(), is("Rev"));
    assertThat("Unexpected forenames.", personalDetails.forenames(), is("Trey"));
    assertThat("Unexpected surname.", personalDetails.surname(), is("Knee"));
    assertThat("Unexpected email.", personalDetails.email(), is("trey.knee@example.com"));
    assertThat("Unexpected GMC number.", personalDetails.gmcNumber(), is("7654321"));
    assertThat("Unexpected GDC number.", personalDetails.gdcNumber(), is("D654321"));
    assertThat("Unexpected telephone number.", personalDetails.telephoneNumber(),
        is("07700 900999"));
    assertThat("Unexpected mobile number.", personalDetails.mobileNumber(), is("07700 900998"));
    assertThat("Unexpected visa holder.", personalDetails.skilledWorkerVisaHolder(), is(true));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldUpdateProgrammeMembershipWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LocalDate newStartDate = LocalDate.now();
    LocalDate newEndDate = newStartDate.plusYears(1);
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .name("new programme")
            .designatedBodyCode("new DBC")
            .managingDeanery("new deanery")
            .startDate(newStartDate)
            .endDate(newEndDate)
            .wte(0.5)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder()
        .programmeMembership(ProgrammeMembership.builder()
            .id(UUID.randomUUID())
            .name("existing programme")
            .designatedBodyCode("existing DBC")
            .managingDeanery("existing deanery")
            .startDate(LocalDate.MIN)
            .endDate(LocalDate.MAX)
            .wte(1.0)
            .build())
        .build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    ProgrammeMembershipDto programmeMembership = formDto.programmeMembership();

    assertThat("Unexpected ID.", programmeMembership.id(), is(PM_UUID));
    assertThat("Unexpected name.", programmeMembership.name(), is("new programme"));
    assertThat("Unexpected DBC.", programmeMembership.designatedBodyCode(), is("new DBC"));
    assertThat("Unexpected deanery.", programmeMembership.managingDeanery(), is("new deanery"));
    assertThat("Unexpected start date.", programmeMembership.startDate(), is(newStartDate));
    assertThat("Unexpected end date.", programmeMembership.endDate(), is(newEndDate));
    assertThat("Unexpected WTE.", programmeMembership.wte(), is(0.5));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldUpdateDeclarationsWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .declarations(DeclarationsDto.builder()
            .discussedWithTpd(true)
            .informationIsCorrect(true)
            .notGuaranteed(true)
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder()
        .declarations(Declarations.builder()
            .discussedWithTpd(false)
            .informationIsCorrect(false)
            .notGuaranteed(false)
            .build())
        .build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    DeclarationsDto declarations = formDto.declarations();

    assertThat("Unexpected declaration.", declarations.discussedWithTpd(), is(true));
    assertThat("Unexpected declaration.", declarations.informationIsCorrect(), is(true));
    assertThat("Unexpected declaration.", declarations.notGuaranteed(), is(true));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldUpdateDiscussionsWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .discussions(DiscussionsDto.builder()
            .tpdName("new TPD")
            .tpdEmail("new.tpd@example.com")
            .other(List.of(PersonDto.builder()
                    .name("new other 1")
                    .email("new.other1@example.com")
                    .role("new role 1")
                    .build(),
                PersonDto.builder()
                    .name("new other 2")
                    .email("new.other2@example.com")
                    .role("new role 2")
                    .build()))
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder()
        .discussions(Discussions.builder()
            .tpdName("existing TPD")
            .tpdEmail("existing.tpd@example.com")
            .other(List.of(Person.builder()
                .name("existing other")
                .email("existing.other@example.com")
                .role("existing role")
                .build()))
            .build())
        .build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    DiscussionsDto discussions = formDto.discussions();

    assertThat("Unexpected TPD name.", discussions.tpdName(), is("new TPD"));
    assertThat("Unexpected TPD email.", discussions.tpdEmail(), is("new.tpd@example.com"));

    List<PersonDto> other = discussions.other();
    assertThat("Unexpected other discussion count.", other, hasSize(2));

    PersonDto other1 = other.get(0);
    assertThat("Unexpected name.", other1.name(), is("new other 1"));
    assertThat("Unexpected email.", other1.email(), is("new.other1@example.com"));
    assertThat("Unexpected role.", other1.role(), is("new role 1"));

    PersonDto other2 = other.get(1);
    assertThat("Unexpected name.", other2.name(), is("new other 2"));
    assertThat("Unexpected email.", other2.email(), is("new.other2@example.com"));
    assertThat("Unexpected role.", other2.role(), is("new role 2"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldUpdateChangeWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    UUID newChangeId = UUID.randomUUID();
    UUID newCalculationId = UUID.randomUUID();
    LocalDate newStartDate = LocalDate.now();
    LocalDate newEndDate = newStartDate.plusYears(1);
    LocalDate newCctDate = newStartDate.plusYears(2);
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .change(CctChangeDto.builder()
            .id(newChangeId)
            .calculationId(newCalculationId)
            .type(CctChangeType.LTFT)
            .wte(0.5)
            .startDate(newStartDate)
            .endDate(newEndDate)
            .cctDate(newCctDate)
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder()
        .change(CctChange.builder()
            .id(UUID.randomUUID())
            .calculationId(UUID.randomUUID())
            .type(CctChangeType.LTFT)
            .wte(1.0)
            .startDate(LocalDate.MIN)
            .endDate(LocalDate.EPOCH)
            .cctDate(LocalDate.MAX)
            .build())
        .build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    CctChangeDto change = formDto.change();

    assertThat("Unexpected change ID.", change.id(), is(newChangeId));
    assertThat("Unexpected calculation ID.", change.calculationId(), is(newCalculationId));
    assertThat("Unexpected CCT type.", change.type(), is(CctChangeType.LTFT));
    assertThat("Unexpected WTE.", change.wte(), is(0.5));
    assertThat("Unexpected start date.", change.startDate(), is(newStartDate));
    assertThat("Unexpected end date.", change.endDate(), is(newEndDate));
    assertThat("Unexpected CCT date.", change.cctDate(), is(newCctDate));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldUpdateReasonsWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .reasons(ReasonsDto.builder()
            .selected(List.of("new reason 1", "new reason 2"))
            .otherDetail("new other detail")
            .supportingInformation("new supporting information")
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder()
        .reasons(Reasons.builder()
            .selected(List.of("existing reason 1"))
            .otherDetail("existing other detail")
            .supportingInformation("existing supporting information")
            .build())
        .build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    ReasonsDto reasons = formDto.reasons();

    assertThat("Unexpected reason count.", reasons.selected(), hasSize(2));
    assertThat("Unexpected reason.", reasons.selected().get(0), is("new reason 1"));
    assertThat("Unexpected reason.", reasons.selected().get(1), is("new reason 2"));
    assertThat("Unexpected other detail.", reasons.otherDetail(), is("new other detail"));
    assertThat("Unexpected supporting information.", reasons.supportingInformation(),
        is("new supporting information"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateAssignedAdminWhenUpdatingLtftFormForTraineeAndNoExistingContent(
      LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .status(StatusDto.builder()
            .current(StatusInfoDto.builder()
                .assignedAdmin(RedactedPersonDto.builder()
                    .name("new admin")
                    .email("new.admin@example.com")
                    .role("NEW_ADMIN")
                    .build())
                .build())
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(null);

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    assertThat("Unexpected assigned admin.", formDto.status().current().assignedAdmin(),
        nullValue());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateAssignedAdminWhenUpdatingLtftFormForTraineeAndNoExistingAdmin(
      LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .status(StatusDto.builder()
            .current(StatusInfoDto.builder()
                .assignedAdmin(RedactedPersonDto.builder()
                    .name("new admin")
                    .email("new.admin@example.com")
                    .role("NEW_ADMIN")
                    .build())
                .build())
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setContent(LtftContent.builder().build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    assertThat("Unexpected assigned admin.", formDto.status().current().assignedAdmin(),
        nullValue());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateAssignedAdminWhenUpdatingLtftFormForTraineeAndExistingAdmin(
      LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .status(StatusDto.builder()
            .current(StatusInfoDto.builder()
                .assignedAdmin(RedactedPersonDto.builder()
                    .name("new admin")
                    .email("new.admin@example.com")
                    .role("NEW_ADMIN")
                    .build())
                .build())
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setAssignedAdmin(
        Person.builder()
            .name("Ad Min")
            .email("ad.min@example.com")
            .role("ADMIN")
            .build(),
        null
    );

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    RedactedPersonDto assignedAdmin = formDto.status().current().assignedAdmin();

    assertThat("Unexpected assigned admin name.", assignedAdmin.name(), is("Ad Min"));
    assertThat("Unexpected assigned admin email.", assignedAdmin.email(), is("ad.min@example.com"));
    assertThat("Unexpected assigned admin role.", assignedAdmin.role(), is("ADMIN"));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateStatusWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    StatusInfoDto newStatus = StatusInfoDto.builder()
        .state(SUBMITTED)
        .revision(2)
        .detail(LftfStatusInfoDetailDto.builder()
            .reason("new reason")
            .message("new message")
            .build())
        .timestamp(Instant.now())
        .modifiedBy(RedactedPersonDto.builder()
            .name("Trey Knee")
            .email("trey.knee@example.com")
            .role("new role")
            .build())
        .build();
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .status(StatusDto.builder()
            .current(newStatus)
            .history(List.of(newStatus, StatusInfoDto.builder().build()))
            .submitted(Instant.now())
            .build())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    StatusInfo existingStatus = StatusInfo.builder()
        .state(state)
        .revision(1)
        .detail(StatusDetail.builder()
            .reason("existing reason")
            .message("existing message")
            .build())
        .timestamp(Instant.EPOCH)
        .modifiedBy(Person.builder()
            .name("Anthony Gilliam")
            .email("anthony.gilliam@example.com")
            .role("TRAINEE")
            .build())
        .build();
    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setStatus(Status.builder()
        .current(existingStatus)
        .history(List.of(existingStatus))
        .submitted(Instant.EPOCH)
        .build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    StatusDto status = formDto.status();
    StatusInfoDto current = status.current();

    assertThat("Unexpected state.", current.state(), is(state));
    assertThat("Unexpected revision.", current.revision(), is(1));
    assertThat("Unexpected reason.", current.detail().reason(), is("existing reason"));
    assertThat("Unexpected message.", current.detail().message(), is("existing message"));
    assertThat("Unexpected timestamp.", current.timestamp(), is(Instant.EPOCH));
    assertThat("Unexpected modified name.", current.modifiedBy().name(), is("Anthony Gilliam"));
    assertThat("Unexpected modified email.", current.modifiedBy().email(),
        is("anthony.gilliam@example.com"));
    assertThat("Unexpected modified role.", current.modifiedBy().role(), is("TRAINEE"));

    assertThat("Unexpected history count.", status.history(), hasSize(1));
    StatusInfoDto historical = status.history().get(0);
    assertThat("Unexpected history.", historical, is(current));

    assertThat("Unexpected submitted timestamp.", status.submitted(), is(Instant.EPOCH));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateCreatedWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .created(Instant.now())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setCreated(Instant.EPOCH);

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    assertThat("Unexpected created timestamp.", formDto.created(), is(Instant.EPOCH));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, mode = INCLUDE, names = {"DRAFT", "UNSUBMITTED"})
  void shouldNotUpdateLastModifiedWhenUpdatingLtftFormForTrainee(LifecycleState state) {
    LtftFormDto dtoToSave = LtftFormDto.builder()
        .id(ID)
        .traineeTisId(TRAINEE_ID)
        .lastModified(Instant.now())
        .programmeMembership(ProgrammeMembershipDto.builder()
            .id(PM_UUID)
            .build())
        .build();

    LtftForm existingForm = new LtftForm();
    existingForm.setId(ID);
    existingForm.setTraineeTisId(TRAINEE_ID);
    existingForm.setLifecycleState(state);
    existingForm.setLastModified(Instant.EPOCH);

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(existingForm));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> formDtoOptional = service.updateLtftForm(ID, dtoToSave);
    assertThat("Unexpected form returned.", formDtoOptional.isPresent(), is(true));

    LtftFormDto formDto = formDtoOptional.get();
    assertThat("Unexpected last modified timestamp.", formDto.lastModified(), is(Instant.EPOCH));
  }

  @Test
  void shouldReturnEmptyWhenDeletingIfFormNotFound() {
    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.empty());

    Optional<Boolean> result = service.deleteLtftForm(ID);

    assertThat("Expected empty result when form not found to delete.", result.isPresent(),
        is(false));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"DRAFT"}, mode = EXCLUDE)
  void shouldReturnFalseIfFormCannotTransitionToDeleted(LifecycleState lifecycleState) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(lifecycleState);

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<Boolean> result = service.deleteLtftForm(ID);

    assertThat("Unexpected empty result when form is deleted.", result.isPresent(), is(true));
    assertThat("Expected false result when form cannot transition to DELETED.", result.get(),
        is(false));
  }

  @Test
  void shouldDeleteFormIfCanTransitionToDeleted() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<Boolean> result = service.deleteLtftForm(ID);

    assertThat("Unexpected empty result when form is deleted.", result.isPresent(), is(true));
    assertThat("Expected true result when form is deleted.", result.get(), is(true));
    verify(repository).deleteById(ID);
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "WITHDRAWN"})
  void shouldReturnEmptyWhenTransitionFormNotFound(LifecycleState targetState) {
    when(repository.findByTraineeTisIdAndId(any(), any())).thenReturn(Optional.empty());
    LftfStatusInfoDetailDto detail
        = new LftfStatusInfoDetailDto("reason", "message");

    Optional<LtftFormDto> result = service.changeLtftFormState(ID, detail, targetState);
    assertThat("Unexpected transition result when form not found.", result.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"SUBMITTED", "UNSUBMITTED", "WITHDRAWN"})
  void shouldReturnEmptyWhenFormCannotTransitionToGivenState(LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(APPROVED); //cannot transition to any of the given states
    form.setContent(LtftContent.builder().name("test").build());
    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<LtftFormDto> result = service.changeLtftFormState(ID, detail, targetState);
    assertThat("Unexpected form transition.", result.isPresent(), is(false));
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"UNSUBMITTED", "WITHDRAWN"})
  void shouldReturnEmptyWhenFormCannotTransitionWithoutStatusDetail(LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setContent(LtftContent.builder().name("test").build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<LtftFormDto> result = service.changeLtftFormState(ID, null, targetState);

    assertThat("Unexpected form transition without status detail.", result.isPresent(), is(false));
    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(value = LifecycleState.class, names = {"UNSUBMITTED", "WITHDRAWN"})
  void shouldReturnEmptyWhenFormCannotTransitionWithoutStatusDetailReason(
      LifecycleState targetState) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setContent(LtftContent.builder().name("test").build());
    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto(null, "message");

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    Optional<LtftFormDto> result = service.changeLtftFormState(ID, detail, targetState);

    assertThat("Unexpected form transition without status detail reason.", result.isPresent(),
        is(false));
    verify(repository, never()).save(any());
  }

  @Test
  void shouldSubmitFormWhenValidTransition() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);
    form.setContent(LtftContent.builder().name("test").build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> result = service.submitLtftForm(ID, null);

    assertThat("Unexpected result when form is submitted.", result.isPresent(), is(true));
    verify(repository).save(form);
  }

  @Test
  void shouldSubmitFormWithStatusDetail() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setRevision(2);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);
    form.setContent(LtftContent.builder().name("test").build());

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> result = service.submitLtftForm(ID, detail);

    assertThat("Unexpected result when form is submitted with status detail.", result.isPresent(),
        is(true));
    Person expectedModifiedBy = new Person(TRAINEE_NAME, TRAINEE_EMAIL, TRAINEE_ROLE);
    AbstractAuditedForm.Status.StatusDetail statusDetail = mapper.toStatusDetail(detail);
    AbstractAuditedForm.Status.StatusInfo newFormState = form.getStatus().current();
    assertThat("Unexpected status detail.", newFormState.detail(), is(statusDetail));
    assertThat("Unexpected status modified by.", newFormState.modifiedBy(),
        is(expectedModifiedBy));
    assertThat("Unexpected status modified timestamp.", newFormState.timestamp(),
        is(notNullValue()));
    assertThat("Unexpected form revision.", form.getRevision(), is(2));
    verify(repository).save(form);
    verify(ltftSubmissionHistoryService).takeSnapshot(form);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', textBlock = """
      0 | _001
      1 | _002
      10 | _011
      100 | _101
      1000 | _1001
      """)
  void shouldPopulateFormRefWhenSubmittingForm(int previousFormCount, String refSuffix) {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);
    form.setContent(LtftContent.builder().name("test").build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(repository.countByTraineeTisIdAndStatus_SubmittedIsNotNull(TRAINEE_ID))
        .thenReturn(previousFormCount);
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> result = service.submitLtftForm(ID, null);

    assertThat("Unexpected result when form is submitted.", result.isPresent(), is(true));
    assertThat("Unexpected form ref.", form.getFormRef(), is("ltft_" + TRAINEE_ID + refSuffix));
    verify(repository).save(form);
    verify(ltftSubmissionHistoryService).takeSnapshot(form);
  }

  @Test
  void shouldNotUpdateFormRefWhenResubmittingForm() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);
    form.setContent(LtftContent.builder().name("test").build());

    String formRef = "ltft_" + TRAINEE_ID + "_123";
    form.setFormRef(formRef);

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(repository.countByTraineeTisIdAndStatus_SubmittedIsNotNull(TRAINEE_ID))
        .thenReturn(new Random().nextInt());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> result = service.submitLtftForm(ID, null);

    assertThat("Unexpected result when form is submitted.", result.isPresent(), is(true));
    assertThat("Unexpected form ref.", form.getFormRef(), is(formRef));
    verify(repository).save(form);
    verify(ltftSubmissionHistoryService).takeSnapshot(form);
  }

  @Test
  void shouldUnsubmitFormWithStatusDetailAndIncrementRevision() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setRevision(2);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setContent(LtftContent.builder().name("test").build());
    form.setFormRef("formRef_001");

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> result = service.unsubmitLtftForm(ID, detail);

    assertThat("Unexpected result when form is unsubmitted with status detail.",
        result.isPresent(), is(true));
    Person expectedModifiedBy = new Person(TRAINEE_NAME, TRAINEE_EMAIL, TRAINEE_ROLE);
    AbstractAuditedForm.Status.StatusDetail statusDetail = mapper.toStatusDetail(detail);
    AbstractAuditedForm.Status.StatusInfo newFormState = form.getStatus().current();
    assertThat("Unexpected status detail.", newFormState.detail(), is(statusDetail));
    assertThat("Unexpected status modified by.", newFormState.modifiedBy(),
        is(expectedModifiedBy));
    assertThat("Unexpected status modified timestamp.", newFormState.timestamp(),
        is(notNullValue()));
    assertThat("Unexpected form revision.", form.getRevision(), is(3));
    assertThat("Unexpected form ref.", form.getFormRef(), is("formRef_001"));
    verify(repository).save(form);
    verifyNoInteractions(ltftSubmissionHistoryService);
  }

  @Test
  void shouldWithdrawFormWithStatusDetail() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setRevision(2);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(LifecycleState.SUBMITTED);
    form.setContent(LtftContent.builder().name("test").build());
    form.setFormRef("formRef_001");

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> result = service.withdrawLtftForm(ID, detail);

    assertThat("Unexpected result when form is unsubmitted with status detail.",
        result.isPresent(), is(true));
    Person expectedModifiedBy = new Person(TRAINEE_NAME, TRAINEE_EMAIL, TRAINEE_ROLE);
    AbstractAuditedForm.Status.StatusDetail statusDetail = mapper.toStatusDetail(detail);
    AbstractAuditedForm.Status.StatusInfo newFormState = form.getStatus().current();
    assertThat("Unexpected status detail.", newFormState.detail(), is(statusDetail));
    assertThat("Unexpected status modified by.", newFormState.modifiedBy(),
        is(expectedModifiedBy));
    assertThat("Unexpected status modified timestamp.", newFormState.timestamp(),
        is(notNullValue()));
    assertThat("Unexpected form revision.", form.getRevision(), is(2));
    assertThat("Unexpected form revision.", form.getRevision(), is(2));
    assertThat("Unexpected form ref.", form.getFormRef(), is("formRef_001"));
    verify(repository).save(form);
    verifyNoInteractions(ltftSubmissionHistoryService);
  }

  @Test
  void shouldPublishNotificationWhenAssignedAdminUpdated() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(SUBMITTED);
    form.setFormRef("LTFT_123");

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PersonDto admin = PersonDto.builder().name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build();

    service.assignAdmin(ID, admin);

    ArgumentCaptor<LtftFormDto> ltftFormCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<String> snsTopicCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishLtftFormUpdateEvent(ltftFormCaptor.capture(),
        any(), snsTopicCaptor.capture());

    LtftFormDto capturedForm = ltftFormCaptor.getValue();

    assertThat("Unexpected form ID.", capturedForm.id(), is(ID));
    assertThat("Unexpected trainee ID.", capturedForm.traineeTisId(), is(TRAINEE_ID));
    assertThat("Unexpected form reference.", capturedForm.formRef(), is("LTFT_123"));
    assertThat("Unexpected lifecycle state.", capturedForm.status().current().state(),
        is(SUBMITTED));

    RedactedPersonDto payloadAdmin = capturedForm.status().current().assignedAdmin();
    assertThat("Unexpected assigned admin name.", payloadAdmin.name(), is(ADMIN_NAME));
    assertThat("Unexpected assigned admin email.", payloadAdmin.email(), is(ADMIN_EMAIL));
    assertThat("Unexpected assigned admin role.", payloadAdmin.role(), is("ADMIN"));

    assertThat("Unexpected group ID.", snsTopicCaptor.getValue(),
        is(LTFT_ASSIGNMENT_UPDATE_TOPIC));
    verifyNoMoreInteractions(eventBroadcastService);
  }

  @Test
  void shouldNotPublishNotificationWhenAssignAdminUpdateFails() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(SUBMITTED);
    form.setFormRef("LTFT_123");

    Person admin = Person.builder().name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN").build();
    form.setAssignedAdmin(admin, null);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(any(), any()))
        .thenReturn(Optional.of(form));

    PersonDto adminDto = PersonDto.builder().name(ADMIN_NAME).email(ADMIN_EMAIL).role("ADMIN")
        .build();
    service.assignAdmin(ID, adminDto);

    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldPublishNotificationWhenStatusUpdatedAsAdmin() throws MethodArgumentNotValidException {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);
    form.setFormRef("LTFT_123");

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.updateStatusAsAdmin(ID, SUBMITTED, null);

    ArgumentCaptor<LtftFormDto> ltftFormCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<String> snsTopicCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishLtftFormUpdateEvent(ltftFormCaptor.capture(),
        messageCaptor.capture(), snsTopicCaptor.capture());

    LtftFormDto capturedForm = ltftFormCaptor.getValue();

    assertThat("Unexpected form ID.", capturedForm.id(), is(ID));
    assertThat("Unexpected trainee ID.", capturedForm.traineeTisId(), is(TRAINEE_ID));
    assertThat("Unexpected form reference.", capturedForm.formRef(), is("LTFT_123"));
    assertThat("Unexpected lifecycle state.", capturedForm.status().current().state(),
        is(SUBMITTED));

    assertThat("Unexpected message.", messageCaptor.getValue(),
        is(FORM_ATTRIBUTE_FORM_STATUS));
    assertThat("Unexpected group ID.", snsTopicCaptor.getValue(),
        is(LTFT_STATUS_UPDATE_TOPIC));
    verifyNoMoreInteractions(eventBroadcastService);
  }

  @Test
  void shouldNotPublishNotificationWhenStatusUpdateByAdminFails() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(APPROVED); //cannot transition to any other state
    form.setContent(LtftContent.builder().name("test").build());

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(any(), any()))
        .thenReturn(Optional.of(form));

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");
    assertThrows(MethodArgumentNotValidException.class,
        () -> service.updateStatusAsAdmin(ID, SUBMITTED, detail));

    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldPublishNotificationWhenStatusChangedByTrainee() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(DRAFT);
    form.setFormRef("LTFT_123");

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");
    service.changeLtftFormState(ID, detail, SUBMITTED);

    ArgumentCaptor<LtftFormDto> ltftFormCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<String> snsTopicCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishLtftFormUpdateEvent(ltftFormCaptor.capture(),
        messageCaptor.capture(), snsTopicCaptor.capture());

    LtftFormDto capturedForm = ltftFormCaptor.getValue();

    assertThat("Unexpected form ID.", capturedForm.id(), is(ID));
    assertThat("Unexpected trainee ID.", capturedForm.traineeTisId(), is(TRAINEE_ID));
    assertThat("Unexpected form reference.", capturedForm.formRef(), is("LTFT_123"));
    assertThat("Unexpected lifecycle state.", capturedForm.status().current().state(),
        is(SUBMITTED));

    assertThat("Unexpected message.", messageCaptor.getValue(),
        is(FORM_ATTRIBUTE_FORM_STATUS));
    assertThat("Unexpected group ID.", snsTopicCaptor.getValue(),
        is(LTFT_STATUS_UPDATE_TOPIC));
    verifyNoMoreInteractions(eventBroadcastService);
  }

  @Test
  void shouldNotPublishNotificationWhenStatusUpdateByTraineeFails() {
    LtftForm form = new LtftForm();
    form.setId(ID);
    form.setTraineeTisId(TRAINEE_ID);
    form.setLifecycleState(APPROVED); //cannot transition to any other state
    form.setContent(LtftContent.builder().name("test").build());

    when(repository.findByTraineeTisIdAndId(TRAINEE_ID, ID)).thenReturn(Optional.of(form));

    LftfStatusInfoDetailDto detail = new LftfStatusInfoDetailDto("reason", "message");
    service.changeLtftFormState(ID, detail, SUBMITTED);

    verifyNoInteractions(eventBroadcastService);
  }


  @ParameterizedTest
  @MethodSource("provideValidLtftLifecycleStateTransitions")
  void shouldIncrementRevisionIffTransitionsToStateThatIncrementsRevision(
      LifecycleState currentState, LifecycleState targetState)
      throws MethodArgumentNotValidException {
    LtftForm entity = new LtftForm();
    entity.setLifecycleState(currentState);
    entity.setRevision(0);

    when(repository.findByIdAndContent_ProgrammeMembership_DesignatedBodyCodeIn(
        ID, Set.of(ADMIN_GROUP))).thenReturn(Optional.of(entity));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Optional<LtftFormDto> optionalDto = service.updateStatusAsAdmin(ID, targetState,
        LftfStatusInfoDetailDto.builder()
            .reason("detail reason")
            .message("detail message")
            .build()
    );

    assertThat("Unexpected form presence.", optionalDto.isPresent(), is(true));

    StatusInfoDto current = optionalDto.get().status().current();
    int expectedRevision = targetState.isIncrementsRevision() ? 1 : 0;
    assertThat("Unexpected revision.", current.revision(), is(expectedRevision));
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(value = EmailValidityType.class, mode = INCLUDE, names = {"UNKNOWN"})
  void shouldUpdateTpdNotificationStatusWhenFormExists(EmailValidityType initialStatus) {
    UUID formId = UUID.randomUUID();
    LtftForm form = new LtftForm();
    LtftContent content = LtftContent.builder()
        .tpdEmailValidity(initialStatus)
        .build();
    form.setContent(content);

    when(repository.findById(formId)).thenReturn(Optional.of(form));
    when(repository.save(any(LtftForm.class))).thenAnswer(i -> i.getArgument(0));

    Optional<LtftAdminSummaryDto> result = service.updateTpdNotificationStatus(formId, "SENT");

    verify(repository).save(form);
    assertThat("Unexpected empty result.", result.isPresent(), is(true));
    assertThat("Unexpected TPD status.", result.get().tpd().emailStatus(), is(VALID));
  }

  @Test
  void shouldNotUpdateTpdNotificationStatusWhenStatusUnchanged() {
    UUID formId = UUID.randomUUID();
    LtftForm form = new LtftForm();
    LtftContent content = LtftContent.builder()
        .tpdEmailValidity(INVALID)
        .build();
    form.setContent(content);

    when(repository.findById(formId)).thenReturn(Optional.of(form));

    Optional<LtftAdminSummaryDto> result = service.updateTpdNotificationStatus(formId, "FAILED");

    verify(repository, never()).save(any());
    assertThat("Unexpected empty result.", result.isPresent(), is(true));
    assertThat("Unexpected TPD status.", result.get().tpd().emailStatus(), is(INVALID));
  }

  @Test
  void shouldReturnEmptyOptionalWhenUpdateTpdNotificationFormNotFound() {
    UUID formId = UUID.randomUUID();
    when(repository.findById(formId)).thenReturn(Optional.empty());

    Optional<LtftAdminSummaryDto> result = service.updateTpdNotificationStatus(formId, "SENT");

    verify(repository, never()).save(any());
    assertThat("Unexpected result.", result.isEmpty(), is(true));
  }

  @Test
  void shouldPublishUpdateNotificationWhenTpdStatusUpdated() {
    UUID formId = UUID.randomUUID();
    LtftForm form = new LtftForm();
    form.setId(formId);
    LtftContent content = LtftContent.builder()
        .tpdEmailValidity(UNKNOWN)
        .build();
    form.setContent(content);

    when(repository.findById(formId)).thenReturn(Optional.of(form));
    when(repository.save(any(LtftForm.class))).thenAnswer(i -> i.getArgument(0));

    service.updateTpdNotificationStatus(formId, "SENT");

    ArgumentCaptor<LtftFormDto> ltftDtoCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<String> snsTopicCaptor = ArgumentCaptor.captor();
    verify(eventBroadcastService).publishLtftFormUpdateEvent(ltftDtoCaptor.capture(),
        messageCaptor.capture(), snsTopicCaptor.capture());

    LtftFormDto capturedFormDto = ltftDtoCaptor.getValue();
    assertThat("Unexpected payload TPD status.", capturedFormDto.tpdEmailStatus(), is(VALID));
    assertThat("Unexpected message attribute.", messageCaptor.getValue(),
        is(FORM_ATTRIBUTE_TPD_STATUS));
    assertThat("Unexpected SNS topic.", snsTopicCaptor.getValue(),
        is(LTFT_STATUS_UPDATE_TOPIC));
    verifyNoMoreInteractions(eventBroadcastService);
  }

  @Test
  void shouldNotPublishUpdateNotificationWhenTpdStatusUnchanged() {
    UUID formId = UUID.randomUUID();
    LtftForm form = new LtftForm();
    LtftContent content = LtftContent.builder()
        .tpdEmailValidity(UNKNOWN)
        .build();
    form.setContent(content);

    when(repository.findById(formId)).thenReturn(Optional.of(form));

    service.updateTpdNotificationStatus(formId, "PENDING");

    verifyNoInteractions(eventBroadcastService);
  }

  @Test
  void shouldNotPublishUpdateNotificationWhenTpdStatusUnchangeable() {
    UUID formId = UUID.randomUUID();
    LtftForm form = new LtftForm();
    LtftContent content = LtftContent.builder()
        .tpdEmailValidity(VALID)
        .build();
    form.setContent(content);

    when(repository.findById(formId)).thenReturn(Optional.of(form));

    service.updateTpdNotificationStatus(formId, "PENDING");

    verifyNoInteractions(eventBroadcastService);
  }

  /**
   * A helper function to provide valid LTFT lifecycle state transitions.
   *
   * @return pairs of valid lifecycle state transitions.
   */
  private static Stream<Arguments> provideValidLtftLifecycleStateTransitions() {
    return Stream.of(
        // From DRAFT
        Arguments.of(DRAFT, SUBMITTED),

        // From SUBMITTED
        Arguments.of(SUBMITTED, APPROVED),
        Arguments.of(SUBMITTED, REJECTED),
        Arguments.of(SUBMITTED, UNSUBMITTED),
        Arguments.of(SUBMITTED, WITHDRAWN),

        // From UNSUBMITTED
        Arguments.of(UNSUBMITTED, SUBMITTED),
        Arguments.of(UNSUBMITTED, WITHDRAWN)
    );
  }
}
