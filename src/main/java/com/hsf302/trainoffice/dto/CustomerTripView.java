package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.entity.Station;
import com.hsf302.trainoffice.entity.TrainTrip;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomerTripView {

    private TrainTrip trainTrip;

    private Station departureStation;

    private Station arrivalStation;

    private Long departureStationId;

    private Long arrivalStationId;

    private boolean bookable;
}