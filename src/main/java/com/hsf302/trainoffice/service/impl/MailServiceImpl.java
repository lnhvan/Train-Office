package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String username;
    private final String from;

    public MailServiceImpl(ObjectProvider<JavaMailSender> mailSender,
                           @Value("${spring.mail.username:}") String username,
                           @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.username = username;
        this.from = from;
    }

    @Override
    public boolean send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null || username == null || username.isBlank()) {
            log.warn("""

                    ===== [DEV] Chưa cấu hình SMTP (MAIL_USERNAME / MAIL_PASSWORD) - email không được gửi =====
                    Đến:     {}
                    Tiêu đề: {}
                    {}
                    ==========================================================================================""",
                    to, subject, body);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from == null || from.isBlank() ? username : from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            sender.send(message);
            return true;
        } catch (MailException ex) {
            log.error("Gửi email tới {} thất bại: {}", to, ex.getMessage());
            throw ex;
        }
    }
}
