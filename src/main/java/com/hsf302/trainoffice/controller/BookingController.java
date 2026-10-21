package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.dto.PassengerInfoForm;
import com.hsf302.trainoffice.dto.SeatSelectionForm;
import com.hsf302.trainoffice.dto.TripSearchForm;
import com.hsf302.trainoffice.service.BookingPageService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BookingController {

    private final BookingPageService bookingPageService;

    public BookingController(BookingPageService bookingPageService) {
        this.bookingPageService = bookingPageService;
    }

    @GetMapping("/booking/search")
    public String search(Model model) {
        return bookingPageService.showSearchPage(model);
    }

    @GetMapping("/booking/trips")
    public String tripsGet(@Valid @ModelAttribute("tripSearchForm") TripSearchForm form,
                           BindingResult bindingResult,
                           Model model) {
        return bookingPageService.showTripResults(form, bindingResult, model);
    }

    @PostMapping("/booking/trips")
    public String tripsPost(@Valid @ModelAttribute("tripSearchForm") TripSearchForm form,
                            BindingResult bindingResult,
                            Model model) {
        return bookingPageService.showTripResults(form, bindingResult, model);
    }

    @GetMapping("/booking/login-required")
    public String loginRequired(RedirectAttributes redirectAttributes) {
        return bookingPageService.loginRequired(redirectAttributes);
    }

    @GetMapping("/booking/{tripId}/seats")
    public String seats(@PathVariable Long tripId,
                        @RequestParam Long departureStationId,
                        @RequestParam Long arrivalStationId,
                        @RequestParam(defaultValue = "1") int passengerCount,
                        HttpSession session,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        return bookingPageService.showSeatSelection(
                tripId,
                departureStationId,
                arrivalStationId,
                passengerCount,
                session,
                model,
                redirectAttributes
        );
    }

    @PostMapping("/booking/seat-selection")
    public String selectSeats(@Valid @ModelAttribute("seatSelectionForm") SeatSelectionForm form,
                              BindingResult bindingResult,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        return bookingPageService.selectSeats(
                form,
                bindingResult,
                session,
                redirectAttributes
        );
    }

    @GetMapping("/booking/passenger-info")
    public String passengerInfo(HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        return bookingPageService.showPassengerInfo(
                session,
                model,
                redirectAttributes
        );
    }

    @PostMapping("/booking/passenger-info")
    public String savePassengerInfo(@Valid @ModelAttribute("passengerInfoForm") PassengerInfoForm form,
                                    BindingResult bindingResult,
                                    HttpSession session,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        return bookingPageService.savePassengerInfo(
                form,
                bindingResult,
                session,
                model,
                redirectAttributes
        );
    }

    @GetMapping("/booking/confirmation")
    public String confirmation(HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        return bookingPageService.showConfirmation(
                session,
                model,
                redirectAttributes
        );
    }

    @PostMapping("/booking/confirmation")
    public String createBooking(HttpSession session,
                                RedirectAttributes redirectAttributes) {
        return bookingPageService.createBooking(
                session,
                redirectAttributes
        );
    }

    @GetMapping("/booking/success")
    public String success(@RequestParam(required = false) Long bookingId,
                          HttpSession session,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        return bookingPageService.showSuccess(
                bookingId,
                session,
                model,
                redirectAttributes
        );
    }

    @GetMapping("/booking/history")
    public String history(@RequestParam(value = "status", required = false) String status,
                          HttpSession session,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        return bookingPageService.showHistory(
                status,
                session,
                model,
                redirectAttributes
        );
    }

    @GetMapping("/booking/{bookingId}")
    public String detail(@PathVariable Long bookingId,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return bookingPageService.showDetail(
                bookingId,
                session,
                model,
                redirectAttributes
        );
    }

    @PostMapping("/booking/{bookingId}/cancel")
    public String cancel(@PathVariable Long bookingId,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        return bookingPageService.cancelBooking(
                bookingId,
                session,
                redirectAttributes
        );
    }

    @PostMapping("/booking/group-discount")
    public String applyGroupDiscount(@RequestParam(value = "groupDiscountPolicyId", required = false) Long groupDiscountPolicyId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        return bookingPageService.applyGroupDiscount(
                groupDiscountPolicyId,
                session,
                redirectAttributes
        );
    }
}