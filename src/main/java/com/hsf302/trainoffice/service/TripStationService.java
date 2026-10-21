package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.TripStation;

import java.util.List;
import java.util.Optional;

public interface TripStationService {
    List<TripStation> getStationsForTrip(Long tripId);

    Optional<TripStation> getTripStationById(Long id);

    TripStation saveTripStation(Long tripId, TripStation tripStation);

    TripStation updateTripStation(Long tripId, Long tripStationId, TripStation tripStation);

    void deleteTripStation(Long tripId, Long tripStationId);
}
