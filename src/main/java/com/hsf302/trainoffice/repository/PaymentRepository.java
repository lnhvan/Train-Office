package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByPaymentStatus(PaymentStatus paymentStatus);

    List<Payment> findByBooking_BookingId(Long bookingId);

    Optional<Payment> findByTransactionCode(String transactionCode);
}