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

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.slf4j.Slf4jLogger;
import com.openhtmltopdf.util.XRLog;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import uk.nhs.hee.tis.trainee.forms.dto.PublishedPdf;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.GoldGuideVersion;
import uk.nhs.hee.tis.trainee.forms.event.ConditionsOfJoiningPublishedEvent;
import uk.nhs.hee.tis.trainee.forms.event.ConditionsOfJoiningSignedEvent;

/**
 * A service handling PDF generation and publishing via S3 and SNS.
 */
@Slf4j
@Service
public class PdfPublisherService {

  private static final String PDF_TYPE = "pdf_type";
  private static final String PDF_TYPE_FORM = "FORM";

  private static final String FORM_TYPE = "form_type";
  private static final String FORM_TYPE_COJ = "COJ";

  private final TemplateEngine templateEngine;

  private final S3Template s3Template;
  private final String publishBucket;

  private final SnsTemplate snsTemplate;
  private final String publishTopic;

  private final ZoneId timezone;

  /**
   * A service handling PDF generation and publishing via S3 and SNS.
   *
   * @param templateEngine The template engine to use for creating an HTML version of the form.
   * @param s3Template     The S3 template to use for uploaded.
   * @param publishBucket  The bucket to upload the PDFs to.
   * @param snsTemplate    The SNS template to use for notifying.
   * @param publishTopic   The topic to send PDF publish notifications to.
   */
  public PdfPublisherService(TemplateEngine templateEngine,
      S3Template s3Template, @Value("${application.file-store.bucket}") String publishBucket,
      SnsTemplate snsTemplate, @Value("${application.aws.sns.pdf-generated}") String publishTopic,
      @Value("${application.timezone}") ZoneId timezone) {
    this.templateEngine = templateEngine;
    this.s3Template = s3Template;
    this.publishBucket = publishBucket;
    this.snsTemplate = snsTemplate;
    this.publishTopic = publishTopic;
    this.timezone = timezone;

    XRLog.setLoggerImpl(new Slf4jLogger());
  }

  /**
   * Publish a Conditions of Joining PDF, the PDF will be uploaded to a file store and a
   * notification sent to inform downstream services where to find it.
   *
   * @param signedEvent The COJ signed event to publish a PDF for.
   * @throws IOException If a valid PDF could not be created.
   */
  public void publishConditionsOfJoining(ConditionsOfJoiningSignedEvent signedEvent)
      throws IOException {
    GoldGuideVersion version = signedEvent.conditionsOfJoining().version();
    String traineeId = signedEvent.traineeId();
    String programmeMembershipId = signedEvent.programmeMembershipId().toString();

    log.info("Publishing a {} Conditions of Joining for trainee '{}' and programme membership '{}'",
        version, traineeId, programmeMembershipId);

    TemplateSpec templateSpec = version.getConditionsOfJoiningTemplate();
    byte[] pdf = generatePdf(templateSpec, Map.of("event", signedEvent));

    PublishedPdf pdfRef = upload(traineeId, FORM_TYPE_COJ, programmeMembershipId, pdf);

    ConditionsOfJoiningPublishedEvent publishEvent = new ConditionsOfJoiningPublishedEvent(
        signedEvent, pdfRef);
    publish(FORM_TYPE_COJ, programmeMembershipId, publishEvent);
  }

  /**
   * Generated a PDF version of the form.
   *
   * @param templateSpec      The template spec to use.
   * @param templateVariables The variables to insert in to the template.
   * @return The generated PDF as an array of bytes.
   * @throws IOException If the renderer could not build a valid PDF.
   */
  private byte[] generatePdf(TemplateSpec templateSpec, Map<String, Object> templateVariables)
      throws IOException {
    log.info("Generating a PDF using template '{}'.", templateSpec.getTemplate());

    Map<String, Object> enhancedVariables = new HashMap<>(templateVariables);
    enhancedVariables.put("timezone", timezone.getId());

    String body = templateEngine.process(templateSpec,
        new Context(Locale.ENGLISH, enhancedVariables));
    Document parsedBody = Jsoup.parse(body);

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    new PdfRendererBuilder()
        .toStream(os)
        .withW3cDocument(W3CDom.convert(parsedBody), "classpath:/")
        .run();

    return os.toByteArray();
  }

  /**
   * Upload the form PDF to S3.
   *
   * @param traineeId The trainee ID the form belongs to.
   * @param formType  The type of form being uploaded.
   * @param filename  The filename without extension e.g. PM ID.
   * @param pdf       The bytes of the PDF file.
   * @return An object referencing the published PDF.
   */
  private PublishedPdf upload(String traineeId, String formType, String filename, byte[] pdf) {
    log.info("Uploading generated {} for trainee '{}': {}.pdf", formType, filename, traineeId);
    String key = String.format("%s/forms/%s/%s.pdf", traineeId, formType.toLowerCase(), filename);

    s3Template.upload(publishBucket, key, new ByteArrayInputStream(pdf));
    return new PublishedPdf(publishBucket, key);
  }

  /**
   * Publish a notification for the uploaded form PDF.
   *
   * @param formType The type of the form e.g. COJ.
   * @param groupId  The message group e.g. PM ID.
   * @param message  The message contents to publish.
   * @param <T>      The object type of the message.
   */
  private <T> void publish(String formType, String groupId, T message) {
    log.info("Publishing notification for generated {} PDF with reference '{}'.", formType,
        groupId);
    SnsNotification<T> notification = SnsNotification.builder(message)
        .groupId(groupId)
        .header(PDF_TYPE, PDF_TYPE_FORM)
        .header(FORM_TYPE, formType)
        .build();

    snsTemplate.sendNotification(publishTopic, notification);
  }
}
