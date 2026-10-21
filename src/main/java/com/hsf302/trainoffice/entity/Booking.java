package com.hsf302.trainoffice.entity;

import com.hsf302.trainoffice.common.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_bookings_code", columnList = "booking_code"),
                @Index(name = "idx_bookings_trip", columnList = "trip_id"),
                @Index(name = "idx_bookings_departure_station", columnList = "departure_station_id"),
                @Index(name = "idx_bookings_arrival_station", columnList = "arrival_station_id"),
                @Index(name = "idx_bookings_user", columnList = "user_id"),
                @Index(name = "idx_bookings_status", columnList = "booking_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    @EqualsAndHashCode.Include
    private Long bookingId;

    @Column(name = "booking_code", unique = true, nullable = false, length = 30)
    private String bookingNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    @ToString.Exclude
    private TrainTrip trainTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departure_station_id", nullable = false)
    @ToString.Exclude
    private Station departureStation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arrival_station_id", nullable = false)
    @ToString.Exclude
    private Station arrivalStation;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    @Column(name = "booker_name", nullable = false, length = 100, columnDefinition = "NVARCHAR(100)")
    private String bookerName;

    @Column(name = "booker_phone", nullable = false, length = 20)
    private String bookerPhone;

    @Column(name = "booker_email", nullable = false, length = 120)
    private String bookerEmail;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 30)
    private BookingStatus bookingStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime bookingDate;

    @Builder.Default
    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Passenger> passengers = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Ticket> tickets = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Payment> payments = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (bookingDate == null) {
            bookingDate = LocalDateTime.now();
        }
        if (bookingStatus == null) {
            bookingStatus = BookingStatus.PENDING_PAYMENT;
        }
    }

    public String getBookingCode() {
        return bookingNumber;
    }

    public void setBookingCode(String bookingCode) {
        this.bookingNumber = bookingCode;
    }

    public String getContactName() {
        return bookerName;
    }

    public void setContactName(String contactName) {
        this.bookerName = contactName;
    }

    public String getContactPhone() {
        return bookerPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.bookerPhone = contactPhone;
    }

    public String getContactEmail() {
        return bookerEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.bookerEmail = contactEmail;
    }

    public LocalDateTime getCreatedAt() {
        return bookingDate;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.bookingDate = createdAt;
    }
}
