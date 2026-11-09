package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.common.enums.RefundStatus;
import com.hsf302.trainoffice.entity.Refund;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    long countByRefundStatus(RefundStatus refundStatus);

    boolean existsByTicket_TicketIdAndRefundStatusIn(Long ticketId,
                                                     Collection<RefundStatus> statuses);

    @EntityGraph(attributePaths = {
            "ticket",
            "ticket.passenger",
            "ticket.booking",
            "ticket.booking.user",
            "ticket.trainTrip",
            "ticket.trainTrip.train",
            "ticket.seat",
            "ticket.seat.coach"
    })
    List<Refund> findByRefundStatusOrderByRefundIdDesc(RefundStatus refundStatus);

    /** Yêu cầu cần admin xử lý: chờ duyệt và đã duyệt nhưng chưa chuyển tiền. */
    @EntityGraph(attributePaths = {
            "ticket",
            "ticket.passenger",
            "ticket.booking",
            "ticket.booking.user",
            "ticket.trainTrip",
            "ticket.trainTrip.train",
            "ticket.seat",
            "ticket.seat.coach"
    })
    List<Refund> findByRefundStatusInOrderByRefundIdDesc(Collection<RefundStatus> refundStatuses);

    /** Yêu cầu hoàn tiền của các vé thuộc một chuyến, theo trạng thái. */
    @Query("""
            select r
            from Refund r
            where r.ticket.booking.trainTrip.tripId = :tripId
              and r.refundStatus in :statuses
            order by r.refundId
            """)
    List<Refund> findByTripAndStatuses(@Param("tripId") Long tripId,
                                       @Param("statuses") Collection<RefundStatus> statuses);

    /** Yêu cầu đã duyệt theo luồng cũ (duyệt = trừ ví ngay, chưa có processed_at). */
    List<Refund> findByRefundStatusAndProcessedAtIsNull(RefundStatus refundStatus);

    /** Tổng tiền hoàn của các yêu cầu có trạng thái trong {@code statuses}, chuyển tiền trong [from, to). */
    @Query("""
            select coalesce(sum(r.refundAmount), 0)
            from Refund r
            where r.refundStatus in :statuses
              and r.refundedAt >= :from
              and r.refundedAt < :to
            """)
    BigDecimal sumRefundAmountBetween(@Param("statuses") Collection<RefundStatus> statuses,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);
}
