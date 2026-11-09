package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.security.CurrentUser;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.PaymentMethod;
import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.service.PaymentService;
import com.hsf302.trainoffice.entity.Passenger;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.service.BookingService;
import com.hsf302.trainoffice.service.TicketService;
import com.hsf302.trainoffice.common.enums.Gender;
import com.hsf302.trainoffice.service.PassengerService;
import com.hsf302.trainoffice.service.SeatHoldService;
import com.hsf302.trainoffice.dto.SeatHold;
import com.hsf302.trainoffice.exception.SeatHoldException;
import com.hsf302.trainoffice.common.enums.BookingStatus;
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
import java.util.UUID;

@Controller
public class PaymentController {

    private final PaymentService paymentService;
    private final BookingRepository bookingRepository;
    private final SeatHoldService seatHoldService;
    private final BookingService bookingService;
    private final TicketService ticketService;
    private final PassengerService passengerService;

    public PaymentController(PaymentService paymentService,
                             BookingRepository bookingRepository,
                             SeatHoldService seatHoldService,
                             BookingService bookingService,
                             TicketService ticketService,
                             PassengerService passengerService) {
        this.paymentService = paymentService;
        this.bookingRepository = bookingRepository;
        this.seatHoldService = seatHoldService;
        this.bookingService = bookingService;
        this.ticketService = ticketService;
        this.passengerService = passengerService;
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
            @RequestParam(value = "bookingId", required = false) Long bookingId,
            @RequestParam(value = "bookingStatus", required = false) BookingStatus bookingStatus,
            @RequestParam(value = "bookingKeyword", required = false) String bookingKeyword,
            @RequestParam(value = "tab", required = false, defaultValue = "payments") String activeTab
    ) {
        List<Payment> payments;

        if (bookingId != null) {
            payments = paymentService.getPaymentsByBookingId(bookingId);
        } else if (status != null) {
            payments = paymentService.getPaymentsByStatus(status);
        } else {
            payments = paymentService.getAllPayments();
        }

        String kw = (bookingKeyword != null && !bookingKeyword.isBlank()) ? bookingKeyword.trim() : null;
        List<Booking> bookings = bookingRepository.searchBookingsAdmin(bookingStatus, kw);

        model.addAttribute("payments", payments);
        model.addAttribute("paymentStatuses", PaymentStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedBookingId", bookingId);

        model.addAttribute("bookings", bookings);
        model.addAttribute("bookingStatuses", BookingStatus.values());
        model.addAttribute("selectedBookingStatus", bookingStatus);
        model.addAttribute("bookingKeyword", bookingKeyword);
        model.addAttribute("activeTab", "bookings".equalsIgnoreCase(activeTab) ? "bookings" : "payments");

        return "payments/list";
    }

    @GetMapping({"/admin/bookings/{id}", "/admin/payments/bookings/{id}"})
    public String showAdminBookingDetail(
            @PathVariable("id") Long bookingId,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Booking booking = bookingService.getBookingById(bookingId);
            List<Passenger> passengers = bookingService.getPassengersForBooking(bookingId);
            List<Ticket> tickets = ticketService.getTicketsByBookingId(bookingId);
            List<Payment> payments = paymentService.getPaymentsByBookingId(bookingId);

            model.addAttribute("booking", booking);
            model.addAttribute("passengers", passengers);
            model.addAttribute("tickets", tickets);
            model.addAttribute("payments", payments);
            model.addAttribute("genders", Gender.values());

            String safeReturnUrl = resolveSafeReturnUrl(returnUrl, request, "/admin/payments?tab=bookings");
            model.addAttribute("returnUrl", safeReturnUrl);

            return "payments/booking-detail";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/payments?tab=bookings";
        }
    }

