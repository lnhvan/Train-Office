package com.hsf302.trainoffice.dto;

public record TripTimelineItemDto(
        Long tripId,
        Long trainId,
        String trainCode,
        String trainName,
        Long routeId,
        String routeCode,
        String routeName,
        String departureTimeIso,
        String departureTimeFormatted,
        String arrivalTimeIso,
        String arrivalTimeFormatted,
        String durationFormatted,
        String status,
        String statusLabel
) {}
