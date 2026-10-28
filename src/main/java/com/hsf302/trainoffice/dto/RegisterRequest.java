package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "{validation.fullName.required}")
    @Size(max = 100, message = "{validation.fullName.max}")
    @Pattern(
            regexp = "^[\\p{L}\\s]+$",
            message = "{validation.fullName.pattern}"
    )
    private String fullName;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 6, message = "{validation.password.min}")
    @Size(max = 64, message = "{validation.password.max}")
    private String password;

    @NotBlank(message = "{validation.confirmPassword.required}")
    private String confirmPassword;
}
