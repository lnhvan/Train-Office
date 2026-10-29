package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.CccdOcrResult;
import org.springframework.web.multipart.MultipartFile;

public interface OcrService {

    /**
     * Runs Tesseract OCR on a photo of a Vietnamese Citizen ID card (CCCD)
     * and extracts full name, ID number, date of birth and gender.
     */
    CccdOcrResult recognizeCccd(MultipartFile image);
}
