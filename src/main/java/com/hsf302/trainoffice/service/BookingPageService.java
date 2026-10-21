package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.PassengerInfoForm;
import com.hsf302.trainoffice.dto.SeatSelectionForm;
import com.hsf302.trainoffice.dto.TripSearchForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public interface BookingPageService {

    String showSearchPage(Model model);

    String showTripResults(TripSearchForm form,
                           BindingResult bindingResult,
                           Model model);

    String showSeatSelection(Long tripId,
                             Long departureStationId,
                             Long arrivalStationId,
                             int passengerCount,
                             HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes);

    String selectSeats(SeatSelectionForm form,
                       BindingResult bindingResult,
                       HttpSession session,
                       RedirectAttributes redirectAttributes);

    String showPassengerInfo(HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes);

    String savePassengerInfo(PassengerInfoForm form,
                             BindingResult bindingResult,
                             HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes);

    String showConfirmation(HttpSession session,
                            Model model,
                            RedirectAttributes redirectAttributes);

    String createBooking(HttpSession session,
                         RedirectAttributes redirectAttributes);

    String showSuccess(Long bookingId,
                       HttpSession session,
                       Model model,
                       RedirectAttributes redirectAttributes);

    String showHistory(String status,
                       HttpSession session,
                       Model model,
                       RedirectAttributes redirectAttributes);

    String showDetail(Long bookingId,
                      HttpSession session,
                      Model model,
                      RedirectAttributes redirectAttributes);

    String cancelBooking(Long bookingId,
                         HttpSession session,
                         RedirectAttributes redirectAttributes);

    String loginRequired(RedirectAttributes redirectAttributes);

    String applyGroupDiscount(Long groupDiscountPolicyId,
                              HttpSession session,
                              RedirectAttributes redirectAttributes);
}