package com.hsf302.trainoffice.redis;

import com.hsf302.trainoffice.dto.SeatHold;
import com.hsf302.trainoffice.exception.SeatHoldException;
import com.hsf302.trainoffice.exception.SeatHoldException.Reason;
import com.hsf302.trainoffice.service.SeatHoldService;
import com.hsf302.trainoffice.service.impl.RedisSeatHoldService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Chạy với Redis thật (xem RedisTestSupport). */
class RedisSeatHoldServiceTest {

    private static final long HOLD_MS = 60_000;

    private static RedisTestSupport support;
    private static RedisSeatHoldService holds;

    private static final Long TRIP = 1L;
    private static final Long ALICE = 100L;
    private static final Long BOB = 200L;

    @BeforeAll
    static void connect() {
        support = new RedisTestSupport();
        holds = new RedisSeatHoldService(support.redis, support.keyPrefix, HOLD_MS);
    }

    @AfterEach
    void cleanup() {
        support.cleanup();
    }

    @AfterAll
    static void disconnect() {
        support.close();
    }

    /** Chặng Hà Nội(1) -> Vinh(4) của chuyến TRIP. */
    private SeatHold hold(Long user, Long seat, int dep, int arr, int maxSeats) {
        return holds.holdSeat(user, TRIP, (long) dep, (long) arr, dep, arr, seat, maxSeats);
    }

    @Test
    void firstSeatStartsHoldWithFullTimeAndBlocksOtherCustomers() {
        SeatHold hold = hold(ALICE, 10L, 1, 4, 2);

        assertEquals(List.of(10L), hold.seatIds());
        assertTrue(hold.remainingMillis() > HOLD_MS - 5_000 && hold.remainingMillis() <= HOLD_MS);

        SeatHoldException ex = assertThrows(SeatHoldException.class, () -> hold(BOB, 10L, 1, 4, 1));
        assertEquals(Reason.SEAT_TAKEN, ex.getReason());

        // Chặng chồng lên một đoạn (3 -> 6) cũng bị chặn.
        ex = assertThrows(SeatHoldException.class, () -> hold(BOB, 10L, 3, 6, 1));
        assertEquals(Reason.SEAT_TAKEN, ex.getReason());
    }

    @Test
    void sameSeatOnNonOverlappingSegmentCanBeHeldByAnotherCustomer() {
        hold(ALICE, 10L, 1, 4, 1);

        // Bob đi Vinh(4) -> Huế(7): không chồng chặng của Alice.
        SeatHold bob = hold(BOB, 10L, 4, 7, 1);

        assertEquals(List.of(10L), bob.seatIds());
        assertEquals("u:" + ALICE, holds.findSeatOwners(TRIP, 1, 4, List.of(10L)).get(10L));
        assertEquals("u:" + BOB, holds.findSeatOwners(TRIP, 4, 7, List.of(10L)).get(10L));
    }

    @Test
    void cannotHoldMoreSeatsThanPassengers() {
        hold(ALICE, 10L, 1, 4, 2);
        hold(ALICE, 11L, 1, 4, 2);

        SeatHoldException ex = assertThrows(SeatHoldException.class, () -> hold(ALICE, 12L, 1, 4, 2));
        assertEquals(Reason.LIMIT_REACHED, ex.getReason());
    }

    @Test
    void extraSeatsShareTheFirstDeadlineAndReleasingKeepsIt() throws InterruptedException {
        SeatHold first = hold(ALICE, 10L, 1, 4, 3);
        Thread.sleep(300);

        SeatHold second = hold(ALICE, 11L, 1, 4, 3);
        assertTrue(second.remainingMillis() < first.remainingMillis(), "adding a seat must not extend the hold");

        SeatHold afterRelease = holds.releaseSeat(ALICE, 10L).orElseThrow();
        assertEquals(List.of(11L), afterRelease.seatIds());
        assertTrue(afterRelease.remainingMillis() <= second.remainingMillis());

        // Ghế vừa nhả: người khác giữ được.
        assertEquals(List.of(10L), hold(BOB, 10L, 1, 4, 1).seatIds());

        // Bỏ hết ghế rồi chọn lại: vẫn dùng hạn cũ, không được thêm giờ.
        holds.releaseSeat(ALICE, 11L);
        SeatHold again = hold(ALICE, 12L, 1, 4, 3);
        assertTrue(again.remainingMillis() <= afterRelease.remainingMillis());
    }

    @Test
    void choosingAnotherSegmentReleasesThePreviousHold() {
        hold(ALICE, 10L, 1, 4, 1);

        SeatHold other = holds.holdSeat(ALICE, 2L, 1L, 4L, 1, 4, 99L, 1);

        assertEquals(2L, other.tripId());
        assertTrue(holds.findSeatOwners(TRIP, 1, 4, List.of(10L)).isEmpty());
        assertEquals(List.of(10L), hold(BOB, 10L, 1, 4, 1).seatIds());
    }

    @Test
    void requireUserHoldChecksSegmentAndExactSeats() {
        hold(ALICE, 10L, 1, 4, 2);
        hold(ALICE, 11L, 1, 4, 2);

        assertEquals(2, holds.requireUserHold(ALICE, TRIP, 1L, 4L, List.of(11L, 10L)).seatIds().size());

        assertEquals(Reason.EXPIRED, assertThrows(SeatHoldException.class,
                () -> holds.requireUserHold(ALICE, TRIP, 1L, 4L, List.of(10L))).getReason());
        assertEquals(Reason.EXPIRED, assertThrows(SeatHoldException.class,
                () -> holds.requireUserHold(ALICE, TRIP, 1L, 5L, List.of(10L, 11L))).getReason());
        assertEquals(Reason.EXPIRED, assertThrows(SeatHoldException.class,
                () -> holds.requireUserHold(BOB, TRIP, 1L, 4L, List.of(10L, 11L))).getReason());
    }

