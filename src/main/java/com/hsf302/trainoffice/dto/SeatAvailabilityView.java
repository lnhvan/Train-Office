package com.hsf302.trainoffice.dto;

import com.hsf302.trainoffice.entity.Seat;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeatAvailabilityView {
    private Seat seat;
    private boolean available;
}
