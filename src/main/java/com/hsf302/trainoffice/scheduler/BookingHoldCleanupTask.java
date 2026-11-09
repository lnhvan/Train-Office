package com.hsf302.trainoffice.scheduler;

import com.hsf302.trainoffice.exception.SeatHoldException;
import com.hsf302.trainoffice.service.BookingService;
import com.hsf302.trainoffice.service.SeatHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mỗi phút hủy các đơn chờ thanh toán đã hết khóa giữ ghế trong Redis.
 * Redis không kết nối được thì bỏ qua lượt chạy (không hủy nhầm đơn đang được giữ).
 */
@Component
public class BookingHoldCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(BookingHoldCleanupTask.class);

    private final BookingService bookingService;
    private final SeatHoldService seatHoldService;

    public BookingHoldCleanupTask(BookingService bookingService, SeatHoldService seatHoldService) {
        this.bookingService = bookingService;
        this.seatHoldService = seatHoldService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void releaseExpiredPendingBookings() {
        try {
            var cancelled = bookingService.expireBookingsWithoutHold(seatHoldService::isBookingHoldActive);
            if (!cancelled.isEmpty()) {
                log.info("[SeatHold] Redis hold expired -> cancelled pending bookings {}", cancelled);
            }
        } catch (SeatHoldException ex) {
            log.warn("Skip expiring pending bookings: {}", ex.getMessage());
        }
    }
}
