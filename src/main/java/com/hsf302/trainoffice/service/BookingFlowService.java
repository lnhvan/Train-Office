package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.BookingConfirmationView;
import com.hsf302.trainoffice.dto.BookingSession;
import com.hsf302.trainoffice.dto.CreateBookingRequest;
import com.hsf302.trainoffice.dto.PassengerInfoForm;
import com.hsf302.trainoffice.dto.SeatAvailabilityView;
import com.hsf302.trainoffice.dto.SeatSelectionForm;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.User;

import java.util.List;

public interface BookingFlowService {
    List<SeatAvailabilityView> getSeatAvailability(Long tripId, Long departureStationId, Long arrivalStationId);

    BookingSession buildBookingSession(SeatSelectionForm form);

    PassengerInfoForm createPassengerInfoForm(int passengerCount);

    PassengerInfoForm createPassengerInfoForm(int passengerCount, User user);

    void savePassengerInfo(BookingSession bookingSession, PassengerInfoForm form);

    void applyGroupDiscount(BookingSession bookingSession, Long groupDiscountPolicyId);

    BookingConfirmationView buildConfirmation(BookingSession bookingSession);

    CreateBookingRequest buildCreateBookingRequest(BookingSession bookingSession);

    Booking createBooking(BookingSession bookingSession, User user);
}