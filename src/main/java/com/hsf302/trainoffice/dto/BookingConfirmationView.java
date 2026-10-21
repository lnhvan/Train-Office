package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.entity.GroupDiscountPolicy;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class BookingConfirmationView {
    private BookingSession bookingSession;
    private PassengerInfoForm passengerInfo;
    private TrainTrip trip;
    private TripSegment segment;
    private List<Seat> selectedSeats;
    private BigDecimal totalAmount;

    private BookingPriceSummary priceSummary;
    private List<GroupDiscountPolicy> availableGroupDiscountPolicies;
}