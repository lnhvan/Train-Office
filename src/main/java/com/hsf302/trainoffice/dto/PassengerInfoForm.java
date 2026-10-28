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
    @NotBlank(message = "{validation.contactName.required}")
    private String contactName;

    @NotBlank(message = "{validation.contactPhone.required}")
    private String contactPhone;

    @NotBlank(message = "{validation.contactEmail.required}")
    @Email(message = "{validation.contactEmail.invalid}")
    private String contactEmail;

    @Valid
    @NotEmpty(message = "{validation.passengers.required}")
    private List<PassengerBookingRequest> passengers = new ArrayList<>();
}
