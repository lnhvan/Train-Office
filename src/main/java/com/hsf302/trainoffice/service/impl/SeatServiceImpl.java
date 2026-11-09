package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.repository.SeatRepository;
import com.hsf302.trainoffice.service.SeatService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SeatServiceImpl implements SeatService {

    private static final int SEATS_PER_PAGE = 12;

    private final SeatRepository seatRepository;

    public SeatServiceImpl(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
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
    @Transactional
    public Seat setUnderMaintenance(Long id, boolean underMaintenance) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.seatNotFoundWithId", id)));

        // Ghế đã ngừng sử dụng (toa bị giảm sức chứa) thì không còn cho đặt, không cần bảo trì.
        if (!Boolean.TRUE.equals(seat.getActive())) {
            throw new IllegalStateException(I18n.t("err.seatRetiredCannotChangeMaintenance", seat.getSeatNumber()));
        }

        seat.setUnderMaintenance(underMaintenance);
        return seatRepository.save(seat);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
