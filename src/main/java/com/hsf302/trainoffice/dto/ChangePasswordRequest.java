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
    @NotBlank(message = "{validation.currentPassword.required}")
    @Size(min = 6, message = "{validation.currentPassword.min}")
    private String currentPassword;

    @NotBlank(message = "{validation.newPassword.required}")
    @Size(min = 6, message = "{validation.newPassword.min}")
    @Size(max = 64, message = "{validation.password.max}")
    private String newPassword;

    @NotBlank(message = "{validation.confirmPassword.required}")
    private String confirmNewPassword;
}
