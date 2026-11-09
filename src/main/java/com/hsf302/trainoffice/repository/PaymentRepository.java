package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByPaymentStatus(PaymentStatus paymentStatus);

    List<Payment> findByBooking_BookingId(Long bookingId);

    Optional<Payment> findByTransactionCode(String transactionCode);

    /** Tổng tiền các thanh toán có trạng thái {@code status} và thời điểm trả trong [from, to). */
    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.paymentStatus = :status
              and p.paidAt >= :from
              and p.paidAt < :to
            """)
    BigDecimal sumAmountPaidBetween(@Param("status") PaymentStatus status,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}