package com.recruit.notify.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * SMTP 邮件发送。{@code MAIL_ENABLED=false} 时只打印日志，不真正连 SMTP。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailSenderService {

    private final JavaMailSender mailSender;

    @Value("${recruit.notify.mail-enabled:true}")
    private boolean mailEnabled;

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人
     * @param subject 标题
     * @param content 正文
     * @return 投递结果
     */
    public DeliveryOutcome send(String to, String subject, String content) {
        long start = System.nanoTime();
        if (to == null || to.isBlank()) {
            return DeliveryOutcome.fail("SMTP", "收件人为空", start);
        }
        if (!mailEnabled) {
            log.info("[MAIL_MOCK] to={}, subject={}", mask(to), subject);
            return DeliveryOutcome.ok("MAIL_MOCK", UUID.randomUUID().toString().replace("-", ""), start);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject == null ? "" : subject);
            helper.setText(content == null ? "" : content, false);
            String requestId = UUID.randomUUID().toString().replace("-", "");
            message.setHeader("X-Request-Id", requestId);
            if (mailSender instanceof JavaMailSenderImpl impl
                    && impl.getUsername() != null && !impl.getUsername().isBlank()) {
                helper.setFrom(impl.getUsername());
            }
            mailSender.send(message);
            return DeliveryOutcome.ok("SMTP", requestId, start);
        } catch (Exception e) {
            log.warn("SMTP 发送失败: to={}, error={}", mask(to), e.getMessage());
            return DeliveryOutcome.fail("SMTP", truncate(e.getMessage(), 512), start);
        }
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
