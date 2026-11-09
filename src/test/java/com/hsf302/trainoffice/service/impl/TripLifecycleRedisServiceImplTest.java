package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TripLifecycleRedisServiceImplTest {

    private final TrainTripRepository trainTripRepository = mock(TrainTripRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private TripLifecycleRedisServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new TripLifecycleRedisServiceImpl(trainTripRepository, redisTemplate);
    }

    @Test
    void pastArrivalTripBecomesCompleted() {
        TrainTrip trip = new TrainTrip();
        trip.setTripId(1L);
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setDepartureTime(LocalDateTime.now().minusHours(5));
        trip.setArrivalTime(LocalDateTime.now().minusHours(1));

        service.scheduleTrip(trip);

        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        verify(trainTripRepository).save(trip);
        verify(redisTemplate).delete("trip:status:scheduled:1");
        verify(redisTemplate).delete("trip:status:ongoing:1");
    }

    @Test
    void currentlyRunningTripBecomesOngoingAndSetsRedisKey() {
        TrainTrip trip = new TrainTrip();
        trip.setTripId(2L);
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setDepartureTime(LocalDateTime.now().minusMinutes(30));
        trip.setArrivalTime(LocalDateTime.now().plusHours(2));

        service.scheduleTrip(trip);

        assertEquals(TripStatus.ONGOING, trip.getStatus());
        verify(trainTripRepository).save(trip);
        verify(valueOperations).set(
                eq("trip:status:ongoing:2"),
                eq("2"),
                longThat(ttl -> ttl > 0),
                eq(TimeUnit.SECONDS)
        );
        verify(redisTemplate).delete("trip:status:scheduled:2");
    }

    @Test
    void futureTripBecomesScheduledAndSetsRedisKey() {
        TrainTrip trip = new TrainTrip();
        trip.setTripId(3L);
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setDepartureTime(LocalDateTime.now().plusHours(3));
        trip.setArrivalTime(LocalDateTime.now().plusHours(7));

        service.scheduleTrip(trip);

        assertEquals(TripStatus.SCHEDULED, trip.getStatus());
        verify(valueOperations).set(
                eq("trip:status:scheduled:3"),
                eq("3"),
                longThat(ttl -> ttl > 0),
                eq(TimeUnit.SECONDS)
        );
        verify(redisTemplate).delete("trip:status:ongoing:3");
    }

    @Test
    void expiredScheduledKeyTransitionsTripToOngoing() {
        TrainTrip trip = new TrainTrip();
        trip.setTripId(10L);
        trip.setStatus(TripStatus.SCHEDULED);
        trip.setDepartureTime(LocalDateTime.now().minusSeconds(1));
        trip.setArrivalTime(LocalDateTime.now().plusHours(4));

        when(trainTripRepository.findById(10L)).thenReturn(Optional.of(trip));

        service.handleExpiredKey("trip:status:scheduled:10");

        assertEquals(TripStatus.ONGOING, trip.getStatus());
        verify(trainTripRepository).save(trip);
        verify(valueOperations).set(
                eq("trip:status:ongoing:10"),
                eq("10"),
                longThat(ttl -> ttl > 0),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    void expiredOngoingKeyTransitionsTripToCompleted() {
        TrainTrip trip = new TrainTrip();
        trip.setTripId(20L);
        trip.setStatus(TripStatus.ONGOING);
        trip.setDepartureTime(LocalDateTime.now().minusHours(3));
        trip.setArrivalTime(LocalDateTime.now().minusSeconds(1));

        when(trainTripRepository.findById(20L)).thenReturn(Optional.of(trip));

        service.handleExpiredKey("trip:status:ongoing:20");

        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        verify(trainTripRepository).save(trip);
    }

    @Test
    void scanAndSyncAllTripsOnStartupProcessesAllActiveTrips() {
        TrainTrip pastTrip = new TrainTrip();
        pastTrip.setTripId(101L);
        pastTrip.setStatus(TripStatus.SCHEDULED);
        pastTrip.setDepartureTime(LocalDateTime.now().minusHours(10));
        pastTrip.setArrivalTime(LocalDateTime.now().minusHours(5));

        TrainTrip ongoingTrip = new TrainTrip();
        ongoingTrip.setTripId(102L);
        ongoingTrip.setStatus(TripStatus.SCHEDULED);
        ongoingTrip.setDepartureTime(LocalDateTime.now().minusMinutes(10));
        ongoingTrip.setArrivalTime(LocalDateTime.now().plusHours(1));

        when(trainTripRepository.findByStatusIn(anyCollection())).thenReturn(List.of(pastTrip, ongoingTrip));

        service.scanAndSyncAllTripsOnStartup();

        assertEquals(TripStatus.COMPLETED, pastTrip.getStatus());
        assertEquals(TripStatus.ONGOING, ongoingTrip.getStatus());
        verify(trainTripRepository).save(pastTrip);
        verify(trainTripRepository).save(ongoingTrip);
    }
}
