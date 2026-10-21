package com.hsf302.trainoffice.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class BookingSession implements Serializable {
    private Long trainTripId;
    private Long departureStationId;
    private Long arrivalStationId;
    private int passengerCount;
    private List<Long> seatIds = new ArrayList<>();
    private PassengerInfoForm passengerInfo;

    private Long selectedGroupDiscountPolicyId;
}