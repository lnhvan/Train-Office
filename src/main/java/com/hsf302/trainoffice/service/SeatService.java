package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.SeatMaintenanceCheckResult;
import com.hsf302.trainoffice.dto.SeatMaintenanceResult;
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

    /** Kiểm tra trước danh sách vé/booking bị ảnh hưởng trong tương lai khi bật bảo trì ghế. */
    SeatMaintenanceCheckResult checkFutureTickets(Long seatId);

    /** Cập nhật trạng thái bảo trì ghế. Nếu bật bảo trì và có vé tương lai, yêu cầu confirmRefund=true để hủy vé và hoàn 100%. */
    SeatMaintenanceResult updateMaintenance(Long seatId, boolean underMaintenance, boolean confirmRefund);
}
