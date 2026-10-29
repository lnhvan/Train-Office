package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.dto.CccdOcrResult;
import com.hsf302.trainoffice.service.OcrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    /**
     * Called from the passenger-info page (AJAX) to scan a CCCD photo and
     * return the extracted fields as JSON so the form can be auto-filled.
     */
    @PostMapping("/booking/ocr-cccd")
    public CccdOcrResult scanCccd(@RequestParam("image") MultipartFile image) {
        return ocrService.recognizeCccd(image);
    }
}