    @PostMapping("/admin/bookings/{bookingId}/passengers/{passengerId}/edit")
    public String updateBookingPassenger(
            @PathVariable Long bookingId,
            @PathVariable Long passengerId,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            @ModelAttribute Passenger passengerForm,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Passenger existing = passengerService.getPassengerById(passengerId)
                    .orElseThrow(() -> new IllegalArgumentException(I18n.t("msg.passenger.notFound")));

            if (passengerForm.getFullName() == null || passengerForm.getFullName().isBlank()) {
                throw new IllegalArgumentException(I18n.t("err.passengerFullNameIsRequired2"));
            }

            existing.setFullName(passengerForm.getFullName().trim());
            existing.setIdentityNumber(passengerForm.getIdentityNumber() != null && !passengerForm.getIdentityNumber().isBlank()
                    ? passengerForm.getIdentityNumber().trim() : null);
            existing.setDateOfBirth(passengerForm.getDateOfBirth());
            existing.setGender(passengerForm.getGender());
            if (passengerForm.getPassengerType() != null && !passengerForm.getPassengerType().isBlank()) {
                existing.setPassengerType(passengerForm.getPassengerType().trim());
            }
            existing.setRelationshipToBooker(passengerForm.getRelationshipToBooker() != null && !passengerForm.getRelationshipToBooker().isBlank()
                    ? passengerForm.getRelationshipToBooker().trim() : null);

            passengerService.savePassenger(existing);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.passenger.saved"));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        if (returnUrl != null && !returnUrl.isBlank() && returnUrl.startsWith("/") && !returnUrl.startsWith("//")) {
            redirectAttributes.addAttribute("returnUrl", returnUrl);
        }
        return "redirect:/admin/bookings/" + bookingId;
    }

    @GetMapping("/admin/payments/edit/{id}")
    public String showEditForm(
            @PathVariable("id") UUID id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Payment> paymentOpt = paymentService.getPaymentById(id);

        if (paymentOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.payment.notFound", id));
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
        if (payment.getPaymentId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.payment.manualCreateDisabled"));
            return "redirect:/admin/payments";
        }
        if (bookingId != null) {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new RuntimeException(I18n.t("msg.booking.notFoundId", bookingId)));

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
            return "payments/edit";
        }

        try {
            paymentService.savePayment(payment);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.payment.saved"));

            return "redirect:/admin/payments";

        } catch (Exception e) {
            model.addAttribute("errorMessage", I18n.t("msg.payment.saveError", e.getMessage()));
            addCommonAttributes(model);

            return "payments/edit";
        }
    }

    @GetMapping("/admin/payments/detail/{id}")
    public String showPaymentDetail(
            @PathVariable("id") UUID id,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Optional<Payment> paymentOpt = paymentService.getPaymentById(id);

        if (paymentOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.payment.notFound", id));
            return "redirect:/admin/payments";
        }

        model.addAttribute("payment", paymentOpt.get());
        String safeReturnUrl = resolveSafeReturnUrl(returnUrl, request, "/admin/payments");
        model.addAttribute("returnUrl", safeReturnUrl);

        return "payments/detail";
    }

    @PostMapping("/admin/payments/failed/{id}")
    public String markPaymentFailed(
            @PathVariable("id") UUID id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            paymentService.markPaymentFailed(id);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.payment.markedFailed"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.payment.updateError", e.getMessage()));
        }

        return "redirect:/admin/payments";
    }

    @PostMapping("/admin/payments/delete/{id}")
    public String deletePayment(
            @PathVariable("id") UUID id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            paymentService.deletePayment(id);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.payment.deleted", id));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.payment.deleteError", e.getMessage()));
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
                redirectAttributes.addFlashAttribute("error", I18n.t("msg.payment.loginRequired"));
                return "redirect:/login";
            }

            Booking booking = paymentService.getBookingForPayment(bookingId);

            if (!canAccessBooking(booking, user)) {
                redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.payment.notAllowed"));
                return "redirect:/booking/history";
            }

            model.addAttribute("booking", booking);

            // Thời gian giữ ghế còn lại của đơn (đọc từ Redis) cho đồng hồ trên trang thanh toán.
            long remainingMs = 0L;
            if (booking.getBookingStatus() == BookingStatus.PENDING_PAYMENT) {
                try {
                    remainingMs = seatHoldService.findBookingHold(bookingId)
                            .map(SeatHold::remainingMillis)
                            .orElse(0L);
                } catch (SeatHoldException ex) {
                    model.addAttribute("errorMessage", ex.getMessage());
                }
            }
            model.addAttribute("seatHoldRemainingMs", remainingMs);

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
                redirectAttributes.addFlashAttribute("error", I18n.t("msg.payment.loginRequired"));
                return "redirect:/login";
            }

            Booking booking = paymentService.getBookingForPayment(bookingId);

            if (!canAccessBooking(booking, user)) {
                redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.payment.notAllowed"));
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
                    ? I18n.t("msg.payment.success")
                    : I18n.t("msg.payment.failed"));

            return "payments/result";

        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("success", false);
            model.addAttribute("message", ex.getMessage());

            return "payments/result";
        }
    }

    /** Người dùng đăng nhập (JWT) của request hiện tại; null nếu chưa đăng nhập. */
    private User currentUser(HttpSession session) {
        return CurrentUser.get();
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

    private String resolveSafeReturnUrl(String returnUrl, HttpServletRequest request, String fallbackUrl) {
        if (returnUrl != null && !returnUrl.isBlank() && returnUrl.startsWith("/") && !returnUrl.startsWith("//")) {
            return returnUrl;
        }
        if (request != null) {
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                try {
                    java.net.URI uri = java.net.URI.create(referer);
                    String path = uri.getPath();
                    String query = uri.getQuery();
                    String relative = path + (query != null ? "?" + query : "");
                    if (relative.startsWith("/admin/")
                            && !relative.contains("/admin/bookings/")
                            && !relative.contains("/admin/payments/bookings/")) {
                        return relative;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return fallbackUrl;
    }

}
