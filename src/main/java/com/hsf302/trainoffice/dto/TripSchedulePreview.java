package com.hsf302.trainoffice.dto;

import java.util.List;

/**
 * Lịch dừng dự kiến (chưa lưu) của một chuyến: dùng cho phần xem trước ở form tạo chuyến.
 * Giờ đã được định dạng sẵn "HH:mm dd/MM" để trang chỉ việc hiển thị.
 */
public record TripSchedulePreview(
        List<Stop> stops,
        String departureTime,
        String arrivalTime,
        long totalMinutes,
        int speedKmh,
        long dwellMinutes
) {

    public record Stop(
            int order,
            String stationName,
            double km,
            String arrivalTime,
            String departureTime
    ) {
    }
}
