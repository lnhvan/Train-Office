package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.dto.StationAvailability;
import com.hsf302.trainoffice.service.TrainTripService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * API JSON cho trang tìm chuyến (gọi bằng fetch từ static/js/rail-search.js).
 */
@RestController
@RequestMapping("/api/booking")
public class BookingApiController {

    private final TrainTripService trainTripService;

    public BookingApiController(TrainTripService trainTripService) {
        this.trainTripService = trainTripService;
    }

    /**
     * Ví dụ: GET /api/booking/station-availability?departureStationId=1&date=2026-10-24
     * -> { "departureStationIds": [...], "arrivalStationIds": [...], "availableDates": ["2026-10-24", ...] }
     */
    @GetMapping("/station-availability")
    public StationAvailability stationAvailability(
            @RequestParam(required = false) Long departureStationId,
            @RequestParam(required = false) Long arrivalStationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return trainTripService.getStationAvailability(departureStationId, arrivalStationId, date);
    }
}
