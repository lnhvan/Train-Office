package com.hsf302.trainoffice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundRequestForm {

    @NotBlank(message = "Customer name is required")
    @Size(min = 2, max = 100, message = "Customer name must be between 2 and 100 characters")
    private String customerName;

    @NotBlank(message = "Customer phone is required")
    @Pattern(
            regexp = "^(0|\\+84)[0-9]{9,10}$",
            message = "Customer phone must be a valid Vietnamese phone number"
    )
    private String customerPhone;

    @Email(message = "Customer email is invalid")
    @Size(max = 120, message = "Customer email must not exceed 120 characters")
    private String customerEmail;

    @NotBlank(message = "Bank name is required")
    @Size(min = 2, max = 100, message = "Bank name must be between 2 and 100 characters")
    private String bankName;

    @NotBlank(message = "Bank account number is required")
    @Pattern(
            regexp = "^[0-9]{6,30}$",
            message = "Bank account number must contain 6 to 30 digits"
    )
    private String bankAccountNumber;

    @NotBlank(message = "Bank account holder is required")
    @Pattern(
            regexp = "^[A-Za-zÀ-ỹ\\s'.-]{2,100}$",
            message = "Bank account holder name is invalid"
    )
    private String bankAccountHolder;

    @NotBlank(message = "Refund reason is required")
    @Size(min = 5, max = 500, message = "Refund reason must be between 5 and 500 characters")
    private String reason;
}