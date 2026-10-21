package com.hsf302.trainoffice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PassengerInfoForm {
    @NotBlank(message = "Contact name is required")
    private String contactName;

    @NotBlank(message = "Contact phone is required")
    private String contactPhone;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email is invalid")
    private String contactEmail;

    @Valid
    @NotEmpty(message = "Passenger list is required")
    private List<PassengerBookingRequest> passengers = new ArrayList<>();
}