    @Test
    void holdExpiresByItselfAndSeatBecomesFree() throws InterruptedException {
        RedisSeatHoldService shortHolds = new RedisSeatHoldService(support.redis, support.keyPrefix, 400);
        shortHolds.holdSeat(ALICE, TRIP, 1L, 4L, 1, 4, 10L, 1);

        Thread.sleep(800);

        assertTrue(shortHolds.findUserHold(ALICE).isEmpty());
        assertTrue(shortHolds.findSeatOwners(TRIP, 1, 4, List.of(10L)).isEmpty());
        assertEquals(List.of(10L), shortHolds.holdSeat(BOB, TRIP, 1L, 4L, 1, 4, 10L, 1).seatIds());
    }

    /** Hạn thanh toán của đơn: 10 phút kể từ lúc đặt (giống BookingHoldPolicy.PAYMENT_WINDOW_MINUTES). */
    private static LocalDateTime payUntil() {
        return LocalDateTime.now().plusMinutes(10);
    }

    @Test
    void transferToBookingResetsHoldToPaymentDeadlineAndChangesOwner() throws InterruptedException {
        // Khách chọn ghế với hạn rất ngắn (sắp hết giờ giữ ghế).
        RedisSeatHoldService shortHolds = new RedisSeatHoldService(support.redis, support.keyPrefix, 2_000);
        shortHolds.holdSeat(ALICE, TRIP, 1L, 4L, 1, 4, 10L, 2);
        shortHolds.holdSeat(ALICE, TRIP, 1L, 4L, 1, 4, 11L, 2);

        SeatHold moved = shortHolds.transferToBooking(ALICE, 500L, payUntil());

        assertEquals(List.of(10L, 11L), moved.seatIds());
        assertTrue(shortHolds.findUserHold(ALICE).isEmpty());

        // Bấm đặt chỗ: hạn đặt lại thành ~10 phút, kể cả khi lúc chọn ghế chỉ còn vài giây.
        SeatHold bookingHold = shortHolds.findBookingHold(500L).orElseThrow();
        assertTrue(bookingHold.remainingMillis() > 9 * 60_000 && bookingHold.remainingMillis() <= 10 * 60_000);
        long legTtl = support.redis.getExpire(support.keyPrefix + "seat:{t1}:10:1",
                java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue(legTtl > 9 * 60_000, "seat legs must follow the payment deadline");

        Thread.sleep(2_500);
        assertTrue(shortHolds.isBookingHoldActive(500L), "old seat-selection hold time no longer applies");

        Map<Long, String> owners = holds.findSeatOwners(TRIP, 1, 4, List.of(10L, 11L));
        assertEquals("b:500", owners.get(10L));
        assertEquals("b:500", owners.get(11L));

        // Không còn giữ của khách -> không tạo đơn thứ hai từ cùng lượt giữ.
        assertEquals(Reason.EXPIRED, assertThrows(SeatHoldException.class,
                () -> shortHolds.transferToBooking(ALICE, 501L, payUntil())).getReason());
    }

    @Test
    void paymentExtendsHoldAndFailedPaymentRestoresOriginalDeadline() {
        hold(ALICE, 10L, 1, 4, 1);
        holds.transferToBooking(ALICE, 500L, payUntil());
        long original = holds.findBookingHold(500L).orElseThrow().remainingMillis();

        assertTrue(holds.extendBookingHold(500L, LocalDateTime.now().plusMinutes(20)));
        long extended = holds.findBookingHold(500L).orElseThrow().remainingMillis();
        assertTrue(extended > original + 60_000, "payment must extend the hold");
        assertTrue(support.redis.getExpire(support.keyPrefix + "seat:{t1}:10:1",
                java.util.concurrent.TimeUnit.MILLISECONDS) > original + 60_000, "seat legs must be extended too");

        holds.restoreBookingHoldAfterFailedPayment(500L);
        long restored = holds.findBookingHold(500L).orElseThrow().remainingMillis();
        assertTrue(restored <= original, "failed payment returns to the original hold deadline");
        assertTrue(restored > original - 5_000);
    }

    @Test
    void extendFailsWhenBookingHoldIsGone() {
        assertFalse(holds.extendBookingHold(999L, LocalDateTime.now().plusMinutes(20)));
    }

    @Test
    void cannotStartPaymentAfterPaymentDeadline() throws InterruptedException {
        hold(ALICE, 10L, 1, 4, 1);
        holds.transferToBooking(ALICE, 500L, LocalDateTime.now().plusNanos(400_000_000));

        Thread.sleep(700);

        assertFalse(holds.extendBookingHold(500L, LocalDateTime.now().plusMinutes(20)));
        assertFalse(holds.isBookingHoldActive(500L));
    }

    @Test
    void releaseBookingHoldAndReleaseTripFreeSeats() {
        hold(ALICE, 10L, 1, 4, 1);
        holds.transferToBooking(ALICE, 500L, payUntil());
        holds.releaseBookingHold(500L);

        assertFalse(holds.isBookingHoldActive(500L));
        assertTrue(holds.findSeatOwners(TRIP, 1, 4, List.of(10L)).isEmpty());

        hold(ALICE, 10L, 1, 4, 2);
        hold(BOB, 20L, 1, 4, 1);
        holds.releaseTrip(TRIP);

        assertTrue(holds.findSeatOwners(TRIP, 1, 4, List.of(10L, 20L)).isEmpty());
    }

    @Test
    void ownersHelperBuildsExpectedOwnerStrings() {
        assertEquals("u:7", SeatHoldService.userOwner(7L));
        assertEquals("b:8", SeatHoldService.bookingOwner(8L));
    }
}
