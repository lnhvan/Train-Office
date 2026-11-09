package com.hsf302.trainoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMaintenanceCheckResult {
    private Long seatId;
    private String seatNumber;
    private String coachNumber;
    private String trainCode;
    private int totalTickets;
    private int confirmedCount;
    private int bookedCount;
    private BigDecimal totalRefundAmount;

    @Builder.Default
    private List<AffectedTicketDto> tickets = new ArrayList<>();
}
