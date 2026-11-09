package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.TrainTrip;

public interface TripLifecycleRedisService {
    String TRIP_SCHEDULED_PREFIX = "trip:status:scheduled:";
    String TRIP_ONGOING_PREFIX = "trip:status:ongoing:";

    void scheduleTrip(TrainTrip trip);

    void cancelTripKey(Long tripId);

    void scanAndSyncAllTripsOnStartup();

    void handleExpiredKey(String expiredKey);
}
