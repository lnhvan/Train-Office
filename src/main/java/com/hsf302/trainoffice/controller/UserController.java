package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.service.UserService;
import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public UserController(UserService userService,
                          BookingRepository bookingRepository,
                          PaymentRepository paymentRepository) {
        this.userService = userService;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
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
                    redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.user.notFound"));
                    return "redirect:/admin/users";
                });
    }

    @GetMapping("/{id}")
    public String showDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return userService.getUserById(id)
                .map(user -> {
                    model.addAttribute("user", user);

                    String email = (user.getEmail() != null && !user.getEmail().isBlank()) ? user.getEmail().trim() : null;
                    List<Booking> bookings = bookingRepository.findByUserOrEmailOrderByBookingDateDesc(id, email);
                    List<Payment> payments = paymentRepository.findByUserOrEmail(id, email);

                    // Sắp xếp payments mới nhất lên đầu
                    payments.sort((p1, p2) -> {
                        LocalDateTime d1 = p1.getPaidAt() != null ? p1.getPaidAt() : (p1.getBooking() != null ? p1.getBooking().getBookingDate() : null);
                        LocalDateTime d2 = p2.getPaidAt() != null ? p2.getPaidAt() : (p2.getBooking() != null ? p2.getBooking().getBookingDate() : null);
                        if (d1 == null && d2 == null) return 0;
                        if (d1 == null) return 1;
                        if (d2 == null) return -1;
                        return d2.compareTo(d1);
                    });

                    long paidBookingsCount = bookings.stream()
                            .filter(b -> b.getBookingStatus() == BookingStatus.PAID)
                            .count();
                    BigDecimal totalSpent = payments.stream()
                            .filter(p -> p.getPaymentStatus() == PaymentStatus.SUCCESS && p.getAmount() != null)
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    model.addAttribute("bookings", bookings);
                    model.addAttribute("payments", payments);
                    model.addAttribute("paidBookingsCount", paidBookingsCount);
                    model.addAttribute("totalSpent", totalSpent);

                    return "users/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.user.notFound"));
                    return "redirect:/admin/users";
                });
    }

    @PostMapping("/save")
    public String saveUser(@ModelAttribute("user") User user,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        if (user.getUserId() != null) {
            // Sửa tài khoản: Admin chỉ có thể thay đổi vai trò (role) và trạng thái (status)
            if (user.getRole() == null || user.getStatus() == null) {
                if (user.getRole() == null) bindingResult.rejectValue("role", "required", "Role is required");
                if (user.getStatus() == null) bindingResult.rejectValue("status", "required", "Status is required");
                addFormOptions(model);
                return "users/form";
            }

            try {
                userService.saveUser(user);
                redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.user.saved"));
                return "redirect:/admin/users";
            } catch (Exception ex) {
                model.addAttribute("errorMessage", ex.getMessage());
                addFormOptions(model);
                return "users/form";
            }
        }

        // Tạo tài khoản mới: kiểm tra đầy đủ các trường
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            bindingResult.rejectValue("password", "required", I18n.t("err.passwordIsRequired"));
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            bindingResult.rejectValue("email", "required", I18n.t("err.emailIsRequired"));
        }
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            bindingResult.rejectValue("fullName", "required", I18n.t("validation.fullName.required"));
        }

        if (bindingResult.hasErrors()) {
            addFormOptions(model);
            return "users/form";
        }

        try {
            userService.saveUser(user);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.user.saved"));
            return "redirect:/admin/users";
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage().toLowerCase().contains("email")) {
                bindingResult.rejectValue("email", "duplicate", ex.getMessage());
            } else {
                model.addAttribute("errorMessage", ex.getMessage());
            }
            addFormOptions(model);
            return "users/form";
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.user.deleted"));
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.user.inUse"));
        }
        return "redirect:/admin/users";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("statuses", UserStatus.values());
    }
}
