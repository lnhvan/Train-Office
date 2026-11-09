package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.service.MailService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");
    private static final DateTimeFormatter PAID_AT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ObjectProvider<ITemplateEngine> templateEngineProvider;
    private final String username;
    private final String from;

    public MailServiceImpl(ObjectProvider<JavaMailSender> mailSender,
                           ObjectProvider<ITemplateEngine> templateEngineProvider,
                           @Value("${spring.mail.username:}") String username,
                           @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.templateEngineProvider = templateEngineProvider;
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

    @Override
    public boolean sendHtml(String to, String subject, String htmlBody) {
        JavaMailSender sender = mailSender.getIfAvailable();

        if (sender == null || username == null || username.isBlank()) {
            log.warn("""

                    ===== [DEV] Chưa cấu hình SMTP (MAIL_USERNAME / MAIL_PASSWORD) - Email HTML không được gửi =====
                    Đến:     {}
                    Tiêu đề: {}
                    [HTML Content length: {} chars]
                    ===============================================================================================""",
                    to, subject, htmlBody != null ? htmlBody.length() : 0);
            return false;
        }

        try {
            MimeMessage mimeMessage = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from == null || from.isBlank() ? username : from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            ClassPathResource logo = new ClassPathResource("static/images/railjet-logo-transparent.png");
            if (!logo.exists()) {
                logo = new ClassPathResource("static/images/railjet-logo.png");
            }
            if (logo.exists()) {
                helper.addInline("railjetLogo", logo, "image/png");
            }

            sender.send(mimeMessage);
            log.info("Đã gửi email HTML thành công tới: {}", to);
            return true;
        } catch (Exception ex) {
            log.error("Gửi email HTML tới {} thất bại: {}", to, ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean sendPasswordResetOtp(String to, String recipientName, String code, int ttlMinutes) {
        ITemplateEngine engine = templateEngineProvider.getIfAvailable();
        String subject = "RailJet - Mã đặt lại mật khẩu";

        if (engine != null) {
            try {
                Context context = new Context();
                context.setVariable("recipientName", recipientName);
                context.setVariable("code", code);
                context.setVariable("ttlMinutes", ttlMinutes);

                String html = engine.process("mail/password-reset", context);
                return sendHtml(to, subject, html);
            } catch (Exception ex) {
                log.error("Lỗi khi render HTML mail password-reset: {}, chuyển sang gửi text thuần", ex.getMessage());
            }
        }

        String plainText = String.format("Xin chào %s,\n\nMã đặt lại mật khẩu RailJet của bạn là: %s\n\n"
                        + "Mã có hiệu lực trong %d phút và chỉ dùng được một lần.\n"
                        + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.\n\nRailJet",
                recipientName != null ? recipientName : to, code, ttlMinutes);
        return send(to, subject, plainText);
    }

    @Override
    public boolean sendPaymentSuccessEmail(Booking booking, Payment payment, List<Ticket> tickets) {
        if (booking == null) {
            return false;
        }

        String recipient = booking.getBookerEmail();
        if (recipient == null || recipient.isBlank()) {
            if (booking.getUser() != null) {
                recipient = booking.getUser().getEmail();
            }
        }

        if (recipient == null || recipient.isBlank()) {
            log.warn("Không tìm thấy email người nhận cho booking #{}", booking.getBookingNumber());
            return false;
        }

        String subject = "RailJet - Xác nhận vé điện tử & Thanh toán thành công #" + booking.getBookingNumber();
        ITemplateEngine engine = templateEngineProvider.getIfAvailable();

        if (engine != null) {
            try {
                Context context = new Context();
                context.setVariable("booking", booking);
                context.setVariable("payment", payment);

                LocalDateTime paidAt = payment != null && payment.getPaidAt() != null
                        ? payment.getPaidAt()
                        : LocalDateTime.now();
                context.setVariable("formattedPaidAt", PAID_AT_FORMATTER.format(paidAt));

                if (booking.getDepartureStation() != null) {
                    context.setVariable("departureStationName", booking.getDepartureStation().getStationName());
                } else {
                    context.setVariable("departureStationName", "Ga đi");
                }

                if (booking.getArrivalStation() != null) {
                    context.setVariable("arrivalStationName", booking.getArrivalStation().getStationName());
                } else {
                    context.setVariable("arrivalStationName", "Ga đến");
                }

                if (booking.getTrainTrip() != null) {
                    if (booking.getTrainTrip().getTrain() != null) {
                        context.setVariable("trainCode", booking.getTrainTrip().getTrain().getTrainCode());
                        context.setVariable("trainName", booking.getTrainTrip().getTrain().getTrainName());
                    } else {
                        context.setVariable("trainCode", "Tàu RailJet");
                        context.setVariable("trainName", "");
                    }

                    if (booking.getTrainTrip().getDepartureTime() != null) {
                        context.setVariable("departureTimeFormatted", DATE_TIME_FORMATTER.format(booking.getDepartureTime() != null ? booking.getDepartureTime() : booking.getTrainTrip().getDepartureTime()));
                    } else {
                        context.setVariable("departureTimeFormatted", "--:--");
                    }

                    if (booking.getTrainTrip().getArrivalTime() != null) {
                        context.setVariable("arrivalTimeFormatted", DATE_TIME_FORMATTER.format(booking.getArrivalTime() != null ? booking.getArrivalTime() : booking.getTrainTrip().getArrivalTime()));
                    } else {
                        context.setVariable("arrivalTimeFormatted", "--:--");
                    }
                } else {
                    context.setVariable("trainCode", "Tàu RailJet");
                    context.setVariable("trainName", "");
                    context.setVariable("departureTimeFormatted", "--:--");
                    context.setVariable("arrivalTimeFormatted", "--:--");
                }

                long totalAmount = booking.getTotalAmount() != null ? booking.getTotalAmount().longValue() : 0L;
                context.setVariable("totalAmountFormatted", String.format("%,d đ", totalAmount));

                List<TicketEmailDto> ticketDtos = new ArrayList<>();
                if (tickets != null) {
                    for (Ticket t : tickets) {
                        String passengerName = t.getPassenger() != null ? t.getPassenger().getFullName() : "Hành khách";
                        String coachNumber = t.getSeat() != null && t.getSeat().getCoach() != null
                                ? String.valueOf(t.getSeat().getCoach().getCoachNumber())
                                : "-";
                        String seatNumber = t.getSeat() != null ? t.getSeat().getSeatNumber() : "-";
                        String priceFormatted = t.getTicketPrice() != null ? String.format("%,d đ", t.getTicketPrice().longValue()) : "0 đ";

                        ticketDtos.add(new TicketEmailDto(
                                t.getTicketCode(),
                                passengerName,
                                coachNumber,
                                seatNumber,
                                priceFormatted
                        ));
                    }
                }
                context.setVariable("ticketDtos", ticketDtos);

                String html = engine.process("mail/payment-success", context);
                return sendHtml(recipient, subject, html);

            } catch (Exception ex) {
                log.error("Lỗi khi tạo template email thanh toán: {}", ex.getMessage(), ex);
            }
        }

        String body = String.format("Xin chào %s,\n\nBạn đã thanh toán thành công đơn đặt vé #%s trên RailJet.\n"
                        + "Tổng tiền: %,d VNĐ.\nCảm ơn bạn đã lựa chọn dịch vụ của RailJet!",
                booking.getBookerName() != null ? booking.getBookerName() : recipient,
                booking.getBookingNumber(),
                booking.getTotalAmount() != null ? booking.getTotalAmount().longValue() : 0L);
        return send(recipient, subject, body);
    }

    public record TicketEmailDto(
            String ticketCode,
            String passengerName,
            String coachNumber,
            String seatNumber,
            String priceFormatted
    ) {}
}
