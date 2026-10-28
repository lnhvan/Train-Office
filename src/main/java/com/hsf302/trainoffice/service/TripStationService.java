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

    /** Thêm vào chuyến các ga của tuyến còn thiếu, giờ nội suy theo km. Trả về số ga đã thêm. */
    int generateFromRoute(Long tripId);
}
