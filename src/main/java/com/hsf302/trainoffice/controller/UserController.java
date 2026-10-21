package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;
@Controller
@RequestMapping("/admin/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) UserRole role,
                            @RequestParam(required = false) UserStatus status,
                            Model model) {
        model.addAttribute("users", userService.searchUsers(keyword, role, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedStatus", status);
        addFormOptions(model);
        return "users/list";
    }

    @GetMapping({"/new", "/create"})
    public String showCreateForm(Model model) {
        User user = new User();
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        model.addAttribute("user", user);
        addFormOptions(model);

        return "users/form";
    }

    @GetMapping({"/edit/{id}", "/{id}/edit"})
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return userService.getUserById(id)
                .map(user -> {
                    model.addAttribute("user", user);
                    addFormOptions(model);
                    return "users/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
                    return "redirect:/admin/users";
                });
    }

    @GetMapping("/{id}")
    public String showDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return userService.getUserById(id)
                .map(user -> {
                    model.addAttribute("user", user);
                    return "users/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "User not found.");
                    return "redirect:/admin/users";
                });
    }

    @PostMapping("/save")
    public String saveUser(@Valid @ModelAttribute("user") User user,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormOptions(model);
            return "users/form";
        }

        try {
            userService.saveUser(user);
            redirectAttributes.addFlashAttribute("successMessage", "User saved successfully.");
            return "redirect:/admin/users";
        } catch (IllegalArgumentException ex) {
            // Nếu lỗi là email trùng
            if (ex.getMessage().toLowerCase().contains("email")) {
                bindingResult.rejectValue(
                        "email",
                        "duplicate",
                        ex.getMessage());
            } else {
                model.addAttribute("errorMessage", ex.getMessage());
            }
            addFormOptions(model);
            return "users/form";
        }

    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("successMessage", "User deleted successfully.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete user because it has related bookings.");
        }
        return "redirect:/admin/users";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("statuses", UserStatus.values());
    }
}
