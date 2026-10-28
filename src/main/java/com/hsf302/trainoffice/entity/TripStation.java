package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "trip_stations",
        uniqueConstraints = {
                // Mỗi ga của tuyến chỉ xuất hiện một lần trong một chuyến.
                // (Không trùng ga / trùng thứ tự được đảm bảo vì route_stations đã unique theo tuyến.)
                @UniqueConstraint(name = "uk_trip_stations_trip_route_station", columnNames = {"trip_id", "route_station_id"})
        },
        indexes = {
                @Index(name = "idx_trip_stations_trip", columnList = "trip_id"),
                @Index(name = "idx_trip_stations_route_station", columnList = "route_station_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TripStation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trip_station_id")
    @EqualsAndHashCode.Include
    private Long tripStationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    @ToString.Exclude
    private TrainTrip trainTrip;

    /**
     * Ga của tuyến mà chuyến dừng lại. Ga (station) và thứ tự dừng lấy qua đây,
     * không lưu lặp lại trong bảng trip_stations.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "route_station_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_trip_stations_route_station")
    )
    @ToString.Exclude
    private RouteStation routeStation;

    @Column(name = "arrival_time")
    private LocalDateTime arrivalTime;

    @Column(name = "departure_time")
    private LocalDateTime departureTime;

    /** Ga của điểm dừng (lấy từ ga của tuyến). Không phải cột trong DB. */
    public Station getStation() {
        return routeStation != null ? routeStation.getStation() : null;
    }

    /** Thứ tự dừng = thứ tự ga trên tuyến (có thể nhảy cóc nếu chuyến bỏ qua ga). Không phải cột trong DB. */
    public Integer getStationOrder() {
        return routeStation != null ? routeStation.getStationOrder() : null;
    }
}
