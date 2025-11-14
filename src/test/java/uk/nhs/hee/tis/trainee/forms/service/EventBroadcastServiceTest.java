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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.nhs.hee.tis.trainee.forms.service.EventBroadcastService.MESSAGE_ATTRIBUTE_DEFAULT_VALUE;
import static uk.nhs.hee.tis.trainee.forms.service.EventBroadcastService.MESSAGE_ATTRIBUTE_KEY;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonalDetailsDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.LifecycleState;

/**
 * Unit tests for the {@link EventBroadcastService}.
 */
class EventBroadcastServiceTest {
  private static final String MESSAGE_ATTRIBUTE = "message-attribute";
  private static final String SNS_TOPIC = "some.sns.topic";

  private static final String TRAINEE_ID = "40";
  private static final String FORM_NAME = "name";
  private static final String FORM_REF = "form-ref";
  private static final Integer FORM_REVISION = 1;

  private static final UUID FORM_ID = UUID.randomUUID();
  private static final String FORM_ID_STRING = FORM_ID.toString();

  private EventBroadcastService service;

  private ObjectMapper objectMapper;
  private SnsClient snsClient;

  @BeforeEach
  void setUp() {
    snsClient = mock(SnsClient.class);
    objectMapper = new ObjectMapper();
    service = new EventBroadcastService(snsClient);
  }

