package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.entity.Seat;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeatAvailabilityView {
    private Seat seat;
    /** Khách hiện tại chọn được (trống, hoặc chính khách đang giữ). */
    private boolean available;
    /** Khách hiện tại đang giữ ghế này trong Redis (hiển thị sẵn là đã chọn). */
    private boolean heldByMe;

    public SeatAvailabilityView(Seat seat, boolean available) {
        this(seat, available, false);
    }
}
