package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.entity.TripStation;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Data
@AllArgsConstructor
public class TripSegment {
    private TripStation departureStop;
    private TripStation arrivalStop;

    public Integer getDepartureOrder() {
        return departureStop.getStationOrder();
    }

    public Integer getArrivalOrder() {
        return arrivalStop.getStationOrder();
    }

    /** Giờ tàu rời ga lên (theo lịch dừng); không có thì lấy giờ khởi hành của chuyến. */
    public LocalDateTime getDepartureTime() {
        if (departureStop == null) {
            return null;
        }
        if (departureStop.getDepartureTime() != null) {
            return departureStop.getDepartureTime();
        }
        return departureStop.getTrainTrip() != null ? departureStop.getTrainTrip().getDepartureTime() : null;
    }

    /** Giờ tàu tới ga xuống (theo lịch dừng); không có thì lấy giờ đến của chuyến. */
    public LocalDateTime getArrivalTime() {
        if (arrivalStop == null) {
            return null;
        }
        if (arrivalStop.getArrivalTime() != null) {
            return arrivalStop.getArrivalTime();
        }
        return arrivalStop.getTrainTrip() != null ? arrivalStop.getTrainTrip().getArrivalTime() : null;
    }

    /** Thời gian đi của đoạn (phút), 0 nếu thiếu giờ. */
    public long getDurationMinutes() {
        LocalDateTime start = getDepartureTime();
        LocalDateTime end = getArrivalTime();
        return start == null || end == null ? 0 : Math.max(0, Duration.between(start, end).toMinutes());
    }

    public long getDurationHours() {
        return getDurationMinutes() / 60;
    }

    public long getDurationRestMinutes() {
        return getDurationMinutes() % 60;
    }

    /** Số ngày chênh giữa ngày đi và ngày đến (để hiện "+1 ngày"). */
    public long getDayOffset() {
        LocalDateTime start = getDepartureTime();
        LocalDateTime end = getArrivalTime();
        return start == null || end == null ? 0 : ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
    }
}
