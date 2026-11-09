package com.hsf302.trainoffice.event;

import java.util.List;

/**
 * Yêu cầu nhả khóa ghế trong Redis sau khi transaction ghi DB thành công.
 *
 * @param bookingIds các đơn cần nhả khóa (đơn bị hủy, hết hạn, đã thanh toán)
 * @param tripId     khác null: nhả mọi khóa ghế của chuyến (admin hủy chuyến)
 */
public record SeatHoldReleaseEvent(List<Long> bookingIds, Long tripId) {

    public static SeatHoldReleaseEvent bookings(List<Long> bookingIds) {
        return new SeatHoldReleaseEvent(List.copyOf(bookingIds), null);
    }

    public static SeatHoldReleaseEvent booking(Long bookingId) {
        return new SeatHoldReleaseEvent(List.of(bookingId), null);
    }

    public static SeatHoldReleaseEvent trip(Long tripId, List<Long> bookingIds) {
        return new SeatHoldReleaseEvent(List.copyOf(bookingIds), tripId);
    }
}
