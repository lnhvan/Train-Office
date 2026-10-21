package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.CustomerTripView;
import com.hsf302.trainoffice.dto.TripSearchForm;
import com.hsf302.trainoffice.dto.TripSearchResult;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.TrainTrip;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrainTripService {
    List<TrainTrip> getAllTrips();

    Optional<TrainTrip> getTripById(Long id);

    TrainTrip saveTrip(TrainTrip trainTrip);

    TrainTrip updateTrip(Long id, TrainTrip trainTrip);

    void cancelTrip(Long id);

    List<TripSearchResult> searchTrips(TripSearchForm form);

    TripSegment getValidSegment(Long tripId, Long departureStationId, Long arrivalStationId);
    List<CustomerTripView> getCustomerTripsByDate(LocalDate departureDate);
}
