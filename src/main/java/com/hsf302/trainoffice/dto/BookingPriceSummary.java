package com.hsf302.trainoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class BookingPriceSummary {
    private List<FareBreakdownItem> passengerItems;

    private BigDecimal passengerSubtotal;

    private Long groupDiscountPolicyId;
    private String groupDiscountPolicyName;
    private BigDecimal groupDiscountPercent;
    private BigDecimal groupDiscountAmount;

    private BigDecimal totalAmount;
}