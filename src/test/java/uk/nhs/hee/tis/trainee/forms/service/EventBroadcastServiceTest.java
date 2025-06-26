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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
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
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.PersonalDetailsDto;

public class EventBroadcastServiceTest {
  private static final String MESSAGE_ATTRIBUTE = "message-attribute";
  private static final String SNS_TOPIC = "some.sns.topic";

  private static final String TRAINEE_ID = "40";
  private static final String FORM_NAME = "name";
  private static final String FORM_REF = "form-ref";
  private static final Integer FORM_REVISION = 1;

  private static final UUID FORM_ID = UUID.randomUUID();


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
  void shouldNotThrowSnsExceptionsWhenBroadcastingEvent() {
    LtftFormDto ltftFormDto = buildDummyLtftFormDto();

    when(snsClient.publish(any(PublishRequest.class))).thenThrow(SnsException.builder().build());

    assertDoesNotThrow(()
        -> service.publishLtftFormUpdateEvent(ltftFormDto, MESSAGE_ATTRIBUTE, SNS_TOPIC));
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
    LtftFormDto ltftFormDto = LtftFormDto.builder().build();

    service.publishLtftFormUpdateEvent(ltftFormDto, MESSAGE_ATTRIBUTE, SNS_TOPIC);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();
    assertThat("Unexpected message group id.", request.messageGroupId(), notNullValue());

    verifyNoMoreInteractions(snsClient);
  }

  @Test
  void shouldNotSetMessageAttributeIfNotRequired() {
    LtftFormDto ltftFormDto = LtftFormDto.builder().build();

    service.publishLtftFormUpdateEvent(ltftFormDto, null, SNS_TOPIC);

    ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(requestCaptor.capture());

    PublishRequest request = requestCaptor.getValue();

    Map<String, MessageAttributeValue> messageAttributes = request.messageAttributes();
    assertNull(messageAttributes.get(MESSAGE_ATTRIBUTE_KEY),
        "Unexpected message attribute value.");

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
    LtftFormDto ltftFormDto = LtftFormDto.builder()
        .id(FORM_ID)
        .traineeTisId(TRAINEE_ID)
        .formRef(FORM_REF)
        .status(status)
        .name(FORM_NAME)
        .revision(FORM_REVISION)
        .personalDetails(personalDetailsDto)
        .build();
    return ltftFormDto;
  }
}
