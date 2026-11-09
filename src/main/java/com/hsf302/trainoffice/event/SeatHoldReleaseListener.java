package com.hsf302.trainoffice.event;

import com.hsf302.trainoffice.service.SeatHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Nhả khóa Redis sau khi DB đã commit: nếu transaction bị rollback thì khóa vẫn còn,
 * ghế không bị nhả nhầm. Redis lỗi thì chỉ ghi log (khóa sẽ tự hết hạn theo TTL).
 */
@Component
public class SeatHoldReleaseListener {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldReleaseListener.class);

    private final SeatHoldService seatHoldService;

    public SeatHoldReleaseListener(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRelease(SeatHoldReleaseEvent event) {
        try {
            if (event.tripId() != null) {
                seatHoldService.releaseTrip(event.tripId());
            }
            for (Long bookingId : event.bookingIds()) {
                seatHoldService.releaseBookingHold(bookingId);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not release seat holds in Redis ({}): {}", event, ex.getMessage());
        }
    }
}
