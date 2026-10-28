package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.common.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateBookingRequest {
    @NotNull(message = "{validation.trainTrip.required}")
    private Long trainTripId;

    @NotNull(message = "{validation.departureStation.required}")
    private Long departureStationId;

    @NotNull(message = "{validation.arrivalStation.required}")
    private Long arrivalStationId;

    @NotBlank(message = "{validation.bookerName.required}")
    private String bookerName;

    @NotBlank(message = "{validation.bookerPhone.required}")
    private String bookerPhone;

    @NotBlank(message = "{validation.bookerEmail.required}")
    @Email(message = "{validation.bookerEmail.invalid}")
    private String bookerEmail;

    private PaymentMethod paymentMethod;

    private Long groupDiscountPolicyId;

    @Valid
    @NotEmpty(message = "{validation.passengers.min}")
    private List<PassengerBookingRequest> passengers = new ArrayList<>();

    @NotEmpty(message = "{validation.seats.min}")
    private List<Long> seatIds = new ArrayList<>();
}