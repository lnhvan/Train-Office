package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPassword {
    /** Mã 6 số gửi qua email. */
    @NotBlank(message = "{validation.resetCode.required}")
    @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "{validation.resetCode.pattern}")
    private String code;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 6, message = "{validation.password.min}")
    @Size(max = 64, message = "{validation.password.max}")
    private String password;

    @NotBlank(message = "{validation.confirmPassword.required}")
    private String confirmPassword;
}
