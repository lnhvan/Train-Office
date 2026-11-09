package com.hsf302.trainoffice.dto;

import java.util.List;

/**
 * Ghế đang được giữ tạm trong Redis (của một khách đang chọn ghế, hoặc của một đơn chờ thanh toán).
 *
 * @param tripId              chuyến
 * @param departureStationId  ga lên
 * @param arrivalStationId    ga xuống
 * @param departureOrder      thứ tự ga lên trên tuyến
 * @param arrivalOrder        thứ tự ga xuống trên tuyến
 * @param seatIds             các ghế đang giữ (theo thứ tự chọn)
 * @param remainingMillis     thời gian giữ còn lại, đọc từ TTL của Redis
 */
public record SeatHold(Long tripId,
                       Long departureStationId,
                       Long arrivalStationId,
                       int departureOrder,
                       int arrivalOrder,
                       List<Long> seatIds,
                       long remainingMillis) {

    public boolean hasSeats() {
        return seatIds != null && !seatIds.isEmpty();
    }

    public boolean isForSegment(Long tripId, Long departureStationId, Long arrivalStationId) {
        return this.tripId.equals(tripId)
                && this.departureStationId.equals(departureStationId)
                && this.arrivalStationId.equals(arrivalStationId);
    }

    public long remainingSeconds() {
        return Math.max(0, (remainingMillis + 999) / 1000);
    }
}
