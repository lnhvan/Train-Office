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

    @NotBlank(message = "{validation.fullName.required}")
    @Size(max = 100, message = "{validation.fullName.max}")
    @Pattern(
            regexp = "^[\\p{L}\\s]+$",
            message = "{validation.fullName.pattern}"
    )
    private String fullName;

    @Pattern(
            regexp = "^$|^\\d{9,12}$",
            message = "{validation.identity.pattern}"
    )
    private String identityNumber;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    private Gender gender;
}