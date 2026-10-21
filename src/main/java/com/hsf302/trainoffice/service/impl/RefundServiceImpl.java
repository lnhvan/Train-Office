package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.RefundStatus;
import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Refund;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.RefundRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.service.AdminWalletService;
import com.hsf302.trainoffice.service.RefundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hsf302.trainoffice.dto.RefundRequestForm;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final TicketRepository ticketRepository;
    private final BookingRepository bookingRepository;
    private final AdminWalletService adminWalletService;

    public RefundServiceImpl(RefundRepository refundRepository,
                             TicketRepository ticketRepository,
                             BookingRepository bookingRepository,
                             AdminWalletService adminWalletService) {
        this.refundRepository = refundRepository;
        this.ticketRepository = ticketRepository;
        this.bookingRepository = bookingRepository;
        this.adminWalletService = adminWalletService;
    }

    @Override
    @Transactional
    public Refund createRefundRequest(Long ticketId, User user, RefundRequestForm form) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("Please log in to request refund.");
        }

        if (form == null) {
            throw new IllegalArgumentException("Refund information is required.");
        }

        Ticket ticket = ticketRepository.findWithDetailsByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket does not exist."));

        Booking booking = ticket.getBooking();

        if (booking == null || booking.getUser() == null) {
            throw new IllegalStateException("Guest refund is not supported.");
        }

        if (!Objects.equals(booking.getUser().getUserId(), user.getUserId())) {
            throw new IllegalStateException("Ticket does not belong to current user.");
        }

        if (booking.getBookingStatus() != BookingStatus.PAID) {
            throw new IllegalStateException("Only paid bookings can be refunded.");
        }

        if (ticket.getTicketStatus() != TicketStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed tickets can be refunded.");
        }

        if (ticket.getTrainTrip() == null) {
            throw new IllegalStateException("Ticket does not have train trip.");
        }

        if (ticket.getTrainTrip().getStatus() != TripStatus.SCHEDULED) {
            throw new IllegalStateException("Only scheduled trips can be refunded.");
        }

        if (ticket.getTrainTrip().getDepartureTime() != null
                && !ticket.getTrainTrip().getDepartureTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Cannot refund after train departure time.");
        }

        boolean duplicated = refundRepository.existsByTicket_TicketIdAndRefundStatusIn(
                ticketId,
                List.of(RefundStatus.PENDING, RefundStatus.APPROVED, RefundStatus.COMPLETED)
        );

        if (duplicated) {
            throw new IllegalStateException("Refund request already exists for this ticket.");
        }

        Refund refund = new Refund();
        refund.setRefundCode(generateRefundCode());
        refund.setTicket(ticket);
        refund.setRefundAmount(ticket.getTicketPrice());
        refund.setRefundReason(form.getReason().trim());
        refund.setCustomerName(form.getCustomerName().trim());
        refund.setCustomerEmail(form.getCustomerEmail());
        refund.setCustomerPhone(form.getCustomerPhone().trim());
        refund.setBankName(form.getBankName().trim());
        refund.setBankAccountNumber(form.getBankAccountNumber().trim());
        refund.setBankAccountHolder(form.getBankAccountHolder().trim());
        refund.setRefundStatus(RefundStatus.PENDING);

        return refundRepository.save(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Refund> getRefundById(Long refundId) {
        if (refundId == null) {
            return Optional.empty();
        }

        return refundRepository.findById(refundId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> getPendingRefunds() {
        return refundRepository.findByRefundStatusOrderByRefundIdDesc(RefundStatus.PENDING);
    }

    @Override
    @Transactional
    public void approveRefund(Long refundId, User adminUser) {
        if (adminUser == null || adminUser.getUserId() == null) {
            throw new IllegalArgumentException("Admin is required.");
        }

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request does not exist."));

        if (refund.getRefundStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException("Refund request has already been processed.");
        }

        Ticket ticket = refund.getTicket();

        if (ticket.getTicketStatus() != TicketStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed tickets can be refunded.");
        }

        refund.setRefundStatus(RefundStatus.APPROVED);
        refund.setRefundedAt(LocalDateTime.now());
        refundRepository.save(refund);

        ticket.setTicketStatus(TicketStatus.REFUNDED);
        ticketRepository.save(ticket);

        adminWalletService.subtractFromBalance(refund.getRefundAmount());

        updateBookingStatusIfAllTicketsRefunded(ticket.getBooking());
    }

    @Override
    @Transactional
    public void rejectRefund(Long refundId, User adminUser) {
        if (adminUser == null || adminUser.getUserId() == null) {
            throw new IllegalArgumentException("Admin is required.");
        }

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request does not exist."));

        if (refund.getRefundStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException("Refund request has already been processed.");
        }

        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setRefundedAt(LocalDateTime.now());

        refundRepository.save(refund);
    }

    private void updateBookingStatusIfAllTicketsRefunded(Booking booking) {
        if (booking == null || booking.getBookingId() == null) {
            return;
        }

        List<Ticket> tickets = ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(
                booking.getBookingId()
        );

        boolean allRefunded = !tickets.isEmpty()
                && tickets.stream().allMatch(ticket -> ticket.getTicketStatus() == TicketStatus.REFUNDED);

        if (allRefunded) {
            booking.setBookingStatus(BookingStatus.REFUNDED);
            bookingRepository.save(booking);
        }
    }

    private String generateRefundCode() {
        return "RF" + System.currentTimeMillis();
    }
}