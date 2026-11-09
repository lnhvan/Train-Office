package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Seat;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface SeatService {
    List<Seat> getAllSeats();

    Page<Seat> listAll(int pageNumber, String keyword, Long trainId, Long coachId);

    Optional<Seat> getSeatById(Long id);

    Seat saveSeat(Seat seat);

    /** Đánh dấu / bỏ đánh dấu ghế đang bảo trì. Ghế bảo trì không cho khách đặt. */
    Seat setUnderMaintenance(Long id, boolean underMaintenance);
}