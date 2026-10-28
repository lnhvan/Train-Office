package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundRequestForm {

    @NotBlank(message = "{validation.customerName.required}")
    @Size(min = 2, max = 100, message = "{validation.customerName.size}")
    private String customerName;

    @NotBlank(message = "{validation.customerPhone.required}")
    @Pattern(
            regexp = "^(0|\\+84)[0-9]{9,10}$",
            message = "{validation.customerPhone.pattern}"
    )
    private String customerPhone;

    @Email(message = "{validation.customerEmail.invalid}")
    @Size(max = 120, message = "{validation.customerEmail.max}")
    private String customerEmail;

    @NotBlank(message = "{validation.bankName.required}")
    @Size(min = 2, max = 100, message = "{validation.bankName.size}")
    private String bankName;

    @NotBlank(message = "{validation.bankAccount.required}")
    @Pattern(
            regexp = "^[0-9]{6,30}$",
            message = "{validation.bankAccount.pattern}"
    )
    private String bankAccountNumber;

    @NotBlank(message = "{validation.bankHolder.required}")
    @Pattern(
            regexp = "^[A-Za-zÀ-ỹ\\s'.-]{2,100}$",
            message = "{validation.bankHolder.pattern}"
    )
    private String bankAccountHolder;

    @NotBlank(message = "{validation.refundReason.required}")
    @Size(min = 5, max = 500, message = "{validation.refundReason.size}")
    private String reason;
}