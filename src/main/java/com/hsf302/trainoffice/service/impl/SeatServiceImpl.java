package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.repository.SeatRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.service.SeatService;
import com.hsf302.trainoffice.exception.ResourceInUseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Service
public class SeatServiceImpl implements SeatService {

    private static final Logger log = LoggerFactory.getLogger(SeatServiceImpl.class);

    private static final int SEATS_PER_PAGE = 12;

    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;

    public SeatServiceImpl(SeatRepository seatRepository,
                           TicketRepository ticketRepository) {
        this.seatRepository = seatRepository;
        this.ticketRepository = ticketRepository;
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
            throw new IllegalStateException("Coach is required for seat");
        }

        if (seat.getSeatNumber() == null || seat.getSeatNumber().isBlank()) {
            throw new IllegalStateException("Seat number is required");
        }

        Long coachId = seat.getCoach().getCoachId();
        String seatNumber = seat.getSeatNumber();

        if (seat.getSeatId() == null) {
            if (seatRepository.existsByCoach_CoachIdAndSeatNumber(coachId, seatNumber)) {
                throw new IllegalStateException("Seat number already exists in this coach");
            }
        } else {
            Optional<Seat> existing = seatRepository.findById(seat.getSeatId());

            if (existing.isPresent()) {
                Seat oldSeat = existing.get();

                boolean changedCoach = !oldSeat.getCoach().getCoachId().equals(coachId);
                boolean changedNumber = !oldSeat.getSeatNumber().equals(seatNumber);

                if ((changedCoach || changedNumber)
                        && seatRepository.existsByCoach_CoachIdAndSeatNumber(coachId, seatNumber)) {
                    throw new IllegalStateException("Seat number already exists in this coach");
                }
            }
        }

        return seatRepository.save(seat);
    }

    @Override
    @Transactional
    public void deleteSeat(Long id) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Seat not found with ID: " + id));

        if (ticketRepository.existsBySeat_SeatId(id)) {
            throw new ResourceInUseException(
                    "Cannot delete this seat because it is already used by one or more tickets."
            );
        }

        try {
            seatRepository.delete(seat);
            seatRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            log.warn("Seat {} could not be deleted because a dependent record was added or still exists.", id, ex);
            throw new ResourceInUseException(
                    "Cannot delete this seat because it is still in use.",
                    ex
            );
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
