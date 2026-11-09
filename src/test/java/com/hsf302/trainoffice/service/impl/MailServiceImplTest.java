package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.PaymentMethod;
import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceImplTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private ObjectProvider<ITemplateEngine> templateEngineProvider;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ITemplateEngine templateEngine;

    private MailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        // Dev mode without username
        mailService = new MailServiceImpl(
                mailSenderProvider,
                templateEngineProvider,
                "",
                ""
        );
    }

    @Test
    void send_WhenInDevMode_ReturnsFalseAndLogs() {
        boolean result = mailService.send("user@test.com", "Subject", "Body");
        assertFalse(result, "Dev mode without SMTP should log and return false");
    }

    @Test
    void sendHtml_WhenInDevMode_ReturnsFalseAndLogs() {
        boolean result = mailService.sendHtml("user@test.com", "Subject", "<h1>Hello</h1>");
        assertFalse(result, "Dev mode without SMTP should log and return false");
    }

    @Test
    void sendPasswordResetOtp_RendersTemplateAndCallsSendHtml() {
        when(templateEngineProvider.getIfAvailable()).thenReturn(templateEngine);
        when(templateEngine.process(eq("mail/password-reset"), any(IContext.class))).thenReturn("<html>OTP: 123456</html>");

        boolean result = mailService.sendPasswordResetOtp("user@test.com", "Nguyen Van A", "123456", 5);

        assertFalse(result); // In dev mode without SMTP, returns false
        verify(templateEngine).process(eq("mail/password-reset"), any(IContext.class));
    }

    @Test
    void sendPaymentSuccessEmail_RendersTemplateAndCallsSendHtml() {
        when(templateEngineProvider.getIfAvailable()).thenReturn(templateEngine);
        when(templateEngine.process(eq("mail/payment-success"), any(IContext.class))).thenReturn("<html>Payment Success</html>");

        Station dep = new Station();
        dep.setStationName("Hà Nội");

        Station arr = new Station();
        arr.setStationName("Sài Gòn");

        Train train = new Train();
        train.setTrainCode("SE1");
        train.setTrainName("Tàu Thống Nhất");

        TrainTrip trip = new TrainTrip();
        trip.setTrain(train);
        trip.setDepartureTime(LocalDateTime.now().plusDays(1));
        trip.setArrivalTime(LocalDateTime.now().plusDays(2));

        Booking booking = new Booking();
        booking.setBookingNumber("RJ99999");
        booking.setBookerName("Tran Van B");
        booking.setBookerEmail("tranvanb@test.com");
        booking.setTotalAmount(new BigDecimal("1500000"));
        booking.setBookingStatus(BookingStatus.PAID);
        booking.setDepartureStation(dep);
        booking.setArrivalStation(arr);
        booking.setTrainTrip(trip);

        Payment payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("1500000"));
        payment.setTransactionCode("VNP123456");
        payment.setPaidAt(LocalDateTime.now());

        Coach coach = new Coach();
        coach.setCoachNumber("1");

        Seat seat = new Seat();
        seat.setSeatNumber("01");
        seat.setCoach(coach);

        Passenger passenger = new Passenger();
        passenger.setFullName("Tran Van B");

        Ticket ticket = new Ticket();
        ticket.setTicketCode("TK8888");
        ticket.setTicketPrice(new BigDecimal("1500000"));
        ticket.setTicketStatus(TicketStatus.CONFIRMED);
        ticket.setPassenger(passenger);
        ticket.setSeat(seat);

        boolean result = mailService.sendPaymentSuccessEmail(booking, payment, List.of(ticket));

        assertFalse(result); // Dev mode returns false gracefully
        verify(templateEngine).process(eq("mail/payment-success"), any(IContext.class));
    }
}
