package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.security.AuthCookie;
import com.hsf302.trainoffice.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

@Controller
public class OAuth2Controller {

    private static final String OAUTH2_STATE = "OAUTH2_STATE";
    private static final String OAUTH2_REDIRECT = "OAUTH2_REDIRECT";

    private final GoogleOAuthService googleOAuthService;
    private final AuthCookie authCookie;

    public OAuth2Controller(GoogleOAuthService googleOAuthService, AuthCookie authCookie) {
        this.googleOAuthService = googleOAuthService;
        this.authCookie = authCookie;
    }

    /**
     * Khởi tạo quá trình đăng nhập qua Google OAuth 2.0.
     */
    @GetMapping("/oauth2/login/google")
    public String loginWithGoogle(@RequestParam(value = "redirect", required = false) String redirect,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        if (!googleOAuthService.isConfigured()) {
            redirectAttributes.addFlashAttribute("error", I18n.t("msg.auth.googleNotConfigured"));
            return "redirect:/login" + (redirect != null && !redirect.isBlank() ? "?redirect=" + redirect : "");
        }

        String state = UUID.randomUUID().toString();
        session.setAttribute(OAUTH2_STATE, state);

        String safeTarget = safeRedirect(redirect);
        if (safeTarget != null) {
            session.setAttribute(OAUTH2_REDIRECT, safeTarget);
        }

        return "redirect:" + googleOAuthService.buildAuthorizationUrl(state);
    }

    /**
     * Nhận callback ủy quyền từ Google trả về duy nhất qua /google-login (theo application.properties).
     */
    @GetMapping("/google-login")
    public String handleGoogleCallback(@RequestParam(value = "code", required = false) String code,
                                       @RequestParam(value = "state", required = false) String state,
                                       @RequestParam(value = "error", required = false) String error,
                                       HttpSession session,
                                       HttpServletResponse response,
                                       RedirectAttributes redirectAttributes) {
        String savedRedirect = (String) session.getAttribute(OAUTH2_REDIRECT);
        session.removeAttribute(OAUTH2_REDIRECT);

        String redirectQuery = savedRedirect != null && !savedRedirect.isBlank() ? "?redirect=" + savedRedirect : "";

        // Trường hợp người dùng hủy hoặc Google trả về mã lỗi
        if (error != null) {
            if ("access_denied".equalsIgnoreCase(error)) {
                redirectAttributes.addFlashAttribute("infoMessage", I18n.t("msg.auth.googleCancelled"));
            } else {
                redirectAttributes.addFlashAttribute("error", I18n.t("msg.auth.googleError", error));
            }
            return "redirect:/login" + redirectQuery;
        }

        // Kiểm tra tính hợp lệ của tham số state (chống CSRF)
        String sessionState = (String) session.getAttribute(OAUTH2_STATE);
        session.removeAttribute(OAUTH2_STATE);

        if (sessionState != null && state != null && !sessionState.equals(state)) {
            redirectAttributes.addFlashAttribute("error", I18n.t("msg.auth.invalidState"));
            return "redirect:/login" + redirectQuery;
        }

        if (code == null || code.isBlank()) {
            redirectAttributes.addFlashAttribute("error", I18n.t("msg.auth.missingCode"));
            return "redirect:/login" + redirectQuery;
        }

        try {
            // Trao đổi code lấy thông tin người dùng từ Google
            Map<String, Object> userInfo = googleOAuthService.exchangeCodeForUserInfo(code);

            // Tìm hoặc tự động tạo tài khoản trong DB
            User user = googleOAuthService.processGoogleUser(userInfo);

            // Đăng nhập thành công: cấp JWT Cookie và hủy giỏ hàng của phiên ẩn danh cũ
            session.invalidate();
            authCookie.signIn(response, user);

            if (user.getRole() == UserRole.ADMIN) {
                return "redirect:/admin/dashboard";
            }

            return "redirect:" + (savedRedirect != null ? savedRedirect : "/booking/search");

        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/login" + redirectQuery;
        }
    }

    /** Chỉ cho phép quay lại đường dẫn nội bộ ("/..."), chặn chuyển hướng trang ngoài. */
    private String safeRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return null;
        }
        String value = redirect.trim();
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("\\")
                || value.contains("://") || value.startsWith("/login") || value.startsWith("/logout")) {
            return null;
        }
        return value;
    }
}
