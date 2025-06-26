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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;

/**
 * A service for broadcasting LTFT form events to SNS.
 */
@Slf4j
@Service
public class EventBroadcastService {

  protected static final String MESSAGE_ATTRIBUTE_KEY = "trigger";

  private final SnsClient snsClient;

  EventBroadcastService(SnsClient snsClient) {
    this.snsClient = snsClient;
  }

  /**
   * Publish a LTFT form update to SNS.
   *
   * @param formDto          The LTFT form DTO to publish.
   * @param messageAttribute The message attribute to include in the request, or null if not needed.
   * @param snsTopic         The SNS topic ARN to publish to.
   */
  public void publishLtftFormUpdateEvent(LtftFormDto formDto, String messageAttribute,
      String snsTopic) {

    if (formDto == null) {
      log.warn("LTFT form is null, skipping SNS publish.");
      return;
    }
    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    JsonNode eventJson = objectMapper.valueToTree(formDto);

    PublishRequest request = buildSnsRequest(snsTopic, eventJson, messageAttribute, formDto.id());

    if (request != null) {
      try {
        snsClient.publish(request);
        log.info("Broadcast event sent to SNS for LTFT form {} with attribute {}.",
            formDto.id(), messageAttribute);
      } catch (SnsException e) {
        String message = String.format(
            "Failed to broadcast event to SNS topic '%s' for LTFT form '%s'",
            snsTopic, formDto.id());
        log.error(message, e);
      }
    }
  }

  /**
   * Build an SNS publish request.
   *
   * @param snsTopic         The SNS topic ARN to publish to.
   * @param eventJson        The SNS message contents.
   * @param messageAttribute The message attribute to include in the request, or null if not needed.
   * @param id               The event UUID.
   * @return the built request.
   */
  private PublishRequest buildSnsRequest(String snsTopic, JsonNode eventJson,
      String messageAttribute, UUID id) {
    if (snsTopic == null || snsTopic.isBlank()) {
      log.warn("SNS topic ARN is null or blank, skipping SNS publish.");
      return null;
    }

    PublishRequest.Builder request = PublishRequest.builder()
        .message(eventJson.toString())
        .topicArn(snsTopic);

    if (messageAttribute != null) {
      MessageAttributeValue messageAttributeValue = MessageAttributeValue.builder()
          .dataType("String")
          .stringValue(messageAttribute)
          .build();
      request.messageAttributes(Map.of(MESSAGE_ATTRIBUTE_KEY, messageAttributeValue));
    }

    String groupId = id == null ? UUID.randomUUID().toString() : id.toString();
    request.messageGroupId(groupId);

    return request.build();
  }
}
