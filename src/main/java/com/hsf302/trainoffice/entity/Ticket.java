package com.hsf302.trainoffice.entity;


import com.hsf302.trainoffice.common.enums.TicketStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;


import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "tickets",
        indexes = {
                @Index(name = "idx_tickets_code", columnList = "ticket_code"),
                @Index(name = "idx_tickets_booking", columnList = "booking_id"),
                @Index(name = "idx_tickets_passenger", columnList = "passenger_id"),
                @Index(name = "idx_tickets_trip", columnList = "trip_id"),
                @Index(name = "idx_tickets_seat", columnList = "seat_id"),
                @Index(name = "idx_tickets_status", columnList = "ticket_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    @EqualsAndHashCode.Include
    private Long ticketId;

    @Column(name = "ticket_code", unique = true, nullable = false, length = 30)
    private String ticketCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    @ToString.Exclude
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "passenger_id", nullable = false)
    @ToString.Exclude
    private Passenger passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    @ToString.Exclude
    private TrainTrip trainTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    @ToString.Exclude
    private Seat seat;

    @Column(name = "ticket_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal ticketPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status", nullable = false, length = 20)
    private TicketStatus ticketStatus;

    @Builder.Default
    @OneToMany(mappedBy = "ticket", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Refund> refunds = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (ticketStatus == null) {
            ticketStatus = TicketStatus.BOOKED;
        }
    }
}
