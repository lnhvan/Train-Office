package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.SeatRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

@Service
public class TicketServiceImpl implements TicketService {
    private static final List<TicketStatus> ACTIVE_TICKET_STATUSES = List.of(
            TicketStatus.BOOKED,
            TicketStatus.CONFIRMED
    );

    private final TicketRepository ticketRepository;
    private final SeatRepository seatRepository;
    private final TrainTripRepository trainTripRepository;

    public TicketServiceImpl(TicketRepository ticketRepository,
                             SeatRepository seatRepository,
                             TrainTripRepository trainTripRepository) {
        this.ticketRepository = ticketRepository;
        this.seatRepository = seatRepository;
        this.trainTripRepository = trainTripRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSeatAvailable(Long seatId, Long tripId, Integer departureOrder, Integer arrivalOrder) {
        return !ticketRepository.existsActiveOverlappingSeatBooking(
                seatId,
                tripId,
                departureOrder,
                arrivalOrder,
                ACTIVE_TICKET_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findBlockedSeatIds(Long tripId, Integer departureOrder, Integer arrivalOrder) {
        // Legacy ACTIVE/CHECKED_IN/EXPIRED statuses are intentionally not included for new booking holds.
        return new HashSet<>(ticketRepository.findBlockedSeatIds(
                tripId,
                departureOrder,
                arrivalOrder,
                ACTIVE_TICKET_STATUSES));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Seat> getSeatsForTrip(Long tripId) {
        TrainTrip trip = trainTripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Train trip does not exist"));
        return seatRepository.findActiveSeatsByTrainId(trip.getTrain().getTrainId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByBookingId(Long bookingId) {
        return ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> getTicketById(Long ticketId) {
        return ticketRepository.findById(ticketId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsForUser(User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User is required");
        }
        return ticketRepository.findByBooking_User_UserIdOrderByTicketIdAsc(user.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public Ticket getTicketDetails(Long ticketId) {
        return ticketRepository.findWithDetailsByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket does not exist"));
    }
}
