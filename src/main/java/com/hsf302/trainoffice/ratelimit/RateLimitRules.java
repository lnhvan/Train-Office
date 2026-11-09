package com.hsf302.trainoffice.ratelimit;

import com.hsf302.trainoffice.ratelimit.RateLimitRule.Subject;

import java.time.Duration;
import java.util.List;

/** Bảng giới hạn request của các endpoint công khai. Sửa số ở đây để điều chỉnh. */
public final class RateLimitRules {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration QUARTER_HOUR = Duration.ofMinutes(15);
    private static final Duration HOUR = Duration.ofHours(1);

    public static final List<RateLimitRule> RULES = List.of(
            // Đăng nhập: chống dò mật khẩu theo IP và theo tài khoản.
            rule("login-ip", "POST", List.of("/login"), Subject.IP, 10, MINUTE),
            rule("login-email", "POST", List.of("/login"), Subject.EMAIL, 15, QUARTER_HOUR),
            rule("google-login", "GET", List.of("/oauth2/login/google", "/google-login"), Subject.IP, 20, MINUTE),

            // Đăng ký: chống tạo tài khoản rác.
            rule("register", "POST", List.of("/register"), Subject.IP, 5, HOUR),

            // Quên mật khẩu: mỗi lần gửi một email.
            rule("forgot-email", "POST", List.of("/forgot-password"), Subject.EMAIL, 3, QUARTER_HOUR),
            rule("forgot-ip", "POST", List.of("/forgot-password", "/reset-password/resend"), Subject.IP, 10, HOUR),
            // Nhập mã OTP: chống dò mã 6 số.
            rule("reset-password", "POST", List.of("/reset-password"), Subject.IP, 10, QUARTER_HOUR),

            // OCR CCCD: Tesseract tốn CPU.
            rule("ocr", "POST", List.of("/booking/ocr-cccd"), Subject.USER, 10, MINUTE),

            // Giữ / bỏ ghế: mỗi lần bấm một request.
            rule("seat-hold", "POST,DELETE", List.of("/api/booking/holds/seats", "/api/booking/holds/seats/*"),
                    Subject.USER, 60, MINUTE),
            rule("seat-hold-status", "GET", List.of("/api/booking/holds/current"), Subject.USER, 60, MINUTE),

            // Các bước đặt vé: chống giữ ghế / tạo đơn hàng loạt.
            rule("booking-steps", "POST", List.of("/booking/seat-selection", "/booking/confirmation"),
                    Subject.USER, 10, MINUTE),

            // Tạo link VNPay.
            rule("payment", "POST", List.of("/payments/bookings/*"), Subject.USER, 5, MINUTE),

            // Tìm chuyến: truy vấn nặng.
            rule("trip-search", "GET,POST", List.of("/booking/trips", "/api/booking/station-availability"),
                    Subject.IP, 60, MINUTE)
    );

    private RateLimitRules() {
    }

    private static RateLimitRule rule(String name,
                                      String methods,
                                      List<String> patterns,
                                      Subject subject,
                                      int limit,
                                      Duration window) {
        return new RateLimitRule(name, List.of(methods.split(",")), patterns, subject, limit, window);
    }
}
