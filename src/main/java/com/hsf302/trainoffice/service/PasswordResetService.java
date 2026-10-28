package com.hsf302.trainoffice.service;

public interface PasswordResetService {

    /** Thời gian mã còn hiệu lực. */
    int CODE_TTL_MINUTES = 10;

    /** Số lần nhập sai tối đa cho một mã. */
    int MAX_ATTEMPTS = 5;

    /** Thời gian chờ giữa hai lần gửi mã. */
    int RESEND_COOLDOWN_SECONDS = 60;

    /**
     * Tạo mã mới và gửi email. Email không tồn tại / bị khóa thì không làm gì
     * nhưng vẫn trả SENT để không lộ email nào có tài khoản.
     */
    RequestResult requestCode(String email);

    /** Kiểm tra mã và đặt mật khẩu mới. */
    ResetResult resetPassword(String email, String code, String newPassword);

    enum RequestResult {
        SENT,
        TOO_SOON
    }

    enum ResetResult {
        OK,
        INVALID_CODE,
        EXPIRED,
        TOO_MANY_ATTEMPTS
    }
}
