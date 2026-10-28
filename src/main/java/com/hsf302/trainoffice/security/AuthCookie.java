package com.hsf302.trainoffice.security;

import com.hsf302.trainoffice.entity.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lưu JWT trong cookie HttpOnly (JavaScript không đọc được -> giảm rủi ro XSS lấy token),
 * SameSite=Lax (form POST từ trang web khác không kèm cookie -> giảm rủi ro CSRF;
 * VNPay chuyển hướng GET về /payments/vnpay-return vẫn kèm cookie).
 */
@Component
public class AuthCookie {

    private final JwtService jwtService;
    private final String name;
    private final boolean secure;

    public AuthCookie(JwtService jwtService,
                      @Value("${app.jwt.cookie-name:RJ_AUTH}") String name,
                      @Value("${app.jwt.cookie-secure:false}") boolean secure) {
        this.jwtService = jwtService;
        this.name = name;
        this.secure = secure;
    }

    /** Tạo JWT cho user và gửi về trình duyệt. */
    public void signIn(HttpServletResponse response, User user) {
        write(response, jwtService.createToken(user), jwtService.getTtl());
    }

    /** Xóa cookie đăng nhập. */
    public void clear(HttpServletResponse response) {
        write(response, "", Duration.ZERO);
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void write(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
