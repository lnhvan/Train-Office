package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.common.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class ProfileForm {

    private Long passengerId;

    @NotBlank(message = "The full name cannot be left blank!")
    @Size(max = 100, message = "Full name (maximum 100 characters)")
    @Pattern(
            regexp = "^[\\p{L}\\s]+$",
            message = "Full names can only contain letters and spaces."
    )
    private String fullName;

    @Pattern(
            regexp = "^$|^\\d{9,12}$",
            message = "Citizen ID/National ID card must consist of 9-12 digits."
    )
    private String identityNumber;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    private Gender gender;
}