package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.AffectedTicketDto;
import com.hsf302.trainoffice.dto.SeatMaintenanceCheckResult;
import com.hsf302.trainoffice.dto.SeatMaintenanceResult;
import com.hsf302.trainoffice.dto.TripCancellationResult;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.event.SeatHoldReleaseEvent;
import com.hsf302.trainoffice.repository.SeatRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.service.RefundService;
import com.hsf302.trainoffice.service.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class SeatServiceImpl implements SeatService {

    private static final int SEATS_PER_PAGE = 12;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");

    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final RefundService refundService;
    private final ApplicationEventPublisher eventPublisher;

    public SeatServiceImpl(SeatRepository seatRepository) {
        this(seatRepository, null, null, null);
    }

    @Autowired
    public SeatServiceImpl(SeatRepository seatRepository,
                           @Autowired(required = false) TicketRepository ticketRepository,
                           @Autowired(required = false) RefundService refundService,
                           @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.seatRepository = seatRepository;
        this.ticketRepository = ticketRepository;
        this.refundService = refundService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<Seat> getAllSeats() {
        return seatRepository.findAll(Sort.by("seatNumber").ascending());
    }

    @Override
    public Page<Seat> listAll(int pageNumber, String keyword, Long trainId, Long coachId) {
        int safePageNumber = Math.max(pageNumber, 1);

        Pageable pageable = PageRequest.of(
                safePageNumber - 1,
                SEATS_PER_PAGE
        );

        return seatRepository.searchSeats(normalizeKeyword(keyword), trainId, coachId, pageable);
    }

    @Override
    public Optional<Seat> getSeatById(Long id) {
        return seatRepository.findById(id);
    }

    @Override
    public Seat saveSeat(Seat seat) {
        if (seat.getCoach() == null || seat.getCoach().getCoachId() == null) {
            throw new IllegalStateException(I18n.t("err.coachIsRequiredForSeat"));
        }

        if (seat.getSeatNumber() == null || seat.getSeatNumber().isBlank()) {
            throw new IllegalStateException(I18n.t("err.seatNumberIsRequired"));
        }

        Long coachId = seat.getCoach().getCoachId();
        String seatNumber = seat.getSeatNumber();

        if (seat.getSeatId() == null) {
            if (seatRepository.existsByCoach_CoachIdAndSeatNumber(coachId, seatNumber)) {
                throw new IllegalStateException(I18n.t("err.seatNumberAlreadyExistsInThisCoach"));
            }
        } else {
            Optional<Seat> existing = seatRepository.findById(seat.getSeatId());

            if (existing.isPresent()) {
                Seat oldSeat = existing.get();

                boolean changedCoach = !oldSeat.getCoach().getCoachId().equals(coachId);
                boolean changedNumber = !oldSeat.getSeatNumber().equals(seatNumber);

                if ((changedCoach || changedNumber)
                        && seatRepository.existsByCoach_CoachIdAndSeatNumber(coachId, seatNumber)) {
                    throw new IllegalStateException(I18n.t("err.seatNumberAlreadyExistsInThisCoach"));
                }
            }
        }

        return seatRepository.save(seat);
    }

    @Override
    @Transactional(readOnly = true)
    public SeatMaintenanceCheckResult checkFutureTickets(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.seatNotFoundWithId", seatId)));

        List<Ticket> activeTickets = (ticketRepository != null)
                ? ticketRepository.findFutureActiveTicketsBySeat(seatId, LocalDateTime.now(), List.of(TicketStatus.CONFIRMED, TicketStatus.BOOKED))
                : Collections.emptyList();

        List<AffectedTicketDto> dtos = new ArrayList<>();
        BigDecimal totalRefund = BigDecimal.ZERO;
        int confirmedCount = 0;
        int bookedCount = 0;

        for (Ticket ticket : activeTickets) {
            Booking booking = ticket.getBooking();
            TrainTrip trip = ticket.getTrainTrip();
            Train train = (trip != null) ? trip.getTrain() : null;

            BigDecimal refund = (ticket.getTicketPrice() != null) ? ticket.getTicketPrice() : BigDecimal.ZERO;

            if (ticket.getTicketStatus() == TicketStatus.CONFIRMED) {
                confirmedCount++;
                totalRefund = totalRefund.add(refund);
            } else if (ticket.getTicketStatus() == TicketStatus.BOOKED) {
                bookedCount++;
            }

            String depStation = "";
            String arrStation = "";
            if (booking != null) {
                if (booking.getDepartureStation() != null) {
                    depStation = booking.getDepartureStation().getStationName();
                }
                if (booking.getArrivalStation() != null) {
                    arrStation = booking.getArrivalStation().getStationName();
                }
            }

            LocalDateTime depTime = (trip != null) ? trip.getDepartureTime() : null;
            String depTimeFormatted = (depTime != null) ? depTime.format(DATE_TIME_FORMATTER) : "--:--";

            dtos.add(AffectedTicketDto.builder()
                    .ticketId(ticket.getTicketId())
                    .ticketCode(ticket.getTicketCode())
                    .bookingId(booking != null ? booking.getBookingId() : null)
                    .bookingNumber(booking != null ? booking.getBookingNumber() : null)
                    .customerName(booking != null ? booking.getBookerName() : "")
                    .customerEmail(booking != null ? booking.getBookerEmail() : "")
                    .customerPhone(booking != null ? booking.getBookerPhone() : "")
                    .passengerName(ticket.getPassenger() != null ? ticket.getPassenger().getFullName() : "")
                    .trainCode(train != null ? train.getTrainCode() : "")
                    .routeName(trip != null && trip.getRoute() != null ? trip.getRoute().getRouteName() : "")
                    .departureStationName(depStation)
                    .arrivalStationName(arrStation)
                    .departureTime(depTime)
                    .departureTimeFormatted(depTimeFormatted)
                    .refundAmount(refund)
                    .status(ticket.getTicketStatus().name())
                    .build());
        }

        return SeatMaintenanceCheckResult.builder()
                .seatId(seat.getSeatId())
                .seatNumber(seat.getSeatNumber())
                .coachNumber(seat.getCoach() != null ? String.valueOf(seat.getCoach().getCoachNumber()) : "")
                .trainCode(seat.getCoach() != null && seat.getCoach().getTrain() != null ? seat.getCoach().getTrain().getTrainCode() : "")
                .totalTickets(dtos.size())
                .confirmedCount(confirmedCount)
                .bookedCount(bookedCount)
                .totalRefundAmount(totalRefund)
                .tickets(dtos)
                .build();
    }

    @Override
    @Transactional
    public SeatMaintenanceResult updateMaintenance(Long seatId, boolean underMaintenance, boolean confirmRefund) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.seatNotFoundWithId", seatId)));

        // Ghế đã ngừng sử dụng (toa bị giảm sức chứa) thì không còn cho đặt, không cần bảo trì.
        if (!Boolean.TRUE.equals(seat.getActive())) {
            throw new IllegalStateException(I18n.t("err.seatRetiredCannotChangeMaintenance", seat.getSeatNumber()));
        }

        // Tắt bảo trì: không ảnh hưởng tới vé đã đặt trong tương lai
        if (!underMaintenance) {
            seat.setUnderMaintenance(false);
            Seat saved = seatRepository.save(seat);
            return SeatMaintenanceResult.builder()
                    .seat(saved)
                    .underMaintenance(false)
                    .refundedTicketsCount(0)
                    .refundedAmount(BigDecimal.ZERO)
                    .cancelledHoldsCount(0)
                    .build();
        }

        // Bật bảo trì: kiểm tra vé đặt trong tương lai
        List<Ticket> activeTickets = (ticketRepository != null)
                ? ticketRepository.findFutureActiveTicketsBySeat(seatId, LocalDateTime.now(), List.of(TicketStatus.CONFIRMED, TicketStatus.BOOKED))
                : Collections.emptyList();

        if (!activeTickets.isEmpty() && !confirmRefund) {
            throw new IllegalStateException(I18n.t("err.seatHasActiveFutureTicketsConfirmationRequired", seat.getSeatNumber(), activeTickets.size()));
        }

        int refundedCount = 0;
        BigDecimal refundedAmount = BigDecimal.ZERO;
        int cancelledHoldsCount = 0;

        if (!activeTickets.isEmpty() && refundService != null) {
            TripCancellationResult cancellationResult = refundService.refundTicketsForSeatMaintenance(seat, activeTickets);
            refundedCount = cancellationResult.refundedTickets();
            refundedAmount = cancellationResult.refundedAmount();
            cancelledHoldsCount = cancellationResult.cancelledHolds();

            if (eventPublisher != null) {
                List<Long> bookingIds = activeTickets.stream()
                        .map(Ticket::getBooking)
                        .filter(b -> b != null && b.getBookingId() != null)
                        .map(Booking::getBookingId)
                        .distinct()
                        .toList();
                if (!bookingIds.isEmpty()) {
                    eventPublisher.publishEvent(SeatHoldReleaseEvent.bookings(bookingIds));
                }
            }
        }

        seat.setUnderMaintenance(true);
        Seat saved = seatRepository.save(seat);

        return SeatMaintenanceResult.builder()
                .seat(saved)
                .underMaintenance(true)
                .refundedTicketsCount(refundedCount)
                .refundedAmount(refundedAmount)
                .cancelledHoldsCount(cancelledHoldsCount)
                .build();
    }

    @Override
    @Transactional
    public Seat setUnderMaintenance(Long id, boolean underMaintenance) {
        return updateMaintenance(id, underMaintenance, true).getSeat();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