  @Test
  void shouldNotPublishLtftFormUpdateEventIfEventDtoIsNull() {
    service.publishLtftFormUpdateEvent(null, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    verifyNoInteractions(snsClient);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldNotPublishLtftFormUpdateEventIfSnsTopicMissing(String snsTopic) {
    LtftFormDto ltftFormDto = buildDummyLtftFormDto();
    service.publishLtftFormUpdateEvent(ltftFormDto, MESSAGE_ATTRIBUTE, snsTopic);

    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldRethrowSnsExceptionsWhenBroadcastingEvent() {
    LtftFormDto ltftFormDto = buildDummyLtftFormDto();

    when(snsClient.publish(any(PublishRequest.class))).thenThrow(SnsException.builder().build());

    assertThrows(SnsException.class,
        () -> service.publishLtftFormUpdateEvent(ltftFormDto, MESSAGE_ATTRIBUTE, SNS_TOPIC));
  }

  @Test
  void shouldSetMessageGroupIdOnIssuedEvent() {
    LtftFormDto ltftFormDto = buildDummyLtftFormDto();

    service.publishLtftFormUpdateEvent(ltftFormDto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected message group id.", request.messageGroupId(), is(FORM_ID.toString()));

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldSetArbitraryMessageGroupIdOnIssuedEventIfNoId() {
    LtftFormDto ltftFormDto = LtftFormDto.builder()
        .id(FORM_ID)
        .build();

    service.publishLtftFormUpdateEvent(ltftFormDto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected message group id.", request.messageGroupId(), notNullValue());

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldIncludeDefaultMessageAttributeIfNotProvided() {
    LtftFormDto ltftFormDto = LtftFormDto.builder()
        .id(FORM_ID)
        .build();

    service.publishLtftFormUpdateEvent(ltftFormDto, null, SNS_TOPIC);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();

    Map<String, MessageAttributeValue> messageAttributes = request.messageAttributes();
    assertThat("Unexpected message attribute.",
        messageAttributes.get(MESSAGE_ATTRIBUTE_KEY).stringValue(),
        is(MESSAGE_ATTRIBUTE_DEFAULT_VALUE));

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldPublishLtftFormEvent() throws JsonProcessingException {
    LtftFormDto ltftFormDto = buildDummyLtftFormDto();

    service.publishLtftFormUpdateEvent(ltftFormDto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected topic ARN.", request.topicArn(), is(SNS_TOPIC));

    Map<String, Object> message = objectMapper.readValue(request.message(),
        new TypeReference<>() {
        });
    assertThat("Unexpected message id.", message.get("id"), is(FORM_ID.toString()));
    assertThat("Unexpected trainee id.", message.get("traineeTisId"), is(TRAINEE_ID));
    assertThat("Unexpected form ref.", message.get("formRef"), is(FORM_REF));
    assertThat("Unexpected form name.", message.get("name"), is(FORM_NAME));
    assertThat("Unexpected form revision.", message.get("revision"), is(FORM_REVISION));

    LinkedHashMap<?, ?> personalDetails
        = objectMapper.convertValue(message.get("personalDetails"), LinkedHashMap.class);
    assertThat("Unexpected personal details id.", personalDetails.get("id"), is(TRAINEE_ID));

    Map<String, MessageAttributeValue> messageAttributes = request.messageAttributes();
    assertThat("Unexpected message attribute value.",
        messageAttributes.get(MESSAGE_ATTRIBUTE_KEY).stringValue(), is(MESSAGE_ATTRIBUTE));
    assertThat("Unexpected message attribute data type.",
        messageAttributes.get(MESSAGE_ATTRIBUTE_KEY).dataType(), is("String"));

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldNotPublishFormRPartAEventIfEventDtoIsNull() {
    service.publishFormRPartAEvent(null, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    verifyNoInteractions(snsClient);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldNotPublishFormRPartAEventIfSnsTopicMissing(String snsTopic) {
    FormRPartADto formRPartADto = buildDummyFormRPartADto();
    service.publishFormRPartAEvent(formRPartADto, MESSAGE_ATTRIBUTE, snsTopic);

    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldPublishFormRPartAEvent() throws JsonProcessingException {
    FormRPartADto formRPartADto = buildDummyFormRPartADto();

    service.publishFormRPartAEvent(formRPartADto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected topic ARN.", request.topicArn(), is(SNS_TOPIC));
    assertThat("Unexpected message group id.", request.messageGroupId(), is(FORM_ID_STRING));

    Map<String, Object> message = objectMapper.readValue(request.message(),
        new TypeReference<>() {
        });
    assertThat("Unexpected message id.", message.get("id"), is(FORM_ID_STRING));
    assertThat("Unexpected trainee id.", message.get("traineeTisId"), is(TRAINEE_ID));

    Map<String, MessageAttributeValue> messageAttributes = request.messageAttributes();
    assertThat("Unexpected message attribute value.",
        messageAttributes.get(MESSAGE_ATTRIBUTE_KEY).stringValue(), is(MESSAGE_ATTRIBUTE));
    assertThat("Unexpected message attribute data type.",
        messageAttributes.get(MESSAGE_ATTRIBUTE_KEY).dataType(), is("String"));

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldNotPublishFormRPartBEventIfEventDtoIsNull() {
    service.publishFormRPartBEvent(null, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    verifyNoInteractions(snsClient);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldNotPublishFormRPartBEventIfSnsTopicMissing(String snsTopic) {
    FormRPartBDto formRPartBDto = buildDummyFormRPartBDto();
    service.publishFormRPartBEvent(formRPartBDto, MESSAGE_ATTRIBUTE, snsTopic);

    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldPublishFormRPartBEvent() throws JsonProcessingException {
    FormRPartBDto formRPartBDto = buildDummyFormRPartBDto();

    service.publishFormRPartBEvent(formRPartBDto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected topic ARN.", request.topicArn(), is(SNS_TOPIC));
    assertThat("Unexpected message group id.", request.messageGroupId(), is(FORM_ID_STRING));

    Map<String, Object> message = objectMapper.readValue(request.message(),
        new TypeReference<>() {
        });
    assertThat("Unexpected message id.", message.get("id"), is(FORM_ID_STRING));
    assertThat("Unexpected trainee id.", message.get("traineeTisId"), is(TRAINEE_ID));

    Map<String, MessageAttributeValue> messageAttributes = request.messageAttributes();
    assertThat("Unexpected message attribute value.",
        messageAttributes.get(MESSAGE_ATTRIBUTE_KEY).stringValue(), is(MESSAGE_ATTRIBUTE));
    assertThat("Unexpected message attribute data type.",
        messageAttributes.get(MESSAGE_ATTRIBUTE_KEY).dataType(), is("String"));

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldNotPublishLtftFormEventIfEventJsonIsEmpty() {
    LtftFormDto ltftFormDto = LtftFormDto.builder().build();

    service.publishLtftFormUpdateEvent(ltftFormDto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldNotPublishFormRPartAEventIfEventJsonIsEmpty() {
    FormRPartADto formRPartADto = new FormRPartADto();

    service.publishFormRPartAEvent(formRPartADto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    verifyNoInteractions(snsClient);
  }

  @Test
  void shouldNotPublishFormRPartBEventIfEventJsonIsEmpty() {
    FormRPartBDto formRPartBDto = new FormRPartBDto();

    service.publishFormRPartBEvent(formRPartBDto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    verifyNoInteractions(snsClient);
  }

  /**
   * Return a largely empty LTFT form DTO for test purposes.
   *
   * @return the LTFT DTO entity.
   */
  private LtftFormDto buildDummyLtftFormDto() {
    PersonalDetailsDto personalDetailsDto = PersonalDetailsDto.builder()
        .id(TRAINEE_ID)
        .build();
    LtftFormDto.StatusDto status = LtftFormDto.StatusDto.builder().build();
    return LtftFormDto.builder()
        .id(FORM_ID)
        .traineeTisId(TRAINEE_ID)
        .formRef(FORM_REF)
        .status(status)
        .name(FORM_NAME)
        .revision(FORM_REVISION)
        .personalDetails(personalDetailsDto)
        .build();
  }

  /**
   * Return a Form R Part A DTO for test purposes.
   *
   * @return the Form R Part A DTO entity.
   */
  private FormRPartADto buildDummyFormRPartADto() {
    FormRPartADto dto = new FormRPartADto();
    dto.setId(FORM_ID.toString());
    dto.setTraineeTisId(TRAINEE_ID);
    dto.setLifecycleState(LifecycleState.SUBMITTED);
    return dto;
  }

  /**
   * Return a Form R Part B DTO for test purposes.
   *
   * @return the Form R Part B DTO entity.
   */
  private FormRPartBDto buildDummyFormRPartBDto() {
    FormRPartBDto dto = new FormRPartBDto();
    dto.setId(FORM_ID.toString());
    dto.setTraineeTisId(TRAINEE_ID);
    dto.setLifecycleState(LifecycleState.SUBMITTED);
    return dto;
  }
}
