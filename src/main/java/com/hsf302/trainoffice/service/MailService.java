package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;
import com.hsf302.trainoffice.entity.Ticket;

import java.util.List;

public interface MailService {

    /**
     * Gửi email dạng chữ thường.
     * Chưa cấu hình SMTP (MAIL_USERNAME trống) thì chỉ ghi nội dung ra console để chạy thử.
     *
     * @return true nếu đã gửi qua SMTP, false nếu chỉ ghi log
     */
    boolean send(String to, String subject, String body);

    /**
     * Gửi email định dạng HTML UTF-8.
     *
     * @return true nếu gửi thành công, false nếu chỉ ghi log DEV hoặc lỗi
     */
    boolean sendHtml(String to, String subject, String htmlBody);

    /**
     * Gửi email mã xác nhận OTP đặt lại mật khẩu với giao diện RailJet HTML (TTL 5 phút).
     */
    boolean sendPasswordResetOtp(String to, String recipientName, String code, int ttlMinutes);

    /**
     * Gửi email xác nhận thanh toán thành công và vé điện tử hành trình RailJet.
     */
    boolean sendPaymentSuccessEmail(Booking booking, Payment payment, List<Ticket> tickets);

}
