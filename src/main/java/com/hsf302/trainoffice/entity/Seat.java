package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_seats_coach_number", columnNames = {"coach_id", "seat_number"}),
        indexes = {
                @Index(name = "idx_seats_coach", columnList = "coach_id"),
                @Index(name = "idx_seats_compartment", columnList = "compartment_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    @EqualsAndHashCode.Include
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    @ToString.Exclude
    private Coach coach;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compartment_id")
    @ToString.Exclude
    private Compartment compartment;

    @Column(name = "seat_number", nullable = false, length = 40)
    private String seatNumber;

    @Column(name = "seat_type", nullable = false, length = 50)
    private String seatType;

    @Column(name = "berth_level", length = 20)
    private String berthLevel;

    @Column(name = "extra_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal extraPrice;

    @Column(name = "active", nullable = false, columnDefinition = "bit default 1")
    @Builder.Default
    private Boolean active = true;

    /**
     * Admin tạm khóa ghế (hỏng, đang sửa...). Khách không đặt được ghế đang bảo trì.
     * Trường này độc lập với {@link #active} nên không bị ghi đè khi sinh lại ghế từ toa.
     */
    @Column(name = "under_maintenance", nullable = false, columnDefinition = "bit default 0")
    @Builder.Default
    private Boolean underMaintenance = false;

    @Builder.Default
    @OneToMany(mappedBy = "seat", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Ticket> tickets = new ArrayList<>();

    /** Ghế đang dùng được và không bảo trì: khách có thể chọn. */
    public boolean isBookable() {
        return Boolean.TRUE.equals(active) && !Boolean.TRUE.equals(underMaintenance);
    }

    @PrePersist
    @PreUpdate
    void applyDefaults() {
        if (active == null) {
            active = true;
        }
        if (underMaintenance == null) {
            underMaintenance = false;
        }
    }
}