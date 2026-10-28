package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.RouteStation;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.RouteStationRepository;
import com.hsf302.trainoffice.repository.TripStationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sinh lịch dừng (TripStation) của một chuyến từ các ga của tuyến (RouteStation).
 *
 * TripStation chỉ lưu: chuyến, ga của tuyến, giờ đến, giờ đi.
 * Ga và thứ tự dừng luôn đọc qua RouteStation nên không cần đồng bộ khi tuyến thay đổi.
 */
@Component
public class TripScheduleBuilder {

    /** Thời gian dừng mặc định ở mỗi ga giữa khi tự sinh lịch. */
    private static final long DEFAULT_DWELL_MINUTES = 5;

    private final RouteStationRepository routeStationRepository;
    private final TripStationRepository tripStationRepository;

    public TripScheduleBuilder(RouteStationRepository routeStationRepository,
                               TripStationRepository tripStationRepository) {
        this.routeStationRepository = routeStationRepository;
        this.tripStationRepository = tripStationRepository;
    }

    /**
     * Thêm vào chuyến những ga của tuyến mà chuyến chưa có.
     * Giờ đến/đi được nội suy theo km giữa giờ khởi hành và giờ đến của chuyến
     * (nếu tuyến chưa nhập km thì chia đều theo số ga).
     *
     * @return số điểm dừng đã thêm
     */
    @Transactional
    public int addMissingStops(TrainTrip trip) {
        if (trip == null || trip.getTripId() == null || trip.getRoute() == null) {
            return 0;
        }

        List<RouteStation> routeStations = routeStationRepository
                .findByRoute_RouteIdOrderByStationOrderAsc(trip.getRoute().getRouteId());

        if (routeStations.isEmpty()) {
            return 0;
        }

        Set<Long> usedRouteStationIds = new HashSet<>();
        for (TripStation stop : tripStationRepository.findStopsOfTrip(trip.getTripId())) {
            usedRouteStationIds.add(stop.getRouteStation().getRouteStationId());
        }

        int added = 0;

        for (int index = 0; index < routeStations.size(); index++) {
            RouteStation routeStation = routeStations.get(index);

            if (usedRouteStationIds.contains(routeStation.getRouteStationId())) {
                continue;
            }

            TripStation stop = new TripStation();
            stop.setTrainTrip(trip);
            stop.setRouteStation(routeStation);
            applyInterpolatedTimes(stop, trip.getDepartureTime(), trip.getArrivalTime(), routeStations, index);

            tripStationRepository.save(stop);
            added++;
        }

        return added;
    }

    /**
     * Đặt giờ đến/đi cho điểm dừng thứ {@code index} trong danh sách {@code routeStations}:
     * ga đầu chỉ có giờ đi, ga cuối chỉ có giờ đến, ga giữa nội suy theo km.
     */
    public void applyInterpolatedTimes(TripStation stop,
                                       LocalDateTime departure,
                                       LocalDateTime arrival,
                                       List<RouteStation> routeStations,
                                       int index) {
        if (departure == null || arrival == null) {
            return;
        }

        int last = routeStations.size() - 1;

        if (index == 0) {
            stop.setArrivalTime(null);
            stop.setDepartureTime(departure);
            return;
        }

        if (index == last) {
            stop.setArrivalTime(arrival);
            stop.setDepartureTime(null);
            return;
        }

        double firstKm = km(routeStations.get(0));
        double lastKm = km(routeStations.get(last));
        double ratio = lastKm > firstKm
                ? (km(routeStations.get(index)) - firstKm) / (lastKm - firstKm)
                : (double) index / last;
        ratio = Math.max(0, Math.min(1, ratio));

        long totalMinutes = Math.max(0, Duration.between(departure, arrival).toMinutes());
        LocalDateTime stopArrival = departure.plusMinutes(Math.round(totalMinutes * ratio));

        stop.setArrivalTime(stopArrival);
        stop.setDepartureTime(stopArrival.plusMinutes(DEFAULT_DWELL_MINUTES));
    }

    private double km(RouteStation routeStation) {
        return routeStation.getDistanceFromStartKm() == null ? 0.0 : routeStation.getDistanceFromStartKm();
    }
}
