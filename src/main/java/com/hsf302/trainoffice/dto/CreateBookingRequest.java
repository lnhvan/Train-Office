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
    @NotNull(message = "Train trip is required")
    private Long trainTripId;

    @NotNull(message = "Departure station is required")
    private Long departureStationId;

    @NotNull(message = "Arrival station is required")
    private Long arrivalStationId;

    @NotBlank(message = "Booker name is required")
    private String bookerName;

    @NotBlank(message = "Booker phone is required")
    private String bookerPhone;

    @NotBlank(message = "Booker email is required")
    @Email(message = "Booker email is invalid")
    private String bookerEmail;

    private PaymentMethod paymentMethod;

    private Long groupDiscountPolicyId;

    @Valid
    @NotEmpty(message = "At least one passenger is required")
    private List<PassengerBookingRequest> passengers = new ArrayList<>();

    @NotEmpty(message = "At least one seat is required")
    private List<Long> seatIds = new ArrayList<>();
}