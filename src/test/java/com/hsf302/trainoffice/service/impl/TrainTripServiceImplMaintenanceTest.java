package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.TrainStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TrainTripServiceImplMaintenanceTest {

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
    }

    @Test
    void saveTripWithMaintenanceTrainThrowsException() {
        Train train = new Train();
        train.setTrainId(1L);
        train.setStatus(TrainStatus.MAINTENANCE);

        Route route = new Route();
        route.setRouteId(2L);

        when(trainRepository.findById(1L)).thenReturn(Optional.of(train));
        when(routeRepository.findById(2L)).thenReturn(Optional.of(route));

        TrainTrip trip = new TrainTrip();
        trip.setTrain(train);
        trip.setRoute(route);
        trip.setDepartureTime(LocalDateTime.now().plusDays(1));
        trip.setBasePrice(BigDecimal.valueOf(100000));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.saveTrip(trip));
        assertTrue(ex.getMessage().contains("err.trainUnderMaintenanceCannotCreateTrip")
                || ex.getMessage().contains("bảo trì"));
    }

    @Test
    void previewScheduleWithMaintenanceTrainThrowsException() {
        Train train = new Train();
        train.setTrainId(1L);
        train.setStatus(TrainStatus.MAINTENANCE);

        when(trainRepository.findById(1L)).thenReturn(Optional.of(train));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.previewSchedule(1L, 2L, LocalDateTime.now().plusDays(1)));
        assertTrue(ex.getMessage().contains("err.trainUnderMaintenanceCannotCreateTrip")
                || ex.getMessage().contains("bảo trì"));
    }
}
