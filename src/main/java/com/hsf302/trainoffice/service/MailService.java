package com.hsf302.trainoffice.service;

public interface MailService {

    /**
     * Gửi email dạng chữ thường.
     * Chưa cấu hình SMTP (MAIL_USERNAME trống) thì chỉ ghi nội dung ra console để chạy thử.
     *
     * @return true nếu đã gửi qua SMTP, false nếu chỉ ghi log
     */
    boolean send(String to, String subject, String body);
}
