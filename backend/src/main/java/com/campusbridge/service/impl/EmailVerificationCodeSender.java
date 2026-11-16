package com.campusbridge.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailVerificationCodeSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationCodeSender.class);

    private final JavaMailSender mailSender;

    @Value("${campusbridge.verification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${campusbridge.verification.email.debug-code-enabled:false}")
    private boolean debugCodeEnabled;

    @Value("${campusbridge.verification.email.from:${spring.mail.username:}}")
    private String fromAddress;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public EmailVerificationCodeSender(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public boolean sendCode(String toAddress, String code, LocalDateTime expiresAt) {
        if (!isDeliveryEnabled()) {
            LOGGER.info("Email verification delivery is not configured, code generated for {}", toAddress);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toAddress);
        message.setFrom(fromAddress);
        message.setSubject("CampusBridge 校园邮箱认证验证码");
        message.setText(buildMessage(code, expiresAt));

        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            LOGGER.warn("Failed to send verification email to {}", toAddress, e);
            return false;
        }
    }

    public boolean isDebugCodeEnabled() {
        return debugCodeEnabled;
    }

    public boolean isDeliveryRequired() {
        return emailEnabled || StringUtils.hasText(mailHost);
    }

    private boolean isDeliveryEnabled() {
        return mailSender != null
                && StringUtils.hasText(fromAddress)
                && (emailEnabled || StringUtils.hasText(mailHost));
    }

    private String buildMessage(String code, LocalDateTime expiresAt) {
        String formattedExpiresAt = expiresAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return "你的 CampusBridge 校园邮箱认证验证码是：" + code
                + "\n\n验证码有效期至 " + formattedExpiresAt
                + "，请在个人主页认证中心输入验证码完成认证。";
    }
}
