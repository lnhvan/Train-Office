package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class TripSearchForm {

    @NotNull(message = "{validation.departureStation.required}")
    private Long departureStationId;

    @NotNull(message = "{validation.arrivalStation.required}")
    private Long arrivalStationId;

    @NotNull(message = "{validation.departureDate.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate departureDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate returnDate;

    private Boolean roundTrip = false;

    @Min(value = 1, message = "{validation.passengerCount.min}")
    @Max(value = 10, message = "{validation.passengerCount.max}")
    private int passengerCount = 1;
}