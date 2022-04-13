/*
 * package com.htc.postprocessing.util;
 * 
 * import java.nio.charset.StandardCharsets; import java.util.HashMap; import
 * java.util.Map;
 * 
 * import javax.mail.internet.MimeMessage;
 * 
 * import org.springframework.beans.factory.annotation.Autowired; import
 * org.springframework.core.io.ClassPathResource; import
 * org.springframework.mail.javamail.JavaMailSender; import
 * org.springframework.mail.javamail.MimeMessageHelper; import
 * org.springframework.stereotype.Component; import
 * org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
 * 
 * import com.htc.postprocessing.email.api.dto.MailRequest; import
 * com.htc.postprocessing.email.api.dto.MailResponse;
 * 
 * import freemarker.template.Configuration; import
 * freemarker.template.Template;
 * 
 * @Component public class MailUtility {
 * 
 * @Autowired private JavaMailSender sender;
 * 
 * @Autowired private Configuration config;
 * 
 * public MailResponse sendEmailPostProcessing(MailRequest request) {
 * Map<String, String> model = new HashMap<>(); model.put("name",
 * request.getName()); return sendEmail(request, model); }
 * 
 * public MailResponse sendEmail(MailRequest request, Map<String, String> model)
 * { MailResponse response = new MailResponse(); MimeMessage message =
 * sender.createMimeMessage(); try { // set mediaType MimeMessageHelper helper =
 * new MimeMessageHelper(message,
 * MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
 * StandardCharsets.UTF_8.name()); // add attachment
 * helper.addAttachment("logo.png", new ClassPathResource("logo.png")); Template
 * t = config.getTemplate("email-template.ftl"); String html =
 * FreeMarkerTemplateUtils.processTemplateIntoString(t, model);
 * helper.setTo(request.getTo()); helper.setText(html, true);
 * helper.setSubject(request.getSubject()); helper.setFrom(request.getFrom());
 * sender.send(message); response.setMessage("mail send to : " +
 * request.getTo()); response.setStatus(Boolean.TRUE); } catch (Exception
 * exception) { response.setMessage("Mail Sending failure : " +
 * exception.getMessage()); response.setStatus(Boolean.FALSE); } return
 * response; } }
 */