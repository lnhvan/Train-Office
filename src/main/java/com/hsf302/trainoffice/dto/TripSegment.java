package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.entity.TripStation;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TripSegment {
    private TripStation departureStop;
    private TripStation arrivalStop;

    public Integer getDepartureOrder() {
        return departureStop.getStationOrder();
    }

    public Integer getArrivalOrder() {
        return arrivalStop.getStationOrder();
    }
}
