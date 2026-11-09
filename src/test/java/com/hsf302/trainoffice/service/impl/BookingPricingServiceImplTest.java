package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.dto.BookingPriceSummary;
import com.hsf302.trainoffice.dto.FareBreakdownItem;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.entity.DiscountPolicy;
import com.hsf302.trainoffice.entity.Route;
import com.hsf302.trainoffice.entity.RouteStation;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.RouteStationRepository;
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
    private final RouteStationRepository routeStations = mock(RouteStationRepository.class);
    private final BookingPricingServiceImpl pricing = new BookingPricingServiceImpl(
            policies, mock(GroupDiscountPolicyService.class), routeStations);
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

        BookingPriceSummary summary = pricing.buildPriceSummary(trip, null, List.of(seat), List.of(passenger), null);

        assertEquals(new BigDecimal("75000"), pricing.ticketPrice(trip, null, seat, "CHILD"));
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

        BookingPriceSummary summary = pricing.buildPriceSummary(trip, null, List.of(seat), List.of(passenger), null);

        assertEquals(new BigDecimal("100000"), pricing.ticketPrice(trip, null, seat, "DEFAULT"));
        assertEquals(new BigDecimal("100000"), summary.getTotalAmount());
        assertEquals("Default", summary.getPassengerItems().get(0).getPassengerPolicyName());
        assertEquals(BigDecimal.ZERO, summary.getPassengerItems().get(0).getPassengerDiscountPercent());
        assertEquals(new BigDecimal("100000"), pricing.ticketPrice(trip, null, seat, "INACTIVE_OR_UNKNOWN"));
    }

    @Test
    void segmentFareIsProratedByDistanceRoundedAndHasMinimum() {
        Route route = Route.builder().routeId(5L).build();
        TrainTrip longTrip = TrainTrip.builder().basePrice(BigDecimal.valueOf(1_000_000)).route(route).build();
        RouteStation km0 = RouteStation.builder().distanceFromStartKm(0.0).build();
        RouteStation km31 = RouteStation.builder().distanceFromStartKm(31.0).build();
        RouteStation km142 = RouteStation.builder().distanceFromStartKm(142.0).build();
        RouteStation km1000 = RouteStation.builder().distanceFromStartKm(1000.0).build();
        when(routeStations.findByRoute_RouteIdOrderByStationOrderAsc(5L))
                .thenReturn(List.of(km0, km31, km142, km1000));

        // 1.000.000 × 142/1000 = 142.000
        assertEquals(new BigDecimal("142000"), pricing.segmentBaseFare(longTrip, segment(km0, km142)));
        // 1.000.000 × 31/1000 = 31.000 -> tối thiểu 50.000
        assertEquals(new BigDecimal("50000"), pricing.segmentBaseFare(longTrip, segment(km0, km31)));
        // đi hết tuyến -> đủ giá
        assertEquals(new BigDecimal("1000000"), pricing.segmentBaseFare(longTrip, segment(km0, km1000)));
    }

    @Test
    void groupDiscountIsSpreadOverTicketsAndSumsToBookingTotal() {
        BookingPriceSummary summary = new BookingPriceSummary(
                List.of(item(new BigDecimal("500000")), item(new BigDecimal("350000")), item(new BigDecimal("350000"))),
                new BigDecimal("1200000"), 1L, "Group", BigDecimal.TEN,
                new BigDecimal("120000"), new BigDecimal("1080000"));

        List<BigDecimal> paid = pricing.allocatePaidPrices(summary);

        assertEquals(List.of(new BigDecimal("450000"), new BigDecimal("315000"), new BigDecimal("315000")), paid);
        assertEquals(new BigDecimal("1080000"), paid.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private TripSegment segment(RouteStation from, RouteStation to) {
        TripStation departure = TripStation.builder().routeStation(from).build();
        TripStation arrival = TripStation.builder().routeStation(to).build();
        return new TripSegment(departure, arrival);
    }

    private FareBreakdownItem item(BigDecimal finalPrice) {
        return new FareBreakdownItem(1, "P", "DEFAULT", "Default", "C01", null, "1A", "STANDARD",
                null, null, null, finalPrice, BigDecimal.ZERO, BigDecimal.ZERO, finalPrice);
    }
}
