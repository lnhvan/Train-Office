package com.hsf302.trainoffice.exception;

/**
 * Lỗi khi giữ ghế bằng Redis. Kế thừa IllegalArgumentException để các controller cũ
 * (bắt IllegalArgumentException rồi báo lỗi) hiển thị được thông báo.
 */
public class SeatHoldException extends IllegalArgumentException {

    public enum Reason {
        /** Ghế đang được người khác giữ hoặc đã bán. */
        SEAT_TAKEN,
        /** Đã giữ đủ số ghế cho số hành khách. */
        LIMIT_REACHED,
        /** Hết thời gian giữ ghế (TTL Redis đã hết) hoặc ghế giữ không khớp đơn. */
        EXPIRED,
        /** Không kết nối được Redis. */
        UNAVAILABLE
    }

    private final Reason reason;

    public SeatHoldException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public SeatHoldException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
