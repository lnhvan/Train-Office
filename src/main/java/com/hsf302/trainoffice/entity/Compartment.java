package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "compartments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_compartments_coach_number",
                columnNames = {"coach_id", "compartment_number"}
        ),
        indexes = @Index(name = "idx_compartments_coach", columnList = "coach_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Compartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compartment_id")
    @EqualsAndHashCode.Include
    private Long compartmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    @ToString.Exclude
    private Coach coach;

    @Column(name = "compartment_number", nullable = false, length = 20)
    private String compartmentNumber;

    @Column(name = "compartment_type", nullable = false, length = 50)
    private String compartmentType;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Builder.Default
    @OneToMany(mappedBy = "compartment", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Seat> seats = new ArrayList<>();
}