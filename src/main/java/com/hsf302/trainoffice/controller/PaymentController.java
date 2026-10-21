package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.PaymentMethod;
import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class PaymentController {

    private final PaymentService paymentService;
    private final BookingRepository bookingRepository;

    public PaymentController(PaymentService paymentService,
                             BookingRepository bookingRepository) {
        this.paymentService = paymentService;
        this.bookingRepository = bookingRepository;
    }

    // =========================================================
    // ADMIN PAYMENT MANAGEMENT
    // URL: /admin/payments
    // View folder: templates/payments/
    // =========================================================

    @GetMapping("/admin/payments")
    public String listPayments(
            Model model,
            @RequestParam(value = "status", required = false) PaymentStatus status,
            @RequestParam(value = "bookingId", required = false) Long bookingId
    ) {
        List<Payment> payments;

        if (bookingId != null) {
            payments = paymentService.getPaymentsByBookingId(bookingId);
        } else if (status != null) {
            payments = paymentService.getPaymentsByStatus(status);
        } else {
            payments = paymentService.getAllPayments();
        }

        model.addAttribute("payments", payments);
        model.addAttribute("paymentStatuses", PaymentStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedBookingId", bookingId);

        return "payments/list";
    }

    @GetMapping("/admin/payments/new")
    public String showCreateForm(
            Model model,
            @RequestParam(value = "bookingId", required = false) Long bookingId
    ) {
        Payment payment = new Payment();

        if (bookingId != null) {
            bookingRepository.findById(bookingId).ifPresent(payment::setBooking);
        }

        model.addAttribute("payment", payment);
        addCommonAttributes(model);

        return "payments/create";
    }

    @GetMapping("/admin/payments/edit/{id}")
    public String showEditForm(
            @PathVariable("id") Long id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Payment> paymentOpt = paymentService.getPaymentById(id);

        if (paymentOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy payment ID: " + id);
            return "redirect:/admin/payments";
        }

        model.addAttribute("payment", paymentOpt.get());
        addCommonAttributes(model);

        return "payments/edit";
    }

    @PostMapping("/admin/payments/save")
    public String savePayment(
            @Valid @ModelAttribute("payment") Payment payment,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes,
            @RequestParam(value = "bookingId", required = false) Long bookingId
    ) {
        if (bookingId != null) {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new RuntimeException("Booking not found with ID: " + bookingId));

            payment.setBooking(booking);
        }

        if (payment.getBooking() != null && payment.getBooking().getBookingId() != null) {
            Booking booking = bookingRepository.findById(payment.getBooking().getBookingId())
                    .orElseThrow(() -> new RuntimeException(
                            "Booking not found with ID: " + payment.getBooking().getBookingId()
                    ));

            payment.setBooking(booking);
        }

        if (result.hasErrors()) {
            addCommonAttributes(model);
            return payment.getPaymentId() == null ? "payments/create" : "payments/edit";
        }

        try {
            paymentService.savePayment(payment);
            redirectAttributes.addFlashAttribute("successMessage", "Payment đã được lưu thành công!");

            return "redirect:/admin/payments";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "Lỗi khi lưu payment: " + e.getMessage());
            addCommonAttributes(model);

            return payment.getPaymentId() == null ? "payments/create" : "payments/edit";
        }
    }

    @GetMapping("/admin/payments/detail/{id}")
    public String showPaymentDetail(
            @PathVariable("id") Long id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Payment> paymentOpt = paymentService.getPaymentById(id);

        if (paymentOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy payment ID: " + id);
            return "redirect:/admin/payments";
        }

        model.addAttribute("payment", paymentOpt.get());

        return "payments/detail";
    }

    @GetMapping("/admin/payments/success/{id}")
    public String markPaymentSuccess(
            @PathVariable("id") Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            paymentService.markPaymentSuccess(id);
            redirectAttributes.addFlashAttribute("successMessage", "Payment đã chuyển sang SUCCESS.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi cập nhật payment: " + e.getMessage());
        }

        return "redirect:/admin/payments";
    }

    @GetMapping("/admin/payments/failed/{id}")
    public String markPaymentFailed(
            @PathVariable("id") Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            paymentService.markPaymentFailed(id);
            redirectAttributes.addFlashAttribute("successMessage", "Payment đã chuyển sang FAILED.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi cập nhật payment: " + e.getMessage());
        }

        return "redirect:/admin/payments";
    }

    @GetMapping("/admin/payments/delete/{id}")
    public String deletePayment(
            @PathVariable("id") Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            paymentService.deletePayment(id);
            redirectAttributes.addFlashAttribute("successMessage", "Payment ID " + id + " đã được xoá.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi xoá payment: " + e.getMessage());
        }

        return "redirect:/admin/payments";
    }

    private void addCommonAttributes(Model model) {
        model.addAttribute("allBookings", bookingRepository.findAll());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("paymentStatuses", PaymentStatus.values());
    }

    // =========================================================
    // CUSTOMER VNPAY CHECKOUT
    // URL: /payments/bookings/{bookingId}
    // View folder: templates/payments/
    // =========================================================

    @GetMapping("/payments/bookings/{bookingId}")
    public String showCustomerCheckout(@PathVariable Long bookingId,
                                       HttpSession session,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        try {
            User user = currentUser(session);

            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "Please log in before payment.");
                return "redirect:/login";
            }

            Booking booking = paymentService.getBookingForPayment(bookingId);

            if (!canAccessBooking(booking, user)) {
                redirectAttributes.addFlashAttribute("errorMessage", "You cannot pay this booking.");
                return "redirect:/booking/history";
            }

            model.addAttribute("booking", booking);

            return "payments/checkout";

        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/booking/history";
        }
    }

    @PostMapping("/payments/bookings/{bookingId}")
    public String startCustomerPayment(@PathVariable Long bookingId,
                                       HttpServletRequest request,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        try {
            User user = currentUser(session);

            if (user == null) {
                redirectAttributes.addFlashAttribute("error", "Please log in before payment.");
                return "redirect:/login";
            }

            Booking booking = paymentService.getBookingForPayment(bookingId);

            if (!canAccessBooking(booking, user)) {
                redirectAttributes.addFlashAttribute("errorMessage", "You cannot pay this booking.");
                return "redirect:/booking/history";
            }

            String paymentUrl = paymentService.createVnpayPaymentUrl(
                    bookingId,
                    resolveClientIp(request)
            );

            return "redirect:" + paymentUrl;

        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/payments/bookings/" + bookingId;
        }
    }

    @GetMapping("/payments/vnpay-return")
    public String handleVnpayReturn(HttpServletRequest request, Model model) {
        Map<String, String> params = new HashMap<>();

        request.getParameterMap().forEach((key, value) -> {
            if (value != null && value.length > 0) {
                params.put(key, value[0]);
            }
        });

        try {
            Payment payment = paymentService.handleVnpayReturn(params);

            boolean success = payment.getPaymentStatus() == PaymentStatus.SUCCESS;

            model.addAttribute("payment", payment);
            model.addAttribute("booking", payment.getBooking());
            model.addAttribute("success", success);
            model.addAttribute("message", success
                    ? "Thanh toán thành công."
                    : "Thanh toán thất bại.");

            return "payments/result";

        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("success", false);
            model.addAttribute("message", ex.getMessage());

            return "payments/result";
        }
    }

    private User currentUser(HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");
        return sessionUser instanceof User user ? user : null;
    }

    private boolean canAccessBooking(Booking booking, User user) {
        if (booking == null || user == null) {
            return false;
        }

        if (booking.getUser() == null) {
            return false;
        }

        return booking.getUser().getUserId().equals(user.getUserId());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String remote = request.getRemoteAddr();

        return remote == null || remote.isBlank() ? "127.0.0.1" : remote;
    }
}