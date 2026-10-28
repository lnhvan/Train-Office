package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.ForgotPasswordRequest;
import com.hsf302.trainoffice.dto.RegisterRequest;
import com.hsf302.trainoffice.dto.ResetPassword;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.security.AuthCookie;
import com.hsf302.trainoffice.service.PasswordResetService;
import com.hsf302.trainoffice.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    /** Email đang đặt lại mật khẩu (chỉ dùng cho luồng quên mật khẩu, không phải đăng nhập). */
    private static final String RESET_EMAIL = "resetEmail";

    @Autowired
    private UserService userService;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private AuthCookie authCookie;

    //Login !!!!! ======================
    @GetMapping("/login")
    public String showLogin(@RequestParam(value = "redirect", required = false) String redirect,
                            Model model) {
        model.addAttribute("redirect", safeRedirect(redirect));
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam("email") String email,
                        @RequestParam("password") String pwd,
                        @RequestParam(value = "redirect", required = false) String redirect,
                        Model model,
                        HttpSession session,
                        HttpServletResponse response) {
        User user = userService.login(email, pwd);

        if (user == null) {
            model.addAttribute("error", I18n.t("msg.auth.loginFailed"));
            model.addAttribute("email", email);
            model.addAttribute("redirect", safeRedirect(redirect));
            return "auth/login";
        }

        // Đăng nhập thành công: cấp JWT trong cookie HttpOnly. Bỏ giỏ đặt vé của phiên trước (nếu có).
        session.invalidate();
        authCookie.signIn(response, user);
        return "redirect:" + landingPage(user, redirect);
    }

    /** Trang mở ra sau khi đăng nhập: quay lại trang khách đang xem (nếu có), admin vào dashboard. */
    private String landingPage(User user, String redirect) {
        if (user.getRole().name().equalsIgnoreCase("ADMIN")) {
            return "/admin/dashboard";
        }
        String target = safeRedirect(redirect);
        return target != null ? target : "/booking/search";
    }

    /** Chỉ cho quay lại đường dẫn nội bộ ("/..."), chặn chuyển hướng sang trang ngoài. */
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

    @GetMapping("/logout")
    public String logout(HttpSession session, HttpServletResponse response) {
        authCookie.clear(response);
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/register")
    public String showRegister(@RequestParam(value = "redirect", required = false) String redirect,
                               Model model) {

        model.addAttribute(
                "registerRequest",
                new RegisterRequest());
        model.addAttribute("redirect", safeRedirect(redirect));

        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest registerRequest,
                           BindingResult bindingResult,
                           @RequestParam(value = "redirect", required = false) String redirect,
                           Model model,
                           HttpServletResponse response,
                           RedirectAttributes redirectAttributes) {

        model.addAttribute("redirect", safeRedirect(redirect));

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        if (!registerRequest.getPassword()
                .equals(registerRequest.getConfirmPassword())) {

            bindingResult.rejectValue(
                    "confirmPassword",
                    "error.confirmPassword",
                    I18n.t("msg.auth.passwordMismatch"));

            return "auth/register";
        }

        if (userService.existsByEmail(registerRequest.getEmail())) {

            bindingResult.rejectValue(
                    "email",
                    "error.email",
                    I18n.t("msg.auth.emailExists"));

            return "auth/register";
        }

        userService.register(registerRequest);

        // Đăng ký xong thì đăng nhập luôn, không bắt nhập lại.
        User user = userService.login(registerRequest.getEmail(), registerRequest.getPassword());
        if (user == null) {
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.auth.registered"));
            return "redirect:/login" + (safeRedirect(redirect) != null
                    ? "?redirect=" + java.net.URLEncoder.encode(safeRedirect(redirect), java.nio.charset.StandardCharsets.UTF_8)
                    : "");
        }

        authCookie.signIn(response, user);
        redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.auth.registeredAndSignedIn"));
        return "redirect:" + landingPage(user, redirect);
    }

    // Quên mật khẩu: nhập email -> nhận mã 6 số qua email -> nhập mã + mật khẩu mới ======

    @GetMapping("/forgot-password")
    public String showForgotPassword(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordRequest());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordRequest request,
                                 BindingResult bindingResult,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        String email = request.getEmail().trim().toLowerCase();

        try {
            if (passwordResetService.requestCode(email) == PasswordResetService.RequestResult.TOO_SOON) {
                redirectAttributes.addFlashAttribute("infoMessage",
                        I18n.t("msg.auth.resetCodeTooSoon", PasswordResetService.RESEND_COOLDOWN_SECONDS));
            } else {
                redirectAttributes.addFlashAttribute("infoMessage", I18n.t("msg.auth.resetCodeSent"));
            }
        } catch (MailException ex) {
            model.addAttribute("errorMessage", I18n.t("msg.auth.resetMailFailed"));
            return "auth/forgot-password";
        }

        // Luôn sang bước nhập mã (kể cả email không có tài khoản) để không lộ email nào đã đăng ký.
        session.setAttribute(RESET_EMAIL, email);
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String showResetPassword(HttpSession session, Model model) {
        String email = (String) session.getAttribute(RESET_EMAIL);
        if (email == null) {
            return "redirect:/forgot-password";
        }

        model.addAttribute("resetPasswordForm", new ResetPassword());
        addResetModel(model, email);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password/resend")
    public String resendResetCode(HttpSession session, RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(RESET_EMAIL);
        if (email == null) {
            return "redirect:/forgot-password";
        }

        try {
            if (passwordResetService.requestCode(email) == PasswordResetService.RequestResult.TOO_SOON) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        I18n.t("msg.auth.resetCodeTooSoon", PasswordResetService.RESEND_COOLDOWN_SECONDS));
            } else {
                redirectAttributes.addFlashAttribute("infoMessage", I18n.t("msg.auth.resetCodeResent"));
            }
        } catch (MailException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.auth.resetMailFailed"));
        }
        return "redirect:/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPassword request,
                                BindingResult bindingResult,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        String email = (String) session.getAttribute(RESET_EMAIL);
        if (email == null) {
            return "redirect:/forgot-password";
        }
        addResetModel(model, email);

        if (!bindingResult.hasFieldErrors("confirmPassword")
                && request.getPassword() != null
                && !request.getPassword().equals(request.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", I18n.t("msg.auth.passwordMismatch"));
        }

        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        PasswordResetService.ResetResult result =
                passwordResetService.resetPassword(email, request.getCode(), request.getPassword());

        switch (result) {
            case OK -> {
                session.removeAttribute(RESET_EMAIL);
                redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.auth.passwordReset"));
                return "redirect:/login";
            }
            case EXPIRED -> model.addAttribute("errorMessage", I18n.t("msg.auth.resetCodeExpired"));
            case TOO_MANY_ATTEMPTS -> model.addAttribute("errorMessage",
                    I18n.t("msg.auth.resetCodeTooManyAttempts"));
            default -> bindingResult.rejectValue("code", "error.code", I18n.t("msg.auth.resetCodeInvalid"));
        }

        return "auth/reset-password";
    }

    private void addResetModel(Model model, String email) {
        model.addAttribute("resetEmail", maskEmail(email));
        model.addAttribute("resetCodeMinutes", PasswordResetService.CODE_TTL_MINUTES);
    }

    /** nguyenvana@gmail.com -> ng*******@gmail.com */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String name = email.substring(0, at);
        String visible = name.substring(0, Math.min(2, name.length()));
        return visible + "*".repeat(Math.max(1, name.length() - visible.length())) + email.substring(at);
    }
}
