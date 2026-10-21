package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.dto.BookingPriceSummary;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.entity.DiscountPolicy;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.service.DiscountPolicyService;
import com.hsf302.trainoffice.service.GroupDiscountPolicyService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingPricingServiceImplTest {

    private final DiscountPolicyService policies = mock(DiscountPolicyService.class);
    private final BookingPricingServiceImpl pricing = new BookingPricingServiceImpl(
            policies, mock(GroupDiscountPolicyService.class));
    private final TrainTrip trip = TrainTrip.builder().basePrice(BigDecimal.valueOf(100_000)).build();
    private final Seat seat = Seat.builder().seatNumber("1A").seatType("STANDARD")
            .extraPrice(BigDecimal.ZERO).build();

    @Test
    void matchingPolicyProducesConsistentDiscountSummaryAndTicketPrice() {
        DiscountPolicy child = DiscountPolicy.builder().policyCode("CHILD").policyName("Child")
                .discountPercent(BigDecimal.valueOf(25)).active(true).minAge(0).maxAge(15).build();
        when(policies.getActivePolicyByCode("CHILD")).thenReturn(Optional.of(child));
        PassengerBookingRequest passenger = new PassengerBookingRequest();
        passenger.setFullName("Child Passenger");
        passenger.setPassengerType("CHILD");

        BookingPriceSummary summary = pricing.buildPriceSummary(trip, List.of(seat), List.of(passenger), null);

        assertEquals(new BigDecimal("75000"), pricing.ticketPrice(trip, seat, "CHILD"));
        assertEquals(new BigDecimal("75000"), summary.getPassengerSubtotal());
        assertEquals(new BigDecimal("75000"), summary.getTotalAmount());
        assertEquals("Child", summary.getPassengerItems().get(0).getPassengerPolicyName());
        assertEquals(BigDecimal.valueOf(25), summary.getPassengerItems().get(0).getPassengerDiscountPercent());
    }

    @Test
    void defaultUnknownAndInactivePoliciesUseFullPriceAndDefaultBreakdown() {
        when(policies.getActivePolicyByCode(any())).thenReturn(Optional.empty());
        PassengerBookingRequest passenger = new PassengerBookingRequest();
        passenger.setFullName("Default Passenger");
        passenger.setPassengerType("DEFAULT");

        BookingPriceSummary summary = pricing.buildPriceSummary(trip, List.of(seat), List.of(passenger), null);

        assertEquals(new BigDecimal("100000"), pricing.ticketPrice(trip, seat, "DEFAULT"));
        assertEquals(new BigDecimal("100000"), summary.getTotalAmount());
        assertEquals("Default", summary.getPassengerItems().get(0).getPassengerPolicyName());
        assertEquals(BigDecimal.ZERO, summary.getPassengerItems().get(0).getPassengerDiscountPercent());
        assertEquals(new BigDecimal("100000"), pricing.ticketPrice(trip, seat, "INACTIVE_OR_UNKNOWN"));
    }
}
