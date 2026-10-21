package com.hsf302.trainoffice.entity;

import com.hsf302.trainoffice.common.enums.TrainStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "trains",
        indexes = @Index(name = "idx_trains_code", columnList = "train_code")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Train {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "train_id")
    @EqualsAndHashCode.Include
    private Long trainId;

    @Column(name = "train_code", unique = true, nullable = false, length = 30)
    private String trainCode;

    @Column(name = "train_name", nullable = false, length = 100, columnDefinition = "NVARCHAR(100)")
    private String trainName;

    @Column(name = "train_type", nullable = false, length = 50, columnDefinition = "NVARCHAR(50)")
    private String trainType;

    @Builder.Default
    @OneToMany(mappedBy = "train", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Coach> coaches = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "train", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<TrainTrip> trainTrips = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TrainStatus status = TrainStatus.AVAILABLE;
}
