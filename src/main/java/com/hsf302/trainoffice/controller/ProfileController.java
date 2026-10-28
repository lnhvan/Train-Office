package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.ChangePasswordRequest;
import com.hsf302.trainoffice.dto.ProfileForm;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.security.AuthCookie;
import com.hsf302.trainoffice.security.CurrentUser;
import com.hsf302.trainoffice.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final UserService userService;
    private final AuthCookie authCookie;

    public ProfileController(UserService userService, AuthCookie authCookie) {
        this.userService = userService;
        this.authCookie = authCookie;
    }

    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {
        User user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("currentUser", user);

        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", toForm(user));
        }

        return "customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileForm profileForm,
                                BindingResult bindingResult,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("currentUser", user);
            model.addAttribute("errorMessage", I18n.t("msg.profile.checkInfo"));
            return "customer/profile";
        }

        userService.updateProfile(user.getUserId(), profileForm);

        redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.profile.updated"));

        return "redirect:/profile";
    }

    @GetMapping("/change-password")
    public String showChangePassword(HttpSession session, Model model) {
        User user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("currentUser", user);

        if (!model.containsAttribute("changePasswordForm")) {
            model.addAttribute("changePasswordForm", new ChangePasswordRequest());
        }

        return "customer/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("changePasswordForm") ChangePasswordRequest request,
                                 BindingResult bindingResult,
                                 HttpSession session,
                                 HttpServletResponse response,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("currentUser", user);

        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", I18n.t("msg.password.fillAll"));
            return "customer/change-password";
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            model.addAttribute("errorMessage", I18n.t("msg.password.confirmMismatch"));
            return "customer/change-password";
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            model.addAttribute("errorMessage", I18n.t("msg.password.mustDiffer"));
            return "customer/change-password";
        }

        boolean success = userService.changePassword(
                user.getUserId(),
                request.getCurrentPassword(),
                request.getNewPassword()
        );

        if (!success) {
            model.addAttribute("errorMessage", I18n.t("msg.password.currentIncorrect"));
            return "customer/change-password";
        }

        // Đổi mật khẩu làm mọi JWT cũ hết hiệu lực (đăng xuất các thiết bị khác);
        // cấp JWT mới để thiết bị đang dùng vẫn đăng nhập.
        authCookie.signIn(response, userService.findById(user.getUserId()));

        redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.password.changed"));

        return "redirect:/change-password";
    }

    private User currentUser(HttpSession session) {
        return CurrentUser.get();
    }

    private ProfileForm toForm(User user) {
        ProfileForm form = new ProfileForm();
        form.setFullName(user.getFullName());
        form.setIdentityNumber(user.getIdentityNumber());
        form.setDateOfBirth(user.getDateOfBirth());
        form.setGender(user.getGender());
        return form;
    }
}