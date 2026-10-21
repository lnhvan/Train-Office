package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SeatSelectionForm {
    @NotNull
    private Long trainTripId;

    @NotNull
    private Long departureStationId;

    @NotNull
    private Long arrivalStationId;

    @Min(1)
    private int passengerCount;

    @NotEmpty
    private List<Long> seatIds = new ArrayList<>();
}
