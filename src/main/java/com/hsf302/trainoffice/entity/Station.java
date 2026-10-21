package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "stations",
        indexes = {
                @Index(name = "idx_stations_code", columnList = "station_code"),
                @Index(name = "idx_stations_city", columnList = "city")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Station {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    @EqualsAndHashCode.Include
    private Long stationId;

    @Column(name = "station_code", unique = true, nullable = false, length = 30)
    private String stationCode;

    @Column(name = "station_name", nullable = false, length = 120, columnDefinition = "NVARCHAR(120)")
    private String stationName;

    @Column(name = "city", nullable = false, length = 80, columnDefinition = "NVARCHAR(80)")
    private String city;

    @Builder.Default
    @OneToMany(mappedBy = "station", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<RouteStation> routeStations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "station", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<TripStation> tripStations = new ArrayList<>();
}
