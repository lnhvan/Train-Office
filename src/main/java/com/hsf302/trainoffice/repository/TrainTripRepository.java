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
              and departureStop.station.stationId = :departureStationId
              and arrivalStop.station.stationId = :arrivalStationId
              and departureStop.stationOrder < arrivalStop.stationOrder
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
}