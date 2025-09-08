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
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.thymeleaf.templatemode.TemplateMode.HTML;

import io.awspring.cloud.s3.S3OutputStreamProvider;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsTemplate;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import software.amazon.awssdk.services.s3.S3Client;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoining;
import uk.nhs.hee.tis.trainee.forms.dto.ConditionsOfJoiningPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartADto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartAPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBDto;
import uk.nhs.hee.tis.trainee.forms.dto.FormRPartBPdfRequestDto;
import uk.nhs.hee.tis.trainee.forms.dto.LtftFormDto;
import uk.nhs.hee.tis.trainee.forms.dto.enumeration.GoldGuideVersion;
import uk.nhs.hee.tis.trainee.forms.event.ConditionsOfJoiningPublishedEvent;
import uk.nhs.hee.tis.trainee.forms.event.FormRPartAPublishedEvent;
import uk.nhs.hee.tis.trainee.forms.event.FormRPartBPublishedEvent;

class PdfServiceTest {

  private static final String BUCKET_NAME = "my-bucket";
  private static final String TOPIC_ARN = "my-topic-arn";

  private static final String TRAINEE_ID = UUID.randomUUID().toString();
  private static final UUID PROGRAMME_MEMBERSHIP_ID = UUID.randomUUID();
  private static final String PROGRAMME_NAME = "Test Programme";
  private static final String FORM_ID = "form-id";

  private static final ZoneId TIMEZONE = ZoneId.of("Europe/London");

  private PdfService service;

  private TemplateEngine templateEngine;
  private S3Template s3Template;
  private SnsTemplate snsTemplate;

  @BeforeEach
  void setUp() {
    templateEngine = mock(TemplateEngine.class);
    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn("<html></html>");

    s3Template = mock(S3Template.class);
    snsTemplate = mock(SnsTemplate.class);

    service = new PdfService(templateEngine, s3Template, BUCKET_NAME, snsTemplate, TOPIC_ARN,
        TIMEZONE);
  }

  @Test
  void shouldNotGetUploadedPdfWhenNotExists() {
    S3Resource pdf = mock(S3Resource.class);
    when(pdf.exists()).thenReturn(false);

    String key = "uploaded-key";
    when(s3Template.download(BUCKET_NAME, key)).thenReturn(pdf);

    Optional<Resource> uploadedPdf = service.getUploadedPdf(key);

    assertThat("Unexpected uploaded PDF presence.", uploadedPdf.isPresent(), is(false));
  }

  @Test
  void shouldGetUploadedPdfWhenExists() {
    S3Resource pdf = mock(S3Resource.class);
    when(pdf.exists()).thenReturn(true);

    String key = "uploaded-key";
    when(s3Template.download(BUCKET_NAME, key)).thenReturn(pdf);

    Optional<Resource> uploadedPdf = service.getUploadedPdf(key);

    assertThat("Unexpected uploaded PDF.", uploadedPdf.get(), is(pdf));
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void shouldGenerateConditionsOfJoiningFromTemplate(GoldGuideVersion version)
      throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(version, Instant.now());
    ConditionsOfJoiningPdfRequestDto request = new ConditionsOfJoiningPdfRequestDto(TRAINEE_ID,
        PROGRAMME_MEMBERSHIP_ID, PROGRAMME_NAME, conditionsOfJoining);

    service.generateConditionsOfJoining(request, false);

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
    assertThat("Unexpected locale.", context.getLocale(), is(Locale.ENGLISH));

    Set<String> variableNames = context.getVariableNames();
    assertThat("Unexpected variable count.", variableNames.size(), is(2));
    assertThat("Unexpected event value.", context.getVariable("var"), sameInstance(request));
    assertThat("Unexpected timezone value.", context.getVariable("timezone"), is(TIMEZONE.getId()));
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void shouldUploadGeneratedConditionsOfJoining(GoldGuideVersion version)
      throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(version, Instant.now());
    ConditionsOfJoiningPdfRequestDto request = new ConditionsOfJoiningPdfRequestDto(TRAINEE_ID,
        PROGRAMME_MEMBERSHIP_ID, PROGRAMME_NAME, conditionsOfJoining);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    service.generateConditionsOfJoining(request, false);

    String key = TRAINEE_ID + "/forms/coj/" + PROGRAMME_MEMBERSHIP_ID + ".pdf";
    ArgumentCaptor<ByteArrayInputStream> contentCaptor = ArgumentCaptor.captor();
    verify(s3Template).upload(eq(BUCKET_NAME), eq(key), contentCaptor.capture());

    ByteArrayInputStream contentIs = contentCaptor.getValue();
    PDDocument pdf = Loader.loadPDF(new RandomAccessReadBuffer(contentIs));
    String pdfText = new PDFTextStripper().getText(pdf);

    assertThat("Unexpected content.", pdfText, is("test content" + System.lineSeparator()));
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void shouldSendNotificationOfGeneratedConditionsOfJoiningWhenPublishTrue(GoldGuideVersion version)
      throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(version, Instant.now());
    ConditionsOfJoiningPdfRequestDto request = new ConditionsOfJoiningPdfRequestDto(TRAINEE_ID,
        PROGRAMME_MEMBERSHIP_ID, PROGRAMME_NAME, conditionsOfJoining);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    String key = TRAINEE_ID + "/forms/coj/" + PROGRAMME_MEMBERSHIP_ID + ".pdf";
    S3Resource uploaded = S3Resource.create("s3://my-bucket/" + key, mock(S3Client.class),
        mock(S3OutputStreamProvider.class));
    when(s3Template.upload(any(), any(), any())).thenReturn(uploaded);

    service.generateConditionsOfJoining(request, true);

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

    assertThat("Unexpected PDF key.", payload.getPdf().key(), is(key));
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void shouldNotSendNotificationOfGeneratedConditionsOfJoiningWhenPublishFalse(
      GoldGuideVersion version)
      throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(version, Instant.now());
    ConditionsOfJoiningPdfRequestDto request = new ConditionsOfJoiningPdfRequestDto(TRAINEE_ID,
        PROGRAMME_MEMBERSHIP_ID, PROGRAMME_NAME, conditionsOfJoining);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    service.generateConditionsOfJoining(request, false);

    verifyNoInteractions(snsTemplate);
  }

