package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.SeatHold;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Giữ ghế tạm thời bằng Redis.
 *
 * <p>Mỗi ghế được khóa theo từng đoạn giữa hai ga liền nhau của chặng khách đi
 * (key {@code rj:seat:{t<tripId>}:<seatId>:<thứ tự ga đầu đoạn>}), giá trị là chủ khóa:
 * {@code u:<userId>} khi khách đang chọn ghế, {@code b:<bookingId>} khi đã tạo đơn chờ thanh toán.
 * Khóa tự hết hạn theo TTL nên không cần job để nhả ghế.</p>
 *
 * <p>Vòng đời: chọn ghế ({@link #holdSeat}) -> tạo đơn ({@link #transferToBooking})
 * -> bấm thanh toán ({@link #extendBookingHold}) -> VNPay thành công ({@link #releaseBookingHold})
 * hoặc thất bại ({@link #restoreBookingHoldAfterFailedPayment}).</p>
 *
 * <p>Mọi lỗi kết nối Redis được đổi thành {@code SeatHoldException(UNAVAILABLE)}.</p>
 */
public interface SeatHoldService {

    String USER_OWNER_PREFIX = "u:";
    String BOOKING_OWNER_PREFIX = "b:";

    static String userOwner(Long userId) {
        return USER_OWNER_PREFIX + userId;
    }

    static String bookingOwner(Long bookingId) {
        return BOOKING_OWNER_PREFIX + bookingId;
    }

    /**
     * Khách chọn thêm một ghế. Lần chọn đầu tiên của chặng bắt đầu đếm giờ giữ chỗ;
     * các ghế chọn sau dùng chung hạn đó (không gia hạn). Chọn chặng khác thì bỏ giữ chặng cũ.
     *
     * @param maxSeats số ghế tối đa được giữ (= số hành khách)
     */
    SeatHold holdSeat(Long userId,
                      Long tripId,
                      Long departureStationId,
                      Long arrivalStationId,
                      int departureOrder,
                      int arrivalOrder,
                      Long seatId,
                      int maxSeats);

    /** Bỏ chọn một ghế. Hạn giữ chỗ vẫn giữ nguyên (chọn lại ghế khác không được thêm giờ). */
    Optional<SeatHold> releaseSeat(Long userId, Long seatId);

    /** Ghế khách đang giữ (nếu còn hạn). */
    Optional<SeatHold> findUserHold(Long userId);

    void releaseUserHold(Long userId);

    /**
     * Kiểm tra khách vẫn đang giữ đúng các ghế này cho đúng chặng này.
     *
     * @throws com.hsf302.trainoffice.exception.SeatHoldException EXPIRED nếu hết hạn hoặc không khớp
     */
    SeatHold requireUserHold(Long userId,
                             Long tripId,
                             Long departureStationId,
                             Long arrivalStationId,
                             Collection<Long> seatIds);

    /**
     * Đơn vừa được tạo: chuyển khóa ghế từ khách sang đơn và đặt hạn mới {@code payUntil}
     * (thời hạn thanh toán của đơn).
     *
     * @throws com.hsf302.trainoffice.exception.SeatHoldException EXPIRED nếu khóa của khách đã hết hạn
     */
    SeatHold transferToBooking(Long userId, Long bookingId, LocalDateTime payUntil);

    Optional<SeatHold> findBookingHold(Long bookingId);

    boolean isBookingHoldActive(Long bookingId);

    /**
     * Gia hạn khóa của đơn tới ít nhất {@code until} (lúc khách bấm thanh toán VNPay).
     *
     * @return false nếu khóa đã hết hạn hoặc đã quá thời hạn thanh toán
     */
    boolean extendBookingHold(Long bookingId, LocalDateTime until);

    /** Thanh toán thất bại: khóa quay về hạn giữ chỗ ban đầu (hết rồi thì nhả luôn). */
    void restoreBookingHoldAfterFailedPayment(Long bookingId);

    void releaseBookingHold(Long bookingId);

    /** Nhả mọi khóa ghế của một chuyến (admin hủy chuyến). */
    void releaseTrip(Long tripId);

    /**
     * Chủ khóa của các ghế đang bị giữ trên chặng [departureOrder, arrivalOrder).
     * Ghế không bị giữ thì không có trong map.
     */
    Map<Long, String> findSeatOwners(Long tripId, int departureOrder, int arrivalOrder, Collection<Long> seatIds);
}
