package com.hsf302.trainoffice.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Result returned to the passenger-info page after scanning a Vietnamese
 * Citizen ID card (CCCD) with Tesseract. All fields are best-effort: anything
 * the OCR could not recognize is left null so the user can fill it manually.
 */
@Data
public class CccdOcrResult {
    private boolean success;
    private String message;

    private String fullName;
    private String identityNumber;
    private LocalDate dateOfBirth;
    private String gender; // MALE / FEMALE / OTHER (matches Gender enum)

    /** Raw OCR text, handy for debugging / showing on screen. */
    private String rawText;

    public static CccdOcrResult failure(String message) {
        CccdOcrResult result = new CccdOcrResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
