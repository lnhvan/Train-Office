package com.hsf302.trainoffice.common;

import java.time.LocalDateTime;

/**
 * Thời gian giữ chỗ khi đặt vé (khóa ghế nằm trong Redis, xem SeatHoldService).
 * <ul>
 *     <li>Khách chọn ghế đầu tiên: ghế được giữ {@link #HOLD_MINUTES} phút. Các ghế chọn thêm,
 *     bước nhập hành khách và xác nhận dùng chung hạn này.</li>
 *     <li>Bấm đặt chỗ (tạo đơn): hạn giữ ghế đặt lại thành {@link #PAYMENT_WINDOW_MINUTES} phút kể từ lúc
 *     tạo đơn. Đây là thời hạn thanh toán hiển thị cho khách ({@link #paymentDeadline}).</li>
 *     <li>Khi khách bấm thanh toán VNPay (trước thời hạn thanh toán), khóa của đơn được gia hạn theo hạn của link VNPay
 *     ({@link #VNPAY_PAYMENT_MINUTES} phút) cộng {@link #ORDER_LOCK_GRACE_MINUTES} phút dự phòng
 *     để VNPay kịp trả kết quả.</li>
 *     <li>Đơn chờ thanh toán mà khóa Redis đã hết hạn sẽ bị job hủy; đơn mới tạo được miễn
 *     {@link #NEW_BOOKING_GRACE_SECONDS} giây để khóa kịp chuyển sang đơn.</li>
 * </ul>
 */
public final class BookingHoldPolicy {

    public static final int HOLD_MINUTES = 15;

    /** Thời gian khách có để thanh toán, tính từ lúc bấm đặt chỗ. */
    public static final int PAYMENT_WINDOW_MINUTES = 10;

    public static final int VNPAY_PAYMENT_MINUTES = 15;

    public static final int ORDER_LOCK_GRACE_MINUTES = 5;

    public static final int NEW_BOOKING_GRACE_SECONDS = 60;

    /** Mỗi tài khoản tối đa bấy nhiêu đơn chờ thanh toán cùng lúc (chống giữ ghế hàng loạt). */
    public static final int MAX_PENDING_BOOKINGS_PER_USER = 2;

    private BookingHoldPolicy() {
    }

    /** Thời hạn thanh toán của đơn tạo lúc {@code bookingDate}. */
    public static LocalDateTime paymentDeadline(LocalDateTime bookingDate) {
        return bookingDate == null ? null : bookingDate.plusMinutes(PAYMENT_WINDOW_MINUTES);
    }
}
