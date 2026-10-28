package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.CustomerTripView;
import com.hsf302.trainoffice.dto.StationAvailability;
import com.hsf302.trainoffice.dto.TripSearchForm;
import com.hsf302.trainoffice.dto.TripSearchResult;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.Route;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.BookingRepository;
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
    private final BookingRepository bookingRepository;
    private final TripScheduleBuilder tripScheduleBuilder;

    public TrainTripServiceImpl(TrainTripRepository trainTripRepository,
                                TripStationRepository tripStationRepository,
                                TrainRepository trainRepository,
                                RouteRepository routeRepository,
                                BookingRepository bookingRepository,
                                TripScheduleBuilder tripScheduleBuilder) {
        this.trainTripRepository = trainTripRepository;
        this.tripStationRepository = tripStationRepository;
        this.trainRepository = trainRepository;
        this.routeRepository = routeRepository;
        this.bookingRepository = bookingRepository;
        this.tripScheduleBuilder = tripScheduleBuilder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainTrip> getAllTrips() {
        return trainTripRepository.findAll();
    }
    @Override
    @Transactional(readOnly = true)
    public List<TrainTrip> filterTrips(String keyword, Long trainId, Long routeId, TripStatus status, LocalDate departureDate) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        LocalDateTime startOfDay = departureDate != null ? departureDate.atStartOfDay() : null;
        LocalDateTime endOfDay = departureDate != null ? departureDate.plusDays(1).atStartOfDay() : null;

        return trainTripRepository.searchTrips(cleanKeyword, trainId, routeId, status, startOfDay, endOfDay);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TrainTrip> getTripById(Long id) {
        return trainTripRepository.findByTripId(id);
    }

    @Override
    @Transactional
    public TrainTrip saveTrip(TrainTrip trainTrip) {
        boolean isNew = trainTrip.getTripId() == null;
        attachTrainAndRoute(trainTrip);
        TrainTrip saved = trainTripRepository.save(trainTrip);

        // Chuyáº¿n má»›i: tá»± sinh lá»‹ch dá»«ng tá»« cĂ¡c ga cá»§a tuyáº¿n (admin chá»‰nh giá» sau náº¿u cáº§n).
        if (isNew) {
            tripScheduleBuilder.addMissingStops(saved);
        }

        return saved;
    }

    @Override
    @Transactional
    public TrainTrip updateTrip(Long id, TrainTrip form) {
        TrainTrip existing = trainTripRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainTripDoesNotExist")));
        Long oldRouteId = existing.getRoute() != null ? existing.getRoute().getRouteId() : null;

        existing.setTrain(form.getTrain());
        existing.setRoute(form.getRoute());
        existing.setDepartureTime(form.getDepartureTime());
        existing.setArrivalTime(form.getArrivalTime());
        existing.setBasePrice(form.getBasePrice());
        existing.setStatus(form.getStatus());
        attachTrainAndRoute(existing);

        boolean routeChanged = !existing.getRoute().getRouteId().equals(oldRouteId);

        if (routeChanged) {
            // Lá»‹ch dá»«ng cÅ© thuá»™c tuyáº¿n cÅ© -> pháº£i bá» vĂ  sinh láº¡i theo tuyáº¿n má»›i.
            if (bookingRepository.existsByTrainTrip_TripId(id)) {
                throw new IllegalArgumentException(I18n.t("err.cannotChangeRouteOfBookedTrip"));
            }

            tripStationRepository.deleteAll(tripStationRepository.findStopsOfTrip(id));
            tripStationRepository.flush();
        }

        TrainTrip saved = trainTripRepository.save(existing);

        if (routeChanged) {
            tripScheduleBuilder.addMissingStops(saved);
        }

        return saved;
    }

    @Override
    @Transactional
    public void cancelTrip(Long id) {
        TrainTrip trainTrip = trainTripRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainTripDoesNotExist")));
        trainTrip.setStatus(TripStatus.CANCELLED);
        trainTripRepository.save(trainTrip);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripSearchResult> searchTrips(TripSearchForm form) {
        if (form.getDepartureStationId() == null || form.getArrivalStationId() == null) {
            throw new IllegalArgumentException(I18n.t("err.departureAndArrivalStationsAreRequired"));
        }
        if (form.getDepartureStationId().equals(form.getArrivalStationId())) {
            throw new IllegalArgumentException(I18n.t("err.departureStationAndArrivalStationMustBe"));
        }
        if (form.getDepartureDate() == null) {
            throw new IllegalArgumentException(I18n.t("err.departureDateIsRequired"));
        }
        if (form.getPassengerCount() <= 0) {
            throw new IllegalArgumentException(I18n.t("err.passengerCountMustBeGreaterThan0"));
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
                .findStop(tripId, departureStationId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.departureStationIsNotPartOfThis")));
        TripStation arrival = tripStationRepository
                .findStop(tripId, arrivalStationId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.arrivalStationIsNotPartOfThis")));
        if (departure.getStationOrder() == null || arrival.getStationOrder() == null
                || departure.getStationOrder() >= arrival.getStationOrder()) {
            throw new IllegalArgumentException(I18n.t("err.departureStationMustBeBeforeArrivalStation2"));
        }
        return new TripSegment(departure, arrival);
    }

    private void attachTrainAndRoute(TrainTrip trainTrip) {
        if (trainTrip.getTrain() == null || trainTrip.getTrain().getTrainId() == null) {
            throw new IllegalArgumentException(I18n.t("err.trainIsRequired"));
        }
        if (trainTrip.getRoute() == null || trainTrip.getRoute().getRouteId() == null) {
            throw new IllegalArgumentException(I18n.t("err.routeIsRequired"));
        }
        Train train = trainRepository.findById(trainTrip.getTrain().getTrainId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainDoesNotExist")));
        Route route = routeRepository.findById(trainTrip.getRoute().getRouteId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.routeDoesNotExist")));
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
    /** Sá»‘ ngĂ y tá»›i (tĂ­nh tá»« hĂ´m nay) dĂ¹ng Ä‘á»ƒ gá»£i Ă½ ngĂ y cĂ³ chuyáº¿n. */
    private static final int AVAILABILITY_WINDOW_DAYS = 60;

    @Override
    @Transactional(readOnly = true)
    public StationAvailability getStationAvailability(Long departureStationId,
                                                      Long arrivalStationId,
                                                      LocalDate departureDate) {
        LocalDateTime windowStart = LocalDate.now().atStartOfDay();
        LocalDateTime windowEnd = windowStart.plusDays(AVAILABILITY_WINDOW_DAYS);

        // ÄĂ£ chá»n ngĂ y -> lá»c ga theo Ä‘Ăºng ngĂ y Ä‘Ă³; chÆ°a chá»n -> má»i chuyáº¿n trong 60 ngĂ y tá»›i.
        LocalDateTime fromTime = departureDate != null ? departureDate.atStartOfDay() : windowStart;
        LocalDateTime toTime = departureDate != null ? fromTime.plusDays(1) : windowEnd;

        List<Long> departureStationIds = tripStationRepository.findDepartureStationIdsWithTrips(
                arrivalStationId, fromTime, toTime, TripStatus.SCHEDULED);

        List<Long> arrivalStationIds = tripStationRepository.findArrivalStationIdsWithTrips(
                departureStationId, fromTime, toTime, TripStatus.SCHEDULED);

        List<String> availableDates = tripStationRepository.findTripDepartureTimes(
                        departureStationId, arrivalStationId, windowStart, windowEnd, TripStatus.SCHEDULED)
                .stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .map(LocalDate::toString)
                .toList();

        return new StationAvailability(departureStationIds, arrivalStationIds, availableDates);
    }

    private CustomerTripView toCustomerTripView(TrainTrip trip) {
        List<TripStation> stations = tripStationRepository
                .findStopsOfTrip(trip.getTripId());

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
