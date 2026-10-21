package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentService {

    // =========================================================
    // ADMIN PAYMENT MANAGEMENT
    // =========================================================

    List<Payment> getAllPayments();

    List<Payment> getPaymentsByStatus(PaymentStatus status);

    List<Payment> getPaymentsByBookingId(Long bookingId);

    Optional<Payment> getPaymentById(Long id);

    Payment savePayment(Payment payment);

    Payment markPaymentSuccess(Long id);

    Payment markPaymentFailed(Long id);

    void deletePayment(Long id);

    // =========================================================
    // CUSTOMER VNPAY CHECKOUT
    // =========================================================

    Booking getBookingForPayment(Long bookingId);

    String createVnpayPaymentUrl(Long bookingId, String clientIp);

    Payment handleVnpayReturn(Map<String, String> vnpayParams);
}