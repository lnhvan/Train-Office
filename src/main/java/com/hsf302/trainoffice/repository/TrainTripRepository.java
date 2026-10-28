package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.TrainTrip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrainTripRepository extends JpaRepository<TrainTrip, Long> {

    @Query("""
            select distinct t
            from TrainTrip t
                 join fetch t.train
                 join fetch t.route
                 join t.tripStations departureStop
                 join t.tripStations arrivalStop
            where t.status = :status
              and t.departureTime >= :startOfDay
              and t.departureTime < :endOfDay
              and departureStop.routeStation.station.stationId = :departureStationId
              and arrivalStop.routeStation.station.stationId = :arrivalStationId
              and departureStop.routeStation.stationOrder < arrivalStop.routeStation.stationOrder
            order by t.departureTime asc
            """)
    List<TrainTrip> findScheduledTripsForSegment(@Param("departureStationId") Long departureStationId,
                                                 @Param("arrivalStationId") Long arrivalStationId,
                                                 @Param("startOfDay") LocalDateTime startOfDay,
                                                 @Param("endOfDay") LocalDateTime endOfDay,
                                                 @Param("status") TripStatus status);

    @EntityGraph(attributePaths = {"train", "route"})
    List<TrainTrip> findAll();

    @EntityGraph(attributePaths = {"train", "route"})
    Optional<TrainTrip> findByTripId(Long tripId);

    @EntityGraph(attributePaths = {"train", "route"})
    List<TrainTrip> findByStatusAndDepartureTimeGreaterThanEqualAndDepartureTimeLessThanOrderByDepartureTimeAsc(
            TripStatus status,
            LocalDateTime start,
            LocalDateTime end
    );

    @EntityGraph(attributePaths = {"train", "route"})
    Page<TrainTrip> findByStatusAndDepartureTimeGreaterThanEqualAndDepartureTimeLessThanOrderByDepartureTimeAsc(
            TripStatus status,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"train", "route"})
    @Query("""
            select t from TrainTrip t
            left join t.train tr
            left join t.route r
            where (:trainId is null or tr.trainId = :trainId)
              and (:routeId is null or r.routeId = :routeId)
              and (:status is null or t.status = :status)
              and (:startOfDay is null or t.departureTime >= :startOfDay)
              and (:endOfDay is null or t.departureTime < :endOfDay)
              and (:keyword is null
                   or lower(tr.trainCode) like lower(concat('%', :keyword, '%'))
                   or lower(tr.trainName) like lower(concat('%', :keyword, '%'))
                   or lower(r.routeCode) like lower(concat('%', :keyword, '%'))
                   or lower(r.routeName) like lower(concat('%', :keyword, '%'))
                   or concat('', t.tripId) like concat('%', :keyword, '%'))
            order by t.departureTime asc, t.tripId asc
            """)
    List<TrainTrip> searchTrips(@Param("keyword") String keyword,
                                @Param("trainId") Long trainId,
                                @Param("routeId") Long routeId,
                                @Param("status") TripStatus status,
                                @Param("startOfDay") LocalDateTime startOfDay,
                                @Param("endOfDay") LocalDateTime endOfDay);
}
