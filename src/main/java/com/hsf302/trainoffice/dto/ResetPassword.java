package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class ResetPassword {
    @NotBlank(message = "The password cannot be left blank.")
    @Size(min = 6, message = "The password must be 6 characters long.")
    private String password;

    @NotBlank(message = "Password confirmation cannot be left blank.")
    private String confirmPassword;
}
