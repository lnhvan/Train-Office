package com.hsf302.trainoffice.security;

import com.hsf302.trainoffice.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Người dùng đang đăng nhập của request hiện tại.
 *
 * JwtAuthenticationFilter kiểm tra cookie JWT ở đầu mỗi request rồi đặt User vào
 * request attribute "currentUser". Controller / service đọc qua lớp này;
 * template Thymeleaf đọc thẳng bằng ${currentUser}.
 */
public final class CurrentUser {

    public static final String ATTRIBUTE = "currentUser";

    private CurrentUser() {
    }

    public static User get(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(ATTRIBUTE);
        return value instanceof User user ? user : null;
    }

    /** Dùng được ở mọi nơi trong luồng xử lý request (controller, service). */
    public static User get() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object value = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof User user ? user : null;
    }

    static void set(HttpServletRequest request, User user) {
        request.setAttribute(ATTRIBUTE, user);
    }
}
