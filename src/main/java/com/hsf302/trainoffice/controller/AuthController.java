package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.dto.ForgotPasswordRequest;
import com.hsf302.trainoffice.dto.RegisterRequest;
import com.hsf302.trainoffice.dto.ResetPassword;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.UserRepository;
import com.hsf302.trainoffice.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;

    //Login !!!!! ======================
    @GetMapping("/login")
    public String showLogin() {
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam("email") String email,
                        @RequestParam("password") String pwd,
                        Model model,
                        HttpSession session) {
        User user = userService.login(email, pwd);

        if (user == null) {
            model.addAttribute("error", "Email or password is incorrect, or the account is not active.");
            return "auth/login";
        }

        session.setAttribute("currentUser", user);

        if (user.getRole().name().equalsIgnoreCase("ADMIN")) {
            return "redirect:/admin/dashboard";
        }

        if (user.getRole().name().equalsIgnoreCase("CUSTOMER")) {
            return "redirect:/booking/search";
        }
        return "auth/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/register")
    public String showRegister(Model model) {

        model.addAttribute(
                "registerRequest",
                new RegisterRequest());

        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest registerRequest,
                           BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        if (!registerRequest.getPassword()
                .equals(registerRequest.getConfirmPassword())) {

            bindingResult.rejectValue(
                    "confirmPassword",
                    "error.confirmPassword",
                    "Mật khẩu xác nhận không khớp");

            return "auth/register";
        }

        if (userService.existsByEmail(registerRequest.getEmail())) {

            bindingResult.rejectValue(
                    "email",
                    "error.email",
                    "Email đã tồn tại");

            return "auth/register";
        }

        userService.register(registerRequest);

        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String showForgotPassword(Model model){

        model.addAttribute(
                "forgotPasswordForm",
                new ForgotPasswordRequest());

        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(
            @Valid
            @ModelAttribute("forgotPasswordForm")
            ForgotPasswordRequest request,

            BindingResult bindingResult,

            HttpSession session,

            Model model){

        if(bindingResult.hasErrors()){
            return "auth/forgot-password";
        }

        User user = userService.findByEmail(request.getEmail());

        if(user == null){
            model.addAttribute(
                    "errorMessage",
                    "Email does not exist.");

            return "auth/forgot-password";
        }

        session.setAttribute(
                "resetEmail",
                user.getEmail());

        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String showResetPassword(
            HttpSession session,
            Model model){

        if(session.getAttribute("resetEmail")==null){
            return "redirect:/forgot-password";
        }

        model.addAttribute(
                "resetPasswordForm",
                new ResetPassword());

        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @Valid
            @ModelAttribute("resetPasswordForm")
            ResetPassword request,

            BindingResult bindingResult,

            HttpSession session,

            Model model,

            RedirectAttributes redirectAttributes){

        if(bindingResult.hasErrors()){

            return "auth/reset-password";
        }

        if(!request.getPassword()
                .equals(request.getConfirmPassword())){

            bindingResult.rejectValue(
                    "confirmPassword",
                    "error.confirmPassword",
                    "Confirm password does not match.");

            return "auth/reset-password";
        }

        String email =
                (String)session.getAttribute("resetEmail");

        userService.resetPassword(
                email,
                request.getPassword());

        session.removeAttribute("resetEmail");

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Password has been reset successfully. Please login with your new password.");

        return "redirect:/login";
    }
}
