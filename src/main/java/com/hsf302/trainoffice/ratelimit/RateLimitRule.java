package com.hsf302.trainoffice.ratelimit;

import java.time.Duration;
import java.util.List;

/**
 * Một quy tắc giới hạn request.
 *
 * @param name     tên (dùng trong key Redis)
 * @param methods  HTTP method áp dụng
 * @param patterns đường dẫn (AntPath, vd. {@code /payments/bookings/*})
 * @param subject  đếm theo IP, theo tài khoản, hay theo email trong form
 * @param limit    số request tối đa trong một cửa sổ
 * @param window   độ dài cửa sổ
 */
public record RateLimitRule(String name,
                            List<String> methods,
                            List<String> patterns,
                            Subject subject,
                            int limit,
                            Duration window) {

    public enum Subject {
        /** Địa chỉ IP của client. */
        IP,
        /** Tài khoản đang đăng nhập; chưa đăng nhập thì theo IP. */
        USER,
        /** Tham số {@code email} trong form (viết thường); không có thì bỏ qua quy tắc. */
        EMAIL
    }
}
