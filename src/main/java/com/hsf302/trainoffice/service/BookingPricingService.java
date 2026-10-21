package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.BookingPriceSummary;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;

import java.math.BigDecimal;
import java.util.List;

public interface BookingPricingService {

    BigDecimal ticketPrice(TrainTrip trainTrip, Seat seat);

    BigDecimal ticketPrice(TrainTrip trainTrip, Seat seat, String passengerType);

    BigDecimal calculateTotal(TrainTrip trainTrip, List<Seat> seats);

    BigDecimal calculateTotal(TrainTrip trainTrip,
                              List<Seat> seats,
                              List<PassengerBookingRequest> passengers);

    BigDecimal calculateTotal(TrainTrip trainTrip,
                              List<Seat> seats,
                              List<PassengerBookingRequest> passengers,
                              Long groupDiscountPolicyId);

    BookingPriceSummary buildPriceSummary(TrainTrip trainTrip,
                                          List<Seat> seats,
                                          List<PassengerBookingRequest> passengers,
                                          Long groupDiscountPolicyId);
}