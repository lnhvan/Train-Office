package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class TripSearchForm {

    @NotNull(message = "Departure station is required")
    private Long departureStationId;

    @NotNull(message = "Arrival station is required")
    private Long arrivalStationId;

    @NotNull(message = "Departure date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate departureDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate returnDate;

    private Boolean roundTrip = false;

    @Min(value = 1, message = "Passenger count must be greater than 0")
    private int passengerCount = 1;
}