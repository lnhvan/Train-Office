package com.hsf302.trainoffice.entity;

import com.hsf302.trainoffice.common.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "refunds",
        indexes = {
                @Index(name = "idx_refunds_code", columnList = "refund_code"),
                @Index(name = "idx_refunds_ticket", columnList = "ticket_id"),
                @Index(name = "idx_refunds_status", columnList = "refund_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    @EqualsAndHashCode.Include
    private Long refundId;

    @Column(name = "refund_code", unique = true, nullable = false, length = 30)
    private String refundCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    @ToString.Exclude
    private Ticket ticket;

    @Column(name = "refund_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", nullable = false, length = 500)
    private String refundReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 20)
    private RefundStatus refundStatus;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;


    @Column(name = "customer_name", length = 100, columnDefinition = "NVARCHAR(100)")
    private String customerName;

    @Column(name = "customer_email", length = 120)
    private String customerEmail;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder", length = 100, columnDefinition = "NVARCHAR(100)")
    private String bankAccountHolder;

    @PrePersist
    void onCreate() {
        if (refundStatus == null) {
            refundStatus = RefundStatus.PENDING;
        }
    }
}
