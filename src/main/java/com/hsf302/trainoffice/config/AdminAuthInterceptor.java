package com.hsf302.trainoffice.config;

import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Chặn trang quản trị: chỉ tài khoản ADMIN (xác thực bằng JWT, xem JwtAuthenticationFilter) được vào.
 * Chưa đăng nhập -> trang đăng nhập; tài khoản khách hàng -> về trang tìm chuyến.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        User user = CurrentUser.get(request);

        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (user.getRole() != UserRole.ADMIN) {
            response.sendRedirect(request.getContextPath() + "/booking/search");
            return false;
        }

        return true;
    }
}
