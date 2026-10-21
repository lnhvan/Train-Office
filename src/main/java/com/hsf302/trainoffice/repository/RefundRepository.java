package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.common.enums.RefundStatus;
import com.hsf302.trainoffice.entity.Refund;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}