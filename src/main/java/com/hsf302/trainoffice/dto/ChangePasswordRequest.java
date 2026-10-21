package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class ChangePasswordRequest {
    @NotBlank(message = "The current password cannot be left blank.")
    @Size(min = 6, message = "The current password must be 6 characters long.")
    private String currentPassword;

    @NotBlank(message = "The new password cannot be left blank.")
    @Size(min = 6, message = "The new password must be 6 characters long.")
    private String newPassword;

    @NotBlank(message = "Password confirmation cannot be left blank.")
    private String confirmNewPassword;
}
