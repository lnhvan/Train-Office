package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.TrainStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.TrainTimelineDto;
import com.hsf302.trainoffice.dto.TripSchedulePreview;
import com.hsf302.trainoffice.dto.TripTimelineItemDto;
import com.hsf302.trainoffice.entity.Route;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.repository.*;
import com.hsf302.trainoffice.service.BookingPricingService;
import com.hsf302.trainoffice.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainTripServiceImplScheduleValidationTest {

    private final TrainTripRepository trainTripRepository = mock(TrainTripRepository.class);
    private final TripStationRepository tripStationRepository = mock(TripStationRepository.class);
    private final TrainRepository trainRepository = mock(TrainRepository.class);
    private final RouteRepository routeRepository = mock(RouteRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final TripScheduleBuilder tripScheduleBuilder = mock(TripScheduleBuilder.class);
    private final BookingPricingService bookingPricingService = mock(BookingPricingService.class);
    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final RefundService refundService = mock(RefundService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private TrainTripServiceImpl service;
    private Train train;
    private Route route;

    @BeforeEach
    void setUp() {
        service = new TrainTripServiceImpl(
                trainTripRepository,
                tripStationRepository,
                trainRepository,
                routeRepository,
                bookingRepository,
                tripScheduleBuilder,
                bookingPricingService,
                ticketRepository,
                refundService,
                eventPublisher
        );

        train = new Train();
        train.setTrainId(10L);
        train.setTrainCode("SE1");
        train.setTrainName("SE1 Express");
        train.setStatus(TrainStatus.AVAILABLE);
        train.setAverageSpeedKmh(60);

        route = new Route();
        route.setRouteId(20L);
        route.setRouteCode("HN-SG");
        route.setRouteName("Hà Nội - Sài Gòn");

        when(trainRepository.findById(10L)).thenReturn(Optional.of(train));
        when(routeRepository.findById(20L)).thenReturn(Optional.of(route));
        when(trainTripRepository.save(any(TrainTrip.class))).thenAnswer(inv -> {
            TrainTrip t = inv.getArgument(0);
            if (t.getTripId() == null) {
                t.setTripId(999L);
            }
            return t;
        });
    }

    @Test
    void saveTripFailsWhenDepartureBeforeLatestTripArrival() {
        LocalDateTime trip1Dep = LocalDateTime.of(2026, 11, 10, 8, 0);
        LocalDateTime trip1Arr = LocalDateTime.of(2026, 11, 10, 16, 0);

        TrainTrip existingTrip = new TrainTrip();
        existingTrip.setTripId(101L);
        existingTrip.setTrain(train);
        existingTrip.setRoute(route);
        existingTrip.setStatus(TripStatus.SCHEDULED);
        existingTrip.setDepartureTime(trip1Dep);
        existingTrip.setArrivalTime(trip1Arr);

        when(trainTripRepository.findByTrain_TrainIdAndStatusInOrderByDepartureTimeAsc(
                eq(10L), anyCollection()))
                .thenReturn(List.of(existingTrip));

        // Create new trip with departure 14:00 (before existing trip ends at 16:00)
        TrainTrip newTrip = new TrainTrip();
        newTrip.setTrain(train);
        newTrip.setRoute(route);
        newTrip.setDepartureTime(LocalDateTime.of(2026, 11, 10, 14, 0));
        newTrip.setBasePrice(BigDecimal.valueOf(500000));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.saveTrip(newTrip));
        assertTrue(ex.getMessage().contains("err.tripDepartureMustBeAfterLatest")
                || ex.getMessage().contains("sau giờ kết thúc của chuyến cuối cùng"));
    }

    @Test
    void saveTripFailsWhenOverlappingWithExistingTrip() {
        LocalDateTime trip1Dep = LocalDateTime.of(2026, 11, 10, 8, 0);
        LocalDateTime trip1Arr = LocalDateTime.of(2026, 11, 10, 16, 0);

        TrainTrip existingTrip = new TrainTrip();
        existingTrip.setTripId(101L);
        existingTrip.setTrain(train);
        existingTrip.setRoute(route);
        existingTrip.setStatus(TripStatus.ONGOING);
        existingTrip.setDepartureTime(trip1Dep);
        existingTrip.setArrivalTime(trip1Arr);

        when(trainTripRepository.findByTrain_TrainIdAndStatusInOrderByDepartureTimeAsc(
                eq(10L), anyCollection()))
                .thenReturn(List.of(existingTrip));

        // Attempting to save a trip departing at 10:00 (inside 8:00 - 16:00)
        TrainTrip newTrip = new TrainTrip();
        newTrip.setTrain(train);
        newTrip.setRoute(route);
        newTrip.setDepartureTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        newTrip.setBasePrice(BigDecimal.valueOf(500000));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.saveTrip(newTrip));
        assertTrue(ex.getMessage().contains("err.tripOverlapExistingTrip")
                || ex.getMessage().contains("err.tripDepartureMustBeAfterLatest")
                || ex.getMessage().contains("trùng với khung giờ"));
    }

    @Test
    void saveTripSucceedsWhenDepartureAfterLatestTripArrival() {
        LocalDateTime trip1Dep = LocalDateTime.of(2026, 11, 10, 8, 0);
        LocalDateTime trip1Arr = LocalDateTime.of(2026, 11, 10, 16, 0);

        TrainTrip existingTrip = new TrainTrip();
        existingTrip.setTripId(101L);
        existingTrip.setTrain(train);
        existingTrip.setRoute(route);
        existingTrip.setStatus(TripStatus.SCHEDULED);
        existingTrip.setDepartureTime(trip1Dep);
        existingTrip.setArrivalTime(trip1Arr);

        when(trainTripRepository.findByTrain_TrainIdAndStatusInOrderByDepartureTimeAsc(
                eq(10L), anyCollection()))
                .thenReturn(List.of(existingTrip));

        // Create new trip with departure 18:00 (after 16:00)
        TrainTrip newTrip = new TrainTrip();
        newTrip.setTrain(train);
        newTrip.setRoute(route);
        newTrip.setDepartureTime(LocalDateTime.of(2026, 11, 10, 18, 0));
        newTrip.setBasePrice(BigDecimal.valueOf(500000));

        TrainTrip result = service.saveTrip(newTrip);
        assertNotNull(result);
        assertEquals(999L, result.getTripId());
    }

    @Test
    void getTrainTimelineReturnsCorrectDto() {
        LocalDateTime trip1Dep = LocalDateTime.of(2026, 11, 10, 8, 0);
        LocalDateTime trip1Arr = LocalDateTime.of(2026, 11, 10, 16, 0);

        TrainTrip existingTrip = new TrainTrip();
        existingTrip.setTripId(101L);
        existingTrip.setTrain(train);
        existingTrip.setRoute(route);
        existingTrip.setStatus(TripStatus.ONGOING);
        existingTrip.setDepartureTime(trip1Dep);
        existingTrip.setArrivalTime(trip1Arr);

        when(trainTripRepository.findByTrain_TrainIdAndStatusInOrderByDepartureTimeAsc(
                eq(10L), anyCollection()))
                .thenReturn(List.of(existingTrip));

        TrainTimelineDto timeline = service.getTrainTimeline(10L);
        assertNotNull(timeline);
        assertTrue(timeline.hasActiveTrips());
        assertEquals("SE1", timeline.trainCode());
        assertEquals(101L, timeline.latestTripId());
        assertEquals("2026-11-10T16:00", timeline.latestArrivalTimeIso());
        assertEquals(1, timeline.trips().size());
    }

    @Test
    void getFleetTimelineReturnsAllActiveTrips() {
        TrainTrip trip1 = new TrainTrip();
        trip1.setTripId(1L);
        trip1.setTrain(train);
        trip1.setRoute(route);
        trip1.setStatus(TripStatus.SCHEDULED);
        trip1.setDepartureTime(LocalDateTime.now().plusHours(2));
        trip1.setArrivalTime(LocalDateTime.now().plusHours(10));

        when(trainTripRepository.findByStatusInOrderByDepartureTimeAsc(anyCollection()))
                .thenReturn(List.of(trip1));

        List<TripTimelineItemDto> fleetTimeline = service.getFleetTimeline(null);
        assertNotNull(fleetTimeline);
        assertEquals(1, fleetTimeline.size());
        assertEquals(1L, fleetTimeline.get(0).tripId());
        assertEquals("SE1", fleetTimeline.get(0).trainCode());
    }
}
