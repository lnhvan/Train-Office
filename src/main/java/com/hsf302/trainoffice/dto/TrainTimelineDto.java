package com.hsf302.trainoffice.dto;

import java.util.List;

public record TrainTimelineDto(
        Long trainId,
        String trainCode,
        String trainName,
        String latestArrivalTimeIso,
        String latestArrivalTimeFormatted,
        Long latestTripId,
        boolean hasActiveTrips,
        List<TripTimelineItemDto> trips
) {}