  @ParameterizedTest
  @EnumSource(GoldGuideVersion.class)
  void shouldReturnGeneratedConditionsOfJoining(GoldGuideVersion version) throws IOException {
    ConditionsOfJoining conditionsOfJoining = new ConditionsOfJoining(version, Instant.now());
    ConditionsOfJoiningPdfRequestDto request = new ConditionsOfJoiningPdfRequestDto(TRAINEE_ID,
        PROGRAMME_MEMBERSHIP_ID, PROGRAMME_NAME, conditionsOfJoining);

    String content = "<html>test content</html>";
    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(content);

    S3Resource uploaded = mock(S3Resource.class);
    when(s3Template.upload(any(), any(), any())).thenReturn(uploaded);

    byte[] contentBytes = content.getBytes();
    when(uploaded.getContentAsByteArray()).thenReturn(contentBytes);

    Resource resource = service.generateConditionsOfJoining(request, false);

    assertThat("Unexpected content.", resource.getContentAsByteArray(), is(contentBytes));
  }

  @Test
  void shouldGenerateLtftFromTemplate() throws IOException {
    LtftFormDto dto = LtftFormDto.builder()
        .id(UUID.randomUUID())
        .build();

    service.generatePdf(dto, "admin");

    ArgumentCaptor<TemplateSpec> templateCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateEngine).process(templateCaptor.capture(), contextCaptor.capture());

    TemplateSpec templateSpec = templateCaptor.getValue();
    assertThat("Unexpected template.", templateSpec.getTemplate(),
        is("ltft" + File.separatorChar + "admin.html"));
    assertThat("Unexpected template selectors.", templateSpec.getTemplateSelectors(), nullValue());
    assertThat("Unexpected template mode.", templateSpec.getTemplateMode(), is(HTML));
    assertThat("Unexpected template resolution attributes.",
        templateSpec.getTemplateResolutionAttributes(), nullValue());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected locale.", context.getLocale(), is(Locale.ENGLISH));

    Set<String> variableNames = context.getVariableNames();
    assertThat("Unexpected variable count.", variableNames.size(), is(2));
    assertThat("Unexpected event value.", context.getVariable("var"), sameInstance(dto));
    assertThat("Unexpected timezone value.", context.getVariable("timezone"), is(TIMEZONE.getId()));
  }

  @Test
  void shouldReturnGeneratedLtft() throws IOException {
    LtftFormDto dto = LtftFormDto.builder().build();

    String content = "<html>test content</html>";
    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(content);

    byte[] bytes = service.generatePdf(dto, "admin");

    PDDocument pdf = Loader.loadPDF(bytes);
    String pdfText = new PDFTextStripper().getText(pdf);
    assertThat("Unexpected content.", pdfText, is("test content" + System.lineSeparator()));
  }

  @Test
  void shouldNotUploadGeneratedLtft() throws IOException {
    LtftFormDto dto = LtftFormDto.builder().build();

    when(templateEngine.process(any(TemplateSpec.class), any()))
        .thenReturn("<html>test content</html>");

    service.generatePdf(dto, "admin");

    verifyNoInteractions(s3Template);
  }

  @Test
  void shouldNotSendNotificationOfGeneratedLtft() throws IOException {
    LtftFormDto dto = LtftFormDto.builder().build();

    when(templateEngine.process(any(TemplateSpec.class), any()))
        .thenReturn("<html>test content</html>");

    service.generatePdf(dto, "admin");

    verifyNoInteractions(snsTemplate);
  }

  @Test
  void shouldReturnGeneratedFormRPartApdf() throws IOException {
    FormRPartADto dto = new FormRPartADto();

    String content = "<html>test content</html>";
    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(content);

    byte[] bytes = service.generatePdf(dto);

    PDDocument pdf = Loader.loadPDF(bytes);
    String pdfText = new PDFTextStripper().getText(pdf);
    assertThat("Unexpected content.", pdfText, is("test content" + System.lineSeparator()));
  }

  @Test
  void shouldNotUploadGeneratedFormRPartA() throws IOException {
    FormRPartADto dto = new FormRPartADto();

    when(templateEngine.process(any(TemplateSpec.class), any()))
        .thenReturn("<html>test content</html>");

    service.generatePdf(dto);

    verifyNoInteractions(s3Template);
  }

  @Test
  void shouldNotSendNotificationOfGeneratedFormRPartA() throws IOException {
    FormRPartADto dto = new FormRPartADto();

    when(templateEngine.process(any(TemplateSpec.class), any()))
        .thenReturn("<html>test content</html>");

    service.generatePdf(dto);

    verifyNoInteractions(snsTemplate);
  }

  @Test
  void shouldReturnGeneratedFormRPartBpdf() throws IOException {
    FormRPartADto dto = new FormRPartADto();

    String content = "<html>test content</html>";
    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(content);

    byte[] bytes = service.generatePdf(dto);

    PDDocument pdf = Loader.loadPDF(bytes);
    String pdfText = new PDFTextStripper().getText(pdf);
    assertThat("Unexpected content.", pdfText, is("test content" + System.lineSeparator()));
  }

  @Test
  void shouldNotUploadGeneratedFormRPartB() throws IOException {
    FormRPartBDto dto = new FormRPartBDto();

    when(templateEngine.process(any(TemplateSpec.class), any()))
        .thenReturn("<html>test content</html>");

    service.generatePdf(dto);

    verifyNoInteractions(s3Template);
  }

  @Test
  void shouldNotSendNotificationOfGeneratedFormRPartB() throws IOException {
    FormRPartBDto dto = new FormRPartBDto();

    when(templateEngine.process(any(TemplateSpec.class), any()))
        .thenReturn("<html>test content</html>");

    service.generatePdf(dto);

    verifyNoInteractions(snsTemplate);
  }

  @Test
  void shouldGenerateFormRPartAFromTemplate() throws IOException {
    FormRPartADto form = new FormRPartADto();
    FormRPartAPdfRequestDto request = new FormRPartAPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    service.generateFormRPartA(request, false);

    ArgumentCaptor<TemplateSpec> templateCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateEngine).process(templateCaptor.capture(), contextCaptor.capture());

    TemplateSpec templateSpec = templateCaptor.getValue();
    String filename = "parta.html";
    assertThat("Unexpected template.", templateSpec.getTemplate(),
        is("formr" + File.separatorChar + filename));
    assertThat("Unexpected template selectors.", templateSpec.getTemplateSelectors(), nullValue());
    assertThat("Unexpected template mode.", templateSpec.getTemplateMode(), is(HTML));
    assertThat("Unexpected template resolution attributes.",
        templateSpec.getTemplateResolutionAttributes(), nullValue());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected locale.", context.getLocale(), is(Locale.ENGLISH));

    Set<String> variableNames = context.getVariableNames();
    assertThat("Unexpected variable count.", variableNames.size(), is(2));
    assertThat("Unexpected event value.", context.getVariable("var"), sameInstance(request));
    assertThat("Unexpected timezone value.", context.getVariable("timezone"), is(TIMEZONE.getId()));
  }

  @Test
  void shouldUploadGeneratedFormRPartA() throws IOException {
    FormRPartADto form = new FormRPartADto();
    FormRPartAPdfRequestDto request = new FormRPartAPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    service.generateFormRPartA(request, false);

    String key = TRAINEE_ID + "/forms/formr_parta/" + FORM_ID + ".pdf";
    ArgumentCaptor<ByteArrayInputStream> contentCaptor = ArgumentCaptor.captor();
    verify(s3Template).upload(eq(BUCKET_NAME), eq(key), contentCaptor.capture());

    ByteArrayInputStream contentIs = contentCaptor.getValue();
    PDDocument pdf = Loader.loadPDF(new RandomAccessReadBuffer(contentIs));
    String pdfText = new PDFTextStripper().getText(pdf);

    assertThat("Unexpected content.", pdfText, is("test content" + System.lineSeparator()));
  }

  @Test
  void shouldSendNotificationOfGeneratedFormRPartAWhenPublishTrue() throws IOException {
    FormRPartADto form = new FormRPartADto();
    FormRPartAPdfRequestDto request = new FormRPartAPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    String key = TRAINEE_ID + "/forms/formr_parta/" + FORM_ID + ".pdf";
    S3Resource uploaded = S3Resource.create("s3://my-bucket/" + key, mock(S3Client.class),
        mock(S3OutputStreamProvider.class));
    when(s3Template.upload(any(), any(), any())).thenReturn(uploaded);

    service.generateFormRPartA(request, true);

    ArgumentCaptor<SnsNotification<FormRPartAPublishedEvent>> notificationCaptor =
        ArgumentCaptor.captor();
    verify(snsTemplate).sendNotification(eq(TOPIC_ARN), notificationCaptor.capture());

    SnsNotification<FormRPartAPublishedEvent> notification = notificationCaptor.getValue();
    assertThat("Unexpected group ID.", notification.getGroupId(), is(FORM_ID));

    Map<String, Object> headers = notification.getHeaders();
    assertThat("Unexpected header count.", headers.size(), is(3));
    assertThat("Unexpected header value.", headers.get("message-group-id"), is(FORM_ID));
    assertThat("Unexpected header value.", headers.get("pdf_type"), is("FORM"));
    assertThat("Unexpected header value.", headers.get("form_type"), is("FORMR_PARTA"));

    FormRPartAPublishedEvent payload = notification.getPayload();
    assertThat("Unexpected trainee ID.", payload.getTraineeId(), is(TRAINEE_ID));
    assertThat("Unexpected form ID.", payload.getId(), is(FORM_ID));
    assertThat("Unexpected form.", payload.getForm(), is(form));
    assertThat("Unexpected PDF bucket.", payload.getPdf().bucket(), is(BUCKET_NAME));
    assertThat("Unexpected PDF key.", payload.getPdf().key(), is(key));
  }

  @Test
  void shouldNotSendNotificationOfGeneratedFormRPartAWhenPublishFalse() throws IOException {
    FormRPartADto form = new FormRPartADto();
    FormRPartAPdfRequestDto request = new FormRPartAPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    service.generateFormRPartA(request, false);

    verifyNoInteractions(snsTemplate);
  }

  @Test
  void shouldReturnGeneratedFormRPartA() throws IOException {
    FormRPartADto form = new FormRPartADto();
    FormRPartAPdfRequestDto request = new FormRPartAPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    String content = "<html>test content</html>";
    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(content);

    S3Resource uploaded = mock(S3Resource.class);
    when(s3Template.upload(any(), any(), any())).thenReturn(uploaded);

    byte[] contentBytes = content.getBytes();
    when(uploaded.getContentAsByteArray()).thenReturn(contentBytes);

    Resource resource = service.generateFormRPartA(request, false);

    assertThat("Unexpected content.", resource.getContentAsByteArray(), is(contentBytes));
  }


  @Test
  void shouldGenerateFormRPartBFromTemplate() throws IOException {
    FormRPartBDto form = new FormRPartBDto();
    FormRPartBPdfRequestDto request = new FormRPartBPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    service.generateFormRPartB(request, false);

    ArgumentCaptor<TemplateSpec> templateCaptor = ArgumentCaptor.captor();
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.captor();
    verify(templateEngine).process(templateCaptor.capture(), contextCaptor.capture());

    TemplateSpec templateSpec = templateCaptor.getValue();
    String filename = "partb.html";
    assertThat("Unexpected template.", templateSpec.getTemplate(),
        is("formr" + File.separatorChar + filename));
    assertThat("Unexpected template selectors.", templateSpec.getTemplateSelectors(), nullValue());
    assertThat("Unexpected template mode.", templateSpec.getTemplateMode(), is(HTML));
    assertThat("Unexpected template resolution attributes.",
        templateSpec.getTemplateResolutionAttributes(), nullValue());

    Context context = contextCaptor.getValue();
    assertThat("Unexpected locale.", context.getLocale(), is(Locale.ENGLISH));

    Set<String> variableNames = context.getVariableNames();
    assertThat("Unexpected variable count.", variableNames.size(), is(2));
    assertThat("Unexpected event value.", context.getVariable("var"), sameInstance(request));
    assertThat("Unexpected timezone value.", context.getVariable("timezone"), is(TIMEZONE.getId()));
  }

  @Test
  void shouldUploadGeneratedFormRPartB() throws IOException {
    FormRPartBDto form = new FormRPartBDto();
    FormRPartBPdfRequestDto request = new FormRPartBPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    service.generateFormRPartB(request, false);

    String key = TRAINEE_ID + "/forms/formr_partb/" + FORM_ID + ".pdf";
    ArgumentCaptor<ByteArrayInputStream> contentCaptor = ArgumentCaptor.captor();
    verify(s3Template).upload(eq(BUCKET_NAME), eq(key), contentCaptor.capture());

    ByteArrayInputStream contentIs = contentCaptor.getValue();
    PDDocument pdf = Loader.loadPDF(new RandomAccessReadBuffer(contentIs));
    String pdfText = new PDFTextStripper().getText(pdf);

    assertThat("Unexpected content.", pdfText, is("test content" + System.lineSeparator()));
  }

  @Test
  void shouldSendNotificationOfGeneratedFormRPartBWhenPublishTrue() throws IOException {
    FormRPartBDto form = new FormRPartBDto();
    FormRPartBPdfRequestDto request = new FormRPartBPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    String key = TRAINEE_ID + "/forms/formr_partb/" + FORM_ID + ".pdf";
    S3Resource uploaded = S3Resource.create("s3://my-bucket/" + key, mock(S3Client.class),
        mock(S3OutputStreamProvider.class));
    when(s3Template.upload(any(), any(), any())).thenReturn(uploaded);

    service.generateFormRPartB(request, true);

    ArgumentCaptor<SnsNotification<FormRPartBPublishedEvent>> notificationCaptor =
        ArgumentCaptor.captor();
    verify(snsTemplate).sendNotification(eq(TOPIC_ARN), notificationCaptor.capture());

    SnsNotification<FormRPartBPublishedEvent> notification = notificationCaptor.getValue();
    assertThat("Unexpected group ID.", notification.getGroupId(), is(FORM_ID));

    Map<String, Object> headers = notification.getHeaders();
    assertThat("Unexpected header count.", headers.size(), is(3));
    assertThat("Unexpected header value.", headers.get("message-group-id"), is(FORM_ID));
    assertThat("Unexpected header value.", headers.get("pdf_type"), is("FORM"));
    assertThat("Unexpected header value.", headers.get("form_type"), is("FORMR_PARTB"));

    FormRPartBPublishedEvent payload = notification.getPayload();
    assertThat("Unexpected trainee ID.", payload.getTraineeId(), is(TRAINEE_ID));
    assertThat("Unexpected form ID.", payload.getId(), is(FORM_ID));
    assertThat("Unexpected form.", payload.getForm(), is(form));
    assertThat("Unexpected PDF bucket.", payload.getPdf().bucket(), is(BUCKET_NAME));
    assertThat("Unexpected PDF key.", payload.getPdf().key(), is(key));
  }

  @Test
  void shouldNotSendNotificationOfGeneratedFormRPartBWhenPublishFalse() throws IOException {
    FormRPartBDto form = new FormRPartBDto();
    FormRPartBPdfRequestDto request = new FormRPartBPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(
        "<html>test content</html>");

    service.generateFormRPartB(request, false);

    verifyNoInteractions(snsTemplate);
  }

  @Test
  void shouldReturnGeneratedFormRPartB() throws IOException {
    FormRPartBDto form = new FormRPartBDto();
    FormRPartBPdfRequestDto request = new FormRPartBPdfRequestDto(FORM_ID, TRAINEE_ID, form);

    String content = "<html>test content</html>";
    when(templateEngine.process(any(TemplateSpec.class), any())).thenReturn(content);

    S3Resource uploaded = mock(S3Resource.class);
    when(s3Template.upload(any(), any(), any())).thenReturn(uploaded);

    byte[] contentBytes = content.getBytes();
    when(uploaded.getContentAsByteArray()).thenReturn(contentBytes);

    Resource resource = service.generateFormRPartB(request, false);

    assertThat("Unexpected content.", resource.getContentAsByteArray(), is(contentBytes));
  }
}
