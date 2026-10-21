package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "trip_stations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_trip_stations_trip_order", columnNames = {"trip_id", "station_order"}),
                @UniqueConstraint(name = "uk_trip_stations_trip_station", columnNames = {"trip_id", "station_id"})
        },
        indexes = {
                @Index(name = "idx_trip_stations_trip", columnList = "trip_id"),
                @Index(name = "idx_trip_stations_station", columnList = "station_id")
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    @ToString.Exclude
    private Station station;

    @Column(name = "station_order", nullable = false)
    private Integer stationOrder;

    @Column(name = "arrival_time")
    private LocalDateTime arrivalTime;

    @Column(name = "departure_time")
    private LocalDateTime departureTime;
}
