package com.hsf302.trainoffice.scheduler;

import com.hsf302.trainoffice.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingHoldCleanupTask {

    private static final int HOLD_MINUTES = 15;

    private final BookingService bookingService;

    public BookingHoldCleanupTask(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void releaseExpiredPendingBookings() {
        bookingService.expirePendingBookingsOlderThan(HOLD_MINUTES);
    }
}