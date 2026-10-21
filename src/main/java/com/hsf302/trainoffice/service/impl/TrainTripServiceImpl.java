package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.CustomerTripView;
import com.hsf302.trainoffice.dto.TripSearchForm;
import com.hsf302.trainoffice.dto.TripSearchResult;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.Route;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.RouteRepository;
import com.hsf302.trainoffice.repository.TrainRepository;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.repository.TripStationRepository;
import com.hsf302.trainoffice.service.TrainTripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TrainTripServiceImpl implements TrainTripService {
    private final TrainTripRepository trainTripRepository;
    private final TripStationRepository tripStationRepository;
    private final TrainRepository trainRepository;
    private final RouteRepository routeRepository;

    public TrainTripServiceImpl(TrainTripRepository trainTripRepository,
                                TripStationRepository tripStationRepository,
                                TrainRepository trainRepository,
                                RouteRepository routeRepository) {
        this.trainTripRepository = trainTripRepository;
        this.tripStationRepository = tripStationRepository;
        this.trainRepository = trainRepository;
        this.routeRepository = routeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainTrip> getAllTrips() {
        return trainTripRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TrainTrip> getTripById(Long id) {
        return trainTripRepository.findByTripId(id);
    }

    @Override
    @Transactional
    public TrainTrip saveTrip(TrainTrip trainTrip) {
        attachTrainAndRoute(trainTrip);
        return trainTripRepository.save(trainTrip);
    }

    @Override
    @Transactional
    public TrainTrip updateTrip(Long id, TrainTrip form) {
        TrainTrip existing = trainTripRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Train trip does not exist"));
        existing.setTrain(form.getTrain());
        existing.setRoute(form.getRoute());
        existing.setDepartureTime(form.getDepartureTime());
        existing.setArrivalTime(form.getArrivalTime());
        existing.setBasePrice(form.getBasePrice());
        existing.setStatus(form.getStatus());
        attachTrainAndRoute(existing);
        return trainTripRepository.save(existing);
    }

    @Override
    @Transactional
    public void cancelTrip(Long id) {
        TrainTrip trainTrip = trainTripRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Train trip does not exist"));
        trainTrip.setStatus(TripStatus.CANCELLED);
        trainTripRepository.save(trainTrip);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripSearchResult> searchTrips(TripSearchForm form) {
        if (form.getDepartureStationId() == null || form.getArrivalStationId() == null) {
            throw new IllegalArgumentException("Departure and arrival stations are required");
        }
        if (form.getDepartureStationId().equals(form.getArrivalStationId())) {
            throw new IllegalArgumentException("Departure station and arrival station must be different");
        }
        if (form.getDepartureDate() == null) {
            throw new IllegalArgumentException("Departure date is required");
        }
        if (form.getPassengerCount() <= 0) {
            throw new IllegalArgumentException("Passenger count must be greater than 0");
        }

        LocalDateTime start = form.getDepartureDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return trainTripRepository.findScheduledTripsForSegment(
                        form.getDepartureStationId(),
                        form.getArrivalStationId(),
                        start,
                        end,
                        TripStatus.SCHEDULED)
                .stream()
                .map(trip -> new TripSearchResult(
                        trip,
                        getValidSegment(trip.getTripId(), form.getDepartureStationId(), form.getArrivalStationId()),
                        form.getPassengerCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TripSegment getValidSegment(Long tripId, Long departureStationId, Long arrivalStationId) {
        TripStation departure = tripStationRepository
                .findByTrainTrip_TripIdAndStation_StationId(tripId, departureStationId)
                .orElseThrow(() -> new IllegalArgumentException("Departure station is not part of this train trip"));
        TripStation arrival = tripStationRepository
                .findByTrainTrip_TripIdAndStation_StationId(tripId, arrivalStationId)
                .orElseThrow(() -> new IllegalArgumentException("Arrival station is not part of this train trip"));
        if (departure.getStationOrder() == null || arrival.getStationOrder() == null
                || departure.getStationOrder() >= arrival.getStationOrder()) {
            throw new IllegalArgumentException("Departure station must be before arrival station");
        }
        return new TripSegment(departure, arrival);
    }

    private void attachTrainAndRoute(TrainTrip trainTrip) {
        if (trainTrip.getTrain() == null || trainTrip.getTrain().getTrainId() == null) {
            throw new IllegalArgumentException("Train is required");
        }
        if (trainTrip.getRoute() == null || trainTrip.getRoute().getRouteId() == null) {
            throw new IllegalArgumentException("Route is required");
        }
        Train train = trainRepository.findById(trainTrip.getTrain().getTrainId())
                .orElseThrow(() -> new IllegalArgumentException("Train does not exist"));
        Route route = routeRepository.findById(trainTrip.getRoute().getRouteId())
                .orElseThrow(() -> new IllegalArgumentException("Route does not exist"));
        trainTrip.setTrain(train);
        trainTrip.setRoute(route);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTripView> getCustomerTripsByDate(LocalDate departureDate) {
        LocalDate date = departureDate != null ? departureDate : LocalDate.now();

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        return trainTripRepository
                .findByStatusAndDepartureTimeGreaterThanEqualAndDepartureTimeLessThanOrderByDepartureTimeAsc(
                        TripStatus.SCHEDULED,
                        start,
                        end
                )
                .stream()
                .map(this::toCustomerTripView)
                .toList();
    }
    private CustomerTripView toCustomerTripView(TrainTrip trip) {
        List<TripStation> stations = tripStationRepository
                .findByTrainTrip_TripIdOrderByStationOrderAsc(trip.getTripId());

        if (stations.size() < 2) {
            return new CustomerTripView(
                    trip,
                    null,
                    null,
                    null,
                    null,
                    false
            );
        }

        TripStation departureStop = stations.get(0);
        TripStation arrivalStop = stations.get(stations.size() - 1);

        return new CustomerTripView(
                trip,
                departureStop.getStation(),
                arrivalStop.getStation(),
                departureStop.getStation().getStationId(),
                arrivalStop.getStation().getStationId(),
                true
        );
    }
}
