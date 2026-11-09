package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.entity.Seat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMaintenanceResult {
    private Seat seat;
    private boolean underMaintenance;
    private int refundedTicketsCount;
    private BigDecimal refundedAmount;
    private int cancelledHoldsCount;
}
