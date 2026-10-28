package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.RouteStation;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.RouteStationRepository;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.repository.TripStationRepository;
import com.hsf302.trainoffice.service.TripStationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class TripStationServiceImpl implements TripStationService {
    private final TripStationRepository tripStationRepository;
    private final TrainTripRepository trainTripRepository;
    private final RouteStationRepository routeStationRepository;
    private final BookingRepository bookingRepository;
    private final TripScheduleBuilder tripScheduleBuilder;

    public TripStationServiceImpl(TripStationRepository tripStationRepository,
                                  TrainTripRepository trainTripRepository,
                                  RouteStationRepository routeStationRepository,
                                  BookingRepository bookingRepository,
                                  TripScheduleBuilder tripScheduleBuilder) {
        this.tripStationRepository = tripStationRepository;
        this.trainTripRepository = trainTripRepository;
        this.routeStationRepository = routeStationRepository;
        this.bookingRepository = bookingRepository;
        this.tripScheduleBuilder = tripScheduleBuilder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripStation> getStationsForTrip(Long tripId) {
        return tripStationRepository.findStopsOfTrip(tripId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TripStation> getTripStationById(Long id) {
        return tripStationRepository.findById(id);
    }

    @Override
    @Transactional
    public TripStation saveTripStation(Long tripId, TripStation tripStation) {
        TrainTrip trainTrip = findTrip(tripId);
        RouteStation routeStation = resolveRouteStation(trainTrip, tripStation);

        validateUnique(tripId, null, routeStation);
        validateTimes(tripStation);

        tripStation.setTrainTrip(trainTrip);
        tripStation.setRouteStation(routeStation);

        return tripStationRepository.save(tripStation);
    }

    @Override
    @Transactional
    public TripStation updateTripStation(Long tripId, Long tripStationId, TripStation form) {
        TripStation existing = findOwnedStop(tripId, tripStationId);
        TrainTrip trainTrip = existing.getTrainTrip();
        RouteStation routeStation = resolveRouteStation(trainTrip, form);

        boolean changesStop = !Objects.equals(existing.getRouteStation().getRouteStationId(), routeStation.getRouteStationId());

        if (changesStop) {
            ensureNoBookings(tripId);
        }

        validateUnique(tripId, tripStationId, routeStation);
        validateTimes(form);

        existing.setRouteStation(routeStation);
        existing.setArrivalTime(form.getArrivalTime());
        existing.setDepartureTime(form.getDepartureTime());

        return tripStationRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteTripStation(Long tripId, Long tripStationId) {
        TripStation existing = findOwnedStop(tripId, tripStationId);
        ensureNoBookings(tripId);
        tripStationRepository.delete(existing);
    }

    @Override
    @Transactional
    public int generateFromRoute(Long tripId) {
        return tripScheduleBuilder.addMissingStops(findTrip(tripId));
    }

    private TrainTrip findTrip(Long tripId) {
        return trainTripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainTripDoesNotExist")));
    }

    private TripStation findOwnedStop(Long tripId, Long tripStationId) {
        TripStation existing = tripStationRepository.findById(tripStationId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.tripStationDoesNotExist")));

        if (!existing.getTrainTrip().getTripId().equals(tripId)) {
            throw new IllegalArgumentException(I18n.t("err.tripStationDoesNotBelongToThis"));
        }

        return existing;
    }

    /** Điểm dừng phải là một ga thuộc đúng tuyến của chuyến. */
    private RouteStation resolveRouteStation(TrainTrip trainTrip, TripStation form) {
        if (form.getRouteStation() == null || form.getRouteStation().getRouteStationId() == null) {
            throw new IllegalArgumentException(I18n.t("err.routeStationIsRequired"));
        }

        RouteStation routeStation = routeStationRepository.findById(form.getRouteStation().getRouteStationId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.routeStationDoesNotExist")));

        if (trainTrip.getRoute() == null
                || !routeStation.getRoute().getRouteId().equals(trainTrip.getRoute().getRouteId())) {
            throw new IllegalArgumentException(I18n.t("err.routeStationNotInTripRoute"));
        }

        return routeStation;
    }

    /** Một ga của tuyến chỉ được xuất hiện một lần trong chuyến (unique trip_id + route_station_id). */
    private void validateUnique(Long tripId, Long currentTripStationId, RouteStation routeStation) {
        for (TripStation existing : tripStationRepository.findStopsOfTrip(tripId)) {
            if (currentTripStationId != null && currentTripStationId.equals(existing.getTripStationId())) {
                continue;
            }

            if (existing.getRouteStation().getRouteStationId().equals(routeStation.getRouteStationId())) {
                throw new IllegalArgumentException(I18n.t("err.stationAlreadyExistsInThisTrainTrip"));
            }
        }
    }

    private void validateTimes(TripStation form) {
        if (form.getArrivalTime() != null && form.getDepartureTime() != null
                && form.getArrivalTime().isAfter(form.getDepartureTime())) {
            throw new IllegalArgumentException(I18n.t("err.arrivalAfterDeparture"));
        }
    }

    /** Xóa / đổi ga của chuyến đã có người đặt sẽ làm sai đoạn đi của vé -> chặn. */
    private void ensureNoBookings(Long tripId) {
        if (bookingRepository.existsByTrainTrip_TripId(tripId)) {
            throw new IllegalArgumentException(I18n.t("err.tripHasBookingsCannotChangeStops"));
        }
    }
}
