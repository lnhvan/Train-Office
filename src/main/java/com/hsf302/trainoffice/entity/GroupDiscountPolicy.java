package com.hsf302.trainoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "group_discount_policies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_group_discount_policy_code", columnNames = "policy_code")
        },
        indexes = {
                @Index(name = "idx_group_discount_policy_active", columnList = "active"),
                @Index(name = "idx_group_discount_policy_passengers", columnList = "min_passengers,max_passengers")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GroupDiscountPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    @EqualsAndHashCode.Include
    private Long policyId;

    @Column(name = "policy_code", nullable = false, length = 50)
    private String policyCode;

    @Column(name = "policy_name", nullable = false, length = 120, columnDefinition = "NVARCHAR(120)")
    private String policyName;

    @Column(name = "min_passengers", nullable = false)
    private Integer minPassengers;

    @Column(name = "max_passengers")
    private Integer maxPassengers;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 1;

    @Column(name = "description", length = 255, columnDefinition = "NVARCHAR(255)")
    private String description;
}