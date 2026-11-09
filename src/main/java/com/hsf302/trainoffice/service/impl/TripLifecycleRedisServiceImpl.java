package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.service.TripLifecycleRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TripLifecycleRedisServiceImpl implements TripLifecycleRedisService {

    private static final Logger log = LoggerFactory.getLogger(TripLifecycleRedisServiceImpl.class);

    private final TrainTripRepository trainTripRepository;
    private final StringRedisTemplate redisTemplate;

    public TripLifecycleRedisServiceImpl(TrainTripRepository trainTripRepository,
                                         StringRedisTemplate redisTemplate) {
        this.trainTripRepository = trainTripRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public void scheduleTrip(TrainTrip trip) {
        if (trip == null || trip.getTripId() == null) {
            return;
        }

        if (trip.getStatus() == TripStatus.CANCELLED) {
            cancelTripKey(trip.getTripId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime departure = trip.getDepartureTime();
        LocalDateTime arrival = trip.getArrivalTime();

        if (departure == null) {
            return;
        }

        if (arrival == null) {
            arrival = departure;
        }

        // Case 1: Đã qua giờ đến -> COMPLETED
        if (!arrival.isAfter(now)) {
            if (trip.getStatus() != TripStatus.COMPLETED) {
                trip.setStatus(TripStatus.COMPLETED);
                trainTripRepository.save(trip);
                log.info("[TripLifecycle] Trip #{} arrival {} has passed -> COMPLETED", trip.getTripId(), arrival);
            }
            cancelTripKey(trip.getTripId());
            return;
        }

        // Case 2: Đang trong khoảng [departure, arrival) -> ONGOING
        if (!departure.isAfter(now)) {
            if (trip.getStatus() != TripStatus.ONGOING) {
                trip.setStatus(TripStatus.ONGOING);
                trainTripRepository.save(trip);
                log.info("[TripLifecycle] Trip #{} departure {} reached -> ONGOING", trip.getTripId(), departure);
            }
            long secondsUntilArrival = Math.max(1L, Duration.between(now, arrival).getSeconds());
            safeRedis(() -> {
                redisTemplate.opsForValue().set(TRIP_ONGOING_PREFIX + trip.getTripId(), trip.getTripId().toString(), secondsUntilArrival, TimeUnit.SECONDS);
                redisTemplate.delete(TRIP_SCHEDULED_PREFIX + trip.getTripId());
            });
            return;
        }

        // Case 3: Chưa tới giờ chạy -> SCHEDULED
        if (trip.getStatus() != TripStatus.SCHEDULED) {
            trip.setStatus(TripStatus.SCHEDULED);
            trainTripRepository.save(trip);
        }
        long secondsUntilDeparture = Math.max(1L, Duration.between(now, departure).getSeconds());
        safeRedis(() -> {
            redisTemplate.opsForValue().set(TRIP_SCHEDULED_PREFIX + trip.getTripId(), trip.getTripId().toString(), secondsUntilDeparture, TimeUnit.SECONDS);
            redisTemplate.delete(TRIP_ONGOING_PREFIX + trip.getTripId());
        });
    }

    @Override
    public void cancelTripKey(Long tripId) {
        if (tripId == null) {
            return;
        }
        safeRedis(() -> {
            redisTemplate.delete(TRIP_SCHEDULED_PREFIX + tripId);
            redisTemplate.delete(TRIP_ONGOING_PREFIX + tripId);
        });
    }

    @Override
    public void scanAndSyncAllTripsOnStartup() {
        log.info("[TripLifecycle] Scanning and syncing all trips on startup...");
        List<TrainTrip> activeTrips;
        try {
            activeTrips = trainTripRepository.findByStatusIn(List.of(TripStatus.SCHEDULED, TripStatus.ONGOING));
        } catch (Exception ex) {
            log.error("[TripLifecycle] Failed to query active trips for startup sync: {}", ex.getMessage());
            return;
        }

        for (TrainTrip trip : activeTrips) {
            try {
                scheduleTrip(trip);
            } catch (Exception ex) {
                log.error("[TripLifecycle] Failed to sync trip #{}: {}", trip.getTripId(), ex.getMessage());
            }
        }
        log.info("[TripLifecycle] Finished synchronizing {} active trips.", activeTrips.size());
    }

    @Override
    @Transactional
    public void handleExpiredKey(String expiredKey) {
        if (expiredKey == null) {
            return;
        }

        if (expiredKey.startsWith(TRIP_SCHEDULED_PREFIX)) {
            Long tripId = parseTripId(expiredKey, TRIP_SCHEDULED_PREFIX);
            if (tripId != null) {
                log.info("[TripLifecycle] Redis key expired for scheduled trip #{}, updating to ONGOING...", tripId);
                trainTripRepository.findById(tripId).ifPresent(trip -> {
                    if (trip.getStatus() == TripStatus.SCHEDULED) {
                        scheduleTrip(trip);
                    }
                });
            }
        } else if (expiredKey.startsWith(TRIP_ONGOING_PREFIX)) {
            Long tripId = parseTripId(expiredKey, TRIP_ONGOING_PREFIX);
            if (tripId != null) {
                log.info("[TripLifecycle] Redis key expired for ongoing trip #{}, updating to COMPLETED...", tripId);
                trainTripRepository.findById(tripId).ifPresent(trip -> {
                    if (trip.getStatus() == TripStatus.ONGOING) {
                        scheduleTrip(trip);
                    }
                });
            }
        }
    }

    @Scheduled(fixedRate = 60000, initialDelay = 60000)
    @Transactional
    public void periodicSafetySync() {
        List<TrainTrip> activeTrips = trainTripRepository.findByStatusIn(List.of(TripStatus.SCHEDULED, TripStatus.ONGOING));
        LocalDateTime now = LocalDateTime.now();
        for (TrainTrip trip : activeTrips) {
            boolean shouldUpdate = false;
            if (trip.getStatus() == TripStatus.SCHEDULED && trip.getDepartureTime() != null && !trip.getDepartureTime().isAfter(now)) {
                shouldUpdate = true;
            } else if (trip.getStatus() == TripStatus.ONGOING && trip.getArrivalTime() != null && !trip.getArrivalTime().isAfter(now)) {
                shouldUpdate = true;
            }
            if (shouldUpdate) {
                scheduleTrip(trip);
            }
        }
    }

    private Long parseTripId(String key, String prefix) {
        try {
            return Long.parseLong(key.substring(prefix.length()));
        } catch (Exception e) {
            return null;
        }
    }

    private void safeRedis(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("[TripLifecycle] Redis operation warning: {}", ex.getMessage());
        }
    }
}
