package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.entity.TrainTrip;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TripSearchResult {
    private TrainTrip trainTrip;
    private TripSegment segment;
    private int passengerCount;
}
