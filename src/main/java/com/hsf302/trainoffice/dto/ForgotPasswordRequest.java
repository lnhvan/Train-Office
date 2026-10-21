package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "Email address should not be left blank.")
    @Email(message = "Invalid email")
    private String email;
}
