package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "route_stations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_route_stations_route_order", columnNames = {"route_id", "station_order"}),
                @UniqueConstraint(name = "uk_route_stations_route_station", columnNames = {"route_id", "station_id"})
        },
        indexes = {
                @Index(name = "idx_route_stations_route", columnList = "route_id"),
                @Index(name = "idx_route_stations_station", columnList = "station_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RouteStation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_station_id")
    @EqualsAndHashCode.Include
    private Long routeStationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    @ToString.Exclude
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    @ToString.Exclude
    private Station station;

    @Column(name = "station_order", nullable = false)
    private Integer stationOrder;

    @Column(name = "distance_from_start_km", nullable = false, columnDefinition = "float default 0")
    @Builder.Default
    private Double distanceFromStartKm = 0.0;
}