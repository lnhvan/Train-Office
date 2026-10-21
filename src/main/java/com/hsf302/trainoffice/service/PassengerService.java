package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Passenger;

import java.util.List;
import java.util.Optional;

public interface PassengerService {
    List<Passenger> searchPassengers(String keyword);

    Optional<Passenger> getPassengerById(Long passengerId);

    Passenger savePassenger(Passenger passenger);

    void deletePassenger(Long passengerId);
}
