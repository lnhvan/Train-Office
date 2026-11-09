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

    /**
     * Tầng của giường: 1, 2, 3 (dựa theo seatNumber L1/L2/L3 hoặc coach berthsPerCompartment).
     */
    public int getTierLevel() {
        if (seatNumber != null) {
            if (seatNumber.contains("L1") || seatNumber.contains("-1") || seatNumber.endsWith("1A") || seatNumber.endsWith("1B")) {
                return 1;
            }
            if (seatNumber.contains("L2") || seatNumber.contains("-2") || seatNumber.endsWith("2A") || seatNumber.endsWith("2B")) {
                return 2;
            }
            if (seatNumber.contains("L3") || seatNumber.contains("-3") || seatNumber.endsWith("3A") || seatNumber.endsWith("3B")) {
                return 3;
            }
        }
        if ("LOWER".equalsIgnoreCase(berthLevel)) {
            return 1;
        }
        if ("MIDDLE".equalsIgnoreCase(berthLevel)) {
            return 2;
        }
        if ("UPPER".equalsIgnoreCase(berthLevel)) {
            if (coach != null && coach.getBerthsPerCompartment() != null && coach.getBerthsPerCompartment() == 6) {
                return 3;
            }
            return 2;
        }
        return 1;
    }

    public String getBerthLevelKey() {
        if (berthLevel == null) {
            return null;
        }
        int tier = getTierLevel();
        return switch (tier) {
            case 1 -> "booking.level1";
            case 2 -> "booking.level2";
            case 3 -> "booking.level3";
            default -> "booking.level" + tier;
        };
    }

    public String getBerthFullLabelKey() {
        if (berthLevel == null) {
            return null;
        }
        int tier = getTierLevel();
        if (tier == 1) {
            return "booking.level1LowerBerth";
        }
        if (tier == 2) {
            if (coach != null && coach.getBerthsPerCompartment() != null && coach.getBerthsPerCompartment() == 4) {
                return "booking.level2UpperBerth";
            }
            if ("UPPER".equalsIgnoreCase(berthLevel)) {
                return "booking.level2UpperBerth";
            }
            return "booking.level2MiddleBerth";
        }
        if (tier == 3) {
            return "booking.level3UpperBerth";
        }
        return "booking.level1LowerBerth";
    }
    public String getBerthCssClass() {
        if (berthLevel == null) {
            return "";
        }
        int tier = getTierLevel();
        if (tier == 1) {
            return "lower";
        }
        if (tier == 2) {
            if ("MIDDLE".equalsIgnoreCase(berthLevel)) {
                return "middle";
            }
            return "upper";
        }
        return "upper";
    }
}
