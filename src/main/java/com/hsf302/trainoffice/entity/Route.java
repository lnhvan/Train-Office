package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "routes",
        indexes = @Index(name = "idx_routes_code", columnList = "route_code")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    @EqualsAndHashCode.Include
    private Long routeId;

    @Column(name = "route_code", unique = true, nullable = false, length = 30)
    private String routeCode;

    @Column(name = "route_name", nullable = false, length = 120, columnDefinition = "NVARCHAR(120)")
    private String routeName;

    @Column(name = "distance_km", nullable = false)
    private Double distanceKm;

    @Builder.Default
    @OneToMany(mappedBy = "route", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<RouteStation> routeStations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "route", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<TrainTrip> trainTrips = new ArrayList<>();
}
