package com.hsf302.trainoffice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AffectedTicketDto {
    private Long ticketId;
    private String ticketCode;
    private Long bookingId;
    private String bookingNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String passengerName;
    private String trainCode;
    private String routeName;
    private String departureStationName;
    private String arrivalStationName;
    private LocalDateTime departureTime;
    private String departureTimeFormatted;
    private BigDecimal refundAmount;
    private String status; // CONFIRMED, BOOKED
}
