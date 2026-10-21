package com.hsf302.trainoffice.entity;

import com.hsf302.trainoffice.common.enums.TripStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "train_trips",
        indexes = {
                @Index(name = "idx_train_trips_train", columnList = "train_id"),
                @Index(name = "idx_train_trips_route", columnList = "route_id"),
                @Index(name = "idx_train_trips_departure", columnList = "departure_time"),
                @Index(name = "idx_train_trips_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TrainTrip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trip_id")
    @EqualsAndHashCode.Include
    private Long tripId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "train_id", nullable = false)
    @ToString.Exclude
    private Train train;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    @ToString.Exclude
    private Route route;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Column(name = "base_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status;

    @Builder.Default
    @OneToMany(mappedBy = "trainTrip", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<TripStation> tripStations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "trainTrip", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Booking> bookings = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = TripStatus.SCHEDULED;
        }
    }
}
