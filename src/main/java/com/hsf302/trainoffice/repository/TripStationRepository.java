package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.TripStation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TripStationRepository extends JpaRepository<TripStation, Long> {

    /** Điểm dừng của chuyến tại một ga. */
    @Query("""
            select ts
            from TripStation ts
                 join fetch ts.routeStation rs
                 join fetch rs.station s
                 join fetch ts.trainTrip
            where ts.trainTrip.tripId = :tripId
              and s.stationId = :stationId
            """)
    Optional<TripStation> findStop(@Param("tripId") Long tripId, @Param("stationId") Long stationId);

    /** Lịch dừng của chuyến, theo thứ tự ga trên tuyến. */
    @Query("""
            select ts
            from TripStation ts
                 join fetch ts.routeStation rs
                 join fetch rs.station
                 join fetch ts.trainTrip
            where ts.trainTrip.tripId = :tripId
            order by rs.stationOrder asc
            """)
    List<TripStation> findStopsOfTrip(@Param("tripId") Long tripId);

    boolean existsByTrainTrip_TripIdAndRouteStation_RouteStationId(Long tripId, Long routeStationId);

    long countByRouteStation_RouteStationId(Long routeStationId);

    @EntityGraph(attributePaths = {"trainTrip", "routeStation"})
    List<TripStation> findByRouteStation_Route_RouteId(Long routeId);

    /*
     * Gợi ý cho ô tìm chuyến. "dep" là điểm dừng lên tàu, "arr" là điểm dừng xuống tàu của cùng một chuyến,
     * dep phải đứng trước arr trên tuyến. Khoảng thời gian lọc theo giờ khởi hành của chuyến
     * (giống truy vấn tìm chuyến findScheduledTripsForSegment).
     */

    /** Ga đi có chuyến (nếu đã chọn ga đến thì chỉ lấy ga đi có chuyến tới ga đó). */
    @Query("""
            select distinct dep.routeStation.station.stationId
            from TripStation dep, TripStation arr
            where arr.trainTrip = dep.trainTrip
              and dep.trainTrip.status = :status
              and dep.trainTrip.departureTime >= :fromTime
              and dep.trainTrip.departureTime < :toTime
              and dep.routeStation.stationOrder < arr.routeStation.stationOrder
              and (:arrivalStationId is null or arr.routeStation.station.stationId = :arrivalStationId)
            """)
    List<Long> findDepartureStationIdsWithTrips(@Param("arrivalStationId") Long arrivalStationId,
                                                @Param("fromTime") LocalDateTime fromTime,
                                                @Param("toTime") LocalDateTime toTime,
                                                @Param("status") TripStatus status);

    /** Ga đến có chuyến (nếu đã chọn ga đi thì chỉ lấy ga đến đi được từ ga đó). */
    @Query("""
            select distinct arr.routeStation.station.stationId
            from TripStation dep, TripStation arr
            where arr.trainTrip = dep.trainTrip
              and dep.trainTrip.status = :status
              and dep.trainTrip.departureTime >= :fromTime
              and dep.trainTrip.departureTime < :toTime
              and dep.routeStation.stationOrder < arr.routeStation.stationOrder
              and (:departureStationId is null or dep.routeStation.station.stationId = :departureStationId)
            """)
    List<Long> findArrivalStationIdsWithTrips(@Param("departureStationId") Long departureStationId,
                                              @Param("fromTime") LocalDateTime fromTime,
                                              @Param("toTime") LocalDateTime toTime,
                                              @Param("status") TripStatus status);

    /** Giờ khởi hành của các chuyến phục vụ được cặp ga đã chọn (ga nào chưa chọn thì bỏ qua điều kiện). */
    @Query("""
            select distinct dep.trainTrip.departureTime
            from TripStation dep, TripStation arr
            where arr.trainTrip = dep.trainTrip
              and dep.trainTrip.status = :status
              and dep.trainTrip.departureTime >= :fromTime
              and dep.trainTrip.departureTime < :toTime
              and dep.routeStation.stationOrder < arr.routeStation.stationOrder
              and (:departureStationId is null or dep.routeStation.station.stationId = :departureStationId)
              and (:arrivalStationId is null or arr.routeStation.station.stationId = :arrivalStationId)
            order by dep.trainTrip.departureTime asc
            """)
    List<LocalDateTime> findTripDepartureTimes(@Param("departureStationId") Long departureStationId,
                                               @Param("arrivalStationId") Long arrivalStationId,
                                               @Param("fromTime") LocalDateTime fromTime,
                                               @Param("toTime") LocalDateTime toTime,
                                               @Param("status") TripStatus status);
}
