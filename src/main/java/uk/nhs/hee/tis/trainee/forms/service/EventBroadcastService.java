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
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;

/**
 * A service for broadcasting form events to SNS.
 */
@Slf4j
@Service
public class EventBroadcastService {

  protected static final String MESSAGE_ATTRIBUTE_KEY = "trigger";
  protected static final String MESSAGE_ATTRIBUTE_DEFAULT_VALUE = "default";

  private final SnsClient snsClient;
  private final ObjectMapper objectMapper;

  EventBroadcastService(SnsClient snsClient) {
    this.snsClient = snsClient;
    objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Publish a LTFT form update to SNS.
   *
   * @param formDto          The LTFT form DTO to publish.
   * @param messageAttribute The message attribute to include in the request (a default is used if
   *                         this is missing).
   * @param snsTopic         The SNS topic ARN to publish to.
   */
  public void publishLtftFormUpdateEvent(LtftFormDto formDto, String messageAttribute,
      String snsTopic) {

    if (formDto == null) {
      log.warn("LTFT form is null, skipping SNS publish.");
      return;
    }

    publishJsonEvent(objectMapper.valueToTree(formDto), messageAttribute, snsTopic,
        formDto.id() == null ? null : formDto.id().toString());
  }

  /**
   * Publish a Form R Part A event to SNS.
   *
   * @param formDto          The Form R Part A DTO to publish.
   * @param messageAttribute The message attribute to include in the request (a default is used if
   *                         this is missing).
   * @param snsTopic         The SNS topic ARN to publish to.
   */
  public void publishFormRPartAEvent(FormRPartADto formDto, String messageAttribute,
      String snsTopic) {

    if (formDto == null) {
      log.warn("Form R Part A is null, skipping SNS publish.");
      return;
    }
    publishJsonEvent(objectMapper.valueToTree(formDto), messageAttribute, snsTopic,
        formDto.getId());
  }

  /**
   * Publish a Form R Part B event to SNS.
   *
   * @param formDto          The Form R Part B DTO to publish.
   * @param messageAttribute The message attribute to include in the request (a default is used if
   *                         this is missing).
   * @param snsTopic         The SNS topic ARN to publish to.
   */
  public void publishFormRPartBEvent(FormRPartBDto formDto, String messageAttribute,
      String snsTopic) {

    if (formDto == null) {
      log.warn("Form R Part B is null, skipping SNS publish.");
      return;
    }
    publishJsonEvent(objectMapper.valueToTree(formDto), messageAttribute, snsTopic,
        formDto.getId());
  }

  /**
   * Publish a generic JSON event to SNS.
   *
   * @param eventJson        The event JSON to publish.
   * @param messageAttribute The message attribute to include in the request.
   * @param snsTopic         The SNS topic ARN to publish to.
   * @param id               The event id.
   */
  private void publishJsonEvent(JsonNode eventJson, String messageAttribute,
      String snsTopic, String id) {

    if (isJsonNodeEmpty(eventJson)) {
      log.warn("Event JSON is empty, skipping SNS publish.");
      return;
    }

    PublishRequest request = buildSnsRequest(snsTopic, eventJson, messageAttribute, id);

    if (request != null) {
      try {
        snsClient.publish(request);
        log.info("Broadcast event sent to SNS for id {} with attribute {}.",
            id, messageAttribute);
      } catch (SnsException e) {
        String message = String.format(
            "Failed to broadcast event to SNS topic '%s' for id '%s'",
            snsTopic, id);
        log.error(message, e);
        throw e;
      }
    }
  }

  /**
   * Build an SNS publish request.
   *
   * @param snsTopic         The SNS topic ARN to publish to.
   * @param eventJson        The SNS message contents.
   * @param messageAttribute The message attribute to include in the request.
   * @param id               The event id.
   * @return the built request.
   */
  private PublishRequest buildSnsRequest(String snsTopic, JsonNode eventJson,
      String messageAttribute, String id) {
    if (snsTopic == null || snsTopic.isBlank()) {
      log.warn("SNS topic ARN is null or blank, skipping SNS publish.");
      return null;
    }

    PublishRequest.Builder request = PublishRequest.builder()
        .message(eventJson.toString())
        .topicArn(snsTopic);

    MessageAttributeValue messageAttributeValue = MessageAttributeValue.builder()
        .dataType("String")
        .stringValue(messageAttribute == null ? MESSAGE_ATTRIBUTE_DEFAULT_VALUE : messageAttribute)
        .build();
    request.messageAttributes(Map.of(MESSAGE_ATTRIBUTE_KEY, messageAttributeValue));

    String groupId = id == null ? UUID.randomUUID().toString() : id;
    request.messageGroupId(groupId);

    return request.build();
  }

  /**
   * Check if a JsonNode is empty (no fields or all fields are null).
   *
   * @param jsonNode The JSON node to check.
   * @return true if the node is empty or contains only nulls, false otherwise.
   */
  private boolean isJsonNodeEmpty(JsonNode jsonNode) {
    if (jsonNode.isEmpty()) {
      return true;
    }

    // Check if all fields are null
    var iterator = jsonNode.fields();
    while (iterator.hasNext()) {
      if (!iterator.next().getValue().isNull()) {
        return false;
      }
    }
    return true;
  }
}
