package com.hsf302.trainoffice.entity;

import com.hsf302.trainoffice.common.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "passengers",
        indexes = {
                @Index(name = "idx_passengers_booking", columnList = "booking_id"),
                @Index(name = "idx_passengers_identity", columnList = "identity_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Passenger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "passenger_id")
    @EqualsAndHashCode.Include
    private Long passengerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    @ToString.Exclude
    private Booking booking;

    @Column(name = "full_name", nullable = false, length = 100, columnDefinition = "NVARCHAR(100)")
    private String fullName;

    @Column(name = "identity_number", length = 30, columnDefinition = "NVARCHAR(30)")
    private String identityNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Builder.Default
    @OneToMany(mappedBy = "passenger", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Ticket> tickets = new ArrayList<>();
    @Column(name = "passenger_type", length = 20)
    private String passengerType;

    @Column(name = "relationship_to_booker", length = 100, columnDefinition = "NVARCHAR(100)")
    private String relationshipToBooker;
}
