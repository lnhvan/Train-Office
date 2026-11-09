package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.TripSchedulePreview;
import com.hsf302.trainoffice.entity.Route;
import com.hsf302.trainoffice.entity.RouteStation;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.RouteStationRepository;
import com.hsf302.trainoffice.repository.TripStationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sinh lịch dừng (TripStation) của một chuyến từ các ga của tuyến (RouteStation)
 * và tính giờ đến/đi từng ga theo tốc độ trung bình của tàu.
 *
 * Cách tính: từ giờ khởi hành ở ga đầu, mỗi chặng chạy mất (km giữa 2 ga ÷ tốc độ tàu),
 * cộng {@value #DWELL_MINUTES} phút dừng ở mỗi ga giữa. Giờ đến ga cuối = giờ đến của chuyến.
 *
 * TripStation chỉ lưu: chuyến, ga của tuyến, giờ đến, giờ đi.
 * Ga và thứ tự dừng luôn đọc qua RouteStation nên không cần đồng bộ khi tuyến thay đổi.
 */
@Component
public class TripScheduleBuilder {

    /** Thời gian dừng ở mỗi ga giữa (phút). */
    public static final long DWELL_MINUTES = 5;

    private static final DateTimeFormatter PREVIEW_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private final RouteStationRepository routeStationRepository;
    private final TripStationRepository tripStationRepository;

    public TripScheduleBuilder(RouteStationRepository routeStationRepository,
                               TripStationRepository tripStationRepository) {
        this.routeStationRepository = routeStationRepository;
        this.tripStationRepository = tripStationRepository;
    }

    /**
     * Thêm vào chuyến những ga của tuyến mà chuyến chưa có, rồi tính lại giờ cho toàn bộ lịch dừng.
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

        for (RouteStation routeStation : routeStations) {
            if (usedRouteStationIds.contains(routeStation.getRouteStationId())) {
                continue;
            }

            TripStation stop = new TripStation();
            stop.setTrainTrip(trip);
            stop.setRouteStation(routeStation);

            tripStationRepository.save(stop);
            added++;
        }

        tripStationRepository.flush();
        recalculateSchedule(trip);

        return added;
    }

    /**
     * Tính lại giờ đến/đi của mọi điểm dừng và giờ đến của chuyến,
     * dựa trên giờ khởi hành của chuyến, tốc độ tàu và km giữa các ga.
     * Trip được cập nhật arrivalTime (entity đang được quản lý trong transaction của nơi gọi).
     */
    @Transactional
    public void recalculateSchedule(TrainTrip trip) {
        if (trip == null || trip.getTripId() == null || trip.getDepartureTime() == null) {
            return;
        }

        List<TripStation> stops = tripStationRepository.findStopsOfTrip(trip.getTripId());

        if (stops.size() < 2) {
            if (trip.getArrivalTime() == null) {
                trip.setArrivalTime(trip.getDepartureTime());
            }
            return;
        }

        List<RouteStation> routeStations = stops.stream().map(TripStation::getRouteStation).toList();
        List<LocalDateTime[]> times = computeTimes(routeStations, requireSpeed(trip.getTrain()), trip.getDepartureTime());

        for (int index = 0; index < stops.size(); index++) {
            stops.get(index).setArrivalTime(times.get(index)[0]);
            stops.get(index).setDepartureTime(times.get(index)[1]);
        }

        trip.setArrivalTime(times.get(times.size() - 1)[0]);
        tripStationRepository.saveAll(stops);
    }

    /**
     * Xem trước lịch dừng nếu chuyến dùng tàu {@code train} chạy hết tuyến {@code route}
     * và khởi hành lúc {@code departure}. Không lưu gì vào DB.
     */
    @Transactional(readOnly = true)
    public TripSchedulePreview preview(Train train, Route route, LocalDateTime departure) {
        List<RouteStation> routeStations = routeStationRepository
                .findByRoute_RouteIdOrderByStationOrderAsc(route.getRouteId());

        if (routeStations.size() < 2) {
            throw new IllegalArgumentException(I18n.t("err.routeNeedsAtLeastTwoStations"));
        }

        int speedKmh = requireSpeed(train);
        List<LocalDateTime[]> times = computeTimes(routeStations, speedKmh, departure);
        List<TripSchedulePreview.Stop> stops = new ArrayList<>();

        for (int index = 0; index < routeStations.size(); index++) {
            RouteStation routeStation = routeStations.get(index);
            stops.add(new TripSchedulePreview.Stop(
                    index + 1,
                    routeStation.getStation() != null ? routeStation.getStation().getStationName() : "?",
                    km(routeStation),
                    format(times.get(index)[0]),
                    format(times.get(index)[1])
            ));
        }

        LocalDateTime arrival = times.get(times.size() - 1)[0];

        return new TripSchedulePreview(
                stops,
                format(departure),
                format(arrival),
                Duration.between(departure, arrival).toMinutes(),
                speedKmh,
                DWELL_MINUTES
        );
    }

    /**
     * Lõi tính giờ: mỗi phần tử kết quả là [giờ đến, giờ đi] của ga tương ứng.
     * Ga đầu không có giờ đến, ga cuối không có giờ đi.
     */
    private List<LocalDateTime[]> computeTimes(List<RouteStation> stations, int speedKmh, LocalDateTime start) {
        List<LocalDateTime[]> result = new ArrayList<>();
        double elapsedMinutes = 0;
        int last = stations.size() - 1;

        for (int index = 0; index <= last; index++) {
            if (index == 0) {
                result.add(new LocalDateTime[]{null, start});
                continue;
            }

            RouteStation current = stations.get(index);
            double legKm = km(current) - km(stations.get(index - 1));

            if (legKm <= 0) {
                throw new IllegalArgumentException(I18n.t(
                        "err.routeDistanceMissing",
                        current.getStation() != null ? current.getStation().getStationName() : "?"));
            }

            elapsedMinutes += legKm / speedKmh * 60.0;
            LocalDateTime arrival = start.plusMinutes(Math.round(elapsedMinutes));

            if (index == last) {
                result.add(new LocalDateTime[]{arrival, null});
            } else {
                result.add(new LocalDateTime[]{arrival, arrival.plusMinutes(DWELL_MINUTES)});
                elapsedMinutes += DWELL_MINUTES;
            }
        }

        return result;
    }

    private int requireSpeed(Train train) {
        Integer speed = train != null ? train.getAverageSpeedKmh() : null;

        if (speed == null || speed <= 0) {
            String trainCode = train != null ? train.getTrainCode() : "?";
            throw new IllegalArgumentException(I18n.t("err.trainSpeedNotSet", trainCode));
        }

        return speed;
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(PREVIEW_FORMAT);
    }

    private double km(RouteStation routeStation) {
        return routeStation == null || routeStation.getDistanceFromStartKm() == null
                ? 0.0
                : routeStation.getDistanceFromStartKm();
    }
}
