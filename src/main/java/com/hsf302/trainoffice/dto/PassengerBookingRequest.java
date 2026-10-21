package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.common.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class PassengerBookingRequest {

    @NotBlank(message = "Passenger full name is required")
    @Size(max = 100)
    private String fullName;

    @Size(max = 30)
    private String identityNumber;

    @NotNull(message = "Date of birth is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private Gender gender;

    private String passengerType = "DEFAULT";

    private String relationshipToBooker;
}
