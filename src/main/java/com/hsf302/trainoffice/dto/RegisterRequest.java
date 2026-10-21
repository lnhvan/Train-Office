package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "The full name cannot be left blank!")
    @Size(max = 100, message = "Full name (maximum 100 characters)")
    @Pattern(
            regexp = "^[\\p{L}\\s]+$",
            message = "Full names can only contain letters and spaces."
    )
    private String fullName;

    @NotBlank(message = "Email address should not be left blank.")
    @Email(message = "Invalid email")
    private String email;

    @NotBlank(message = "The password cannot be left blank.")
    @Size(min = 6, message = "The password must be 6 characters long.")
    private String password;

    @NotBlank(message = "Password confirmation cannot be left blank.")
    private String confirmPassword;
}
