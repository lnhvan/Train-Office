package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.CreateBookingRequest;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Passenger;
import com.hsf302.trainoffice.entity.User;

import java.util.List;

public interface BookingService {
    Booking createBookingForUser(CreateBookingRequest request, User user);

    Booking createBookingForGuest(CreateBookingRequest request);

    Booking getBookingById(Long bookingId);

    List<Booking> getBookingsForUser(User user);

    List<Booking> findGuestBookings(String email, String phone);

    List<Passenger> getPassengersForBooking(Long bookingId);

    Booking cancelPendingBooking(Long bookingId, User user);

    Booking cancelPendingGuestBooking(Long bookingId);
    int expirePendingBookingsOlderThan(int minutes);
}
