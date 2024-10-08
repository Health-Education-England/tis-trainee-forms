/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thymeleaf.templatemode.TemplateMode.HTML;

import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoining;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.GoldGuideVersion;
import uk.nhs.hee.tis.trainee.forms.event.ConditionsOfJoiningPublishedEvent;
import uk.nhs.hee.tis.trainee.forms.event.ConditionsOfJoiningSignedEvent;

class PdfPublisherServiceTest {

  private static final String BUCKET_NAME = "my-bucket";
  private static final String TOPIC_ARN = "my-topic-arn";

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final UUID PROGRAMME_MEMBERSHIP_ID = UUID.randomUUID();
  private static final String PROGRAMME_NAME = "Test Programme";

  private PdfPublisherService service;

  private TemplateEngine templateEngine;
  private S3Template s3Template;
  private SnsTemplate snsTemplate;

  @BeforeEach
  void setUp() {
    templateEngine = mock(TemplateEngine.class);
    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn("<html></html>");

    s3Template = mock(S3Template.class);
    snsTemplate = mock(SnsTemplate.class);

    service = new PdfPublisherService(templateEngine, s3Template, BUCKET_NAME, snsTemplate,
        TOPIC_ARN);
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void shouldGenerateConditionsOfJoiningFromTemplate(GoldGuideVersion version)
      throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(version, Instant.now());
    ConditionsOfJoiningSignedEvent event = new ConditionsOfJoiningSignedEvent(TRAINEE_ID,
        PROGRAMME_MEMBERSHIP_ID, PROGRAMME_NAME, conditionsOfJoining);

    service.publishConditionsOfJoining(event);

    ArgumentCaptor<TemplateSpec> templateCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateEngine).process(templateCaptor.capture(), contextCaptor.capture());

    TemplateSpec templateSpec = templateCaptor.getValue();
    String filename = version.toString().toLowerCase() + ".html";
    assertThat("Unexpected template.", templateSpec.getTemplate(),
        is("conditions-of-joining" + File.separatorChar + filename));
    assertThat("Unexpected template selectors.", templateSpec.getTemplateSelectors(), nullValue());
    assertThat("Unexpected template mode.", templateSpec.getTemplateMode(), is(HTML));
    assertThat("Unexpected template resolution attributes.",
        templateSpec.getTemplateResolutionAttributes(), nullValue());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected locale.", context.getLocale(), is(Locale.getDefault()));

    Set<String> variableNames = context.getVariableNames();
    assertThat("Unexpected variable count.", variableNames.size(), is(1));
    assertThat("Unexpected variable value.", context.getVariable("event"), is(event));
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void shouldUploadGeneratedConditionsOfJoining(GoldGuideVersion version)
      throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(version, Instant.now());
    ConditionsOfJoiningSignedEvent event = new ConditionsOfJoiningSignedEvent(TRAINEE_ID,
        PROGRAMME_MEMBERSHIP_ID, PROGRAMME_NAME, conditionsOfJoining);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    service.publishConditionsOfJoining(event);

    String key = TRAINEE_ID + "/forms/coj/" + PROGRAMME_MEMBERSHIP_ID + ".pdf";
    ArgumentCaptor<ByteArrayInputStream> contentCaptor = ArgumentCaptor.captor();
    verify(s3Template).upload(eq(BUCKET_NAME), eq(key), contentCaptor.capture());

    ByteArrayInputStream contentIs = contentCaptor.getValue();
    PDDocument pdf = PDDocument.load(contentIs);
    String pdfText = new PDFTextStripper().getText(pdf);

    assertThat("Unexpected content.", pdfText, is("test content" + System.lineSeparator()));
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void shouldSendNotificationOfGeneratedConditionsOfJoining(GoldGuideVersion version)
      throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(version, Instant.now());
    ConditionsOfJoiningSignedEvent event = new ConditionsOfJoiningSignedEvent(TRAINEE_ID,
        PROGRAMME_MEMBERSHIP_ID, PROGRAMME_NAME, conditionsOfJoining);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    service.publishConditionsOfJoining(event);

    ArgumentCaptor<SnsNotification<ConditionsOfJoiningPublishedEvent>> notificationCaptor =
        ArgumentCaptor.captor();
    verify(snsTemplate).sendNotification(eq(TOPIC_ARN), notificationCaptor.capture());

    SnsNotification<ConditionsOfJoiningPublishedEvent> notification = notificationCaptor.getValue();
    assertThat("Unexpected group ID.", notification.getGroupId(),
        is(PROGRAMME_MEMBERSHIP_ID.toString()));

    Map<String, Object> headers = notification.getHeaders();
    assertThat("Unexpected header count.", headers.size(), is(3));
    assertThat("Unexpected header value.", headers.get("message-group-id"),
        is(PROGRAMME_MEMBERSHIP_ID.toString()));
    assertThat("Unexpected header value.", headers.get("pdf_type"), is("FORM"));
    assertThat("Unexpected header value.", headers.get("form_type"), is("COJ"));

    ConditionsOfJoiningPublishedEvent payload = notification.getPayload();
    assertThat("Unexpected programme membership ID.", payload.getTraineeId(), is(TRAINEE_ID));
    assertThat("Unexpected programme membership ID.", payload.getProgrammeMembershipId(),
        is(PROGRAMME_MEMBERSHIP_ID));
    assertThat("Unexpected conditions of joining.", payload.getConditionsOfJoining(),
        is(conditionsOfJoining));
    assertThat("Unexpected PDF bucket.", payload.getPdf().bucket(), is(BUCKET_NAME));

    String key = TRAINEE_ID + "/forms/coj/" + PROGRAMME_MEMBERSHIP_ID + ".pdf";
    assertThat("Unexpected PDF key.", payload.getPdf().key(), is(key));
  }
}
