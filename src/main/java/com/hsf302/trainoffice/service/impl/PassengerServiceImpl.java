package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Passenger;
import com.hsf302.trainoffice.repository.PassengerRepository;
import com.hsf302.trainoffice.service.PassengerService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PassengerServiceImpl implements PassengerService {
    private final PassengerRepository passengerRepository;

    public PassengerServiceImpl(PassengerRepository passengerRepository) {
        this.passengerRepository = passengerRepository;
    }

    @Override
    public List<Passenger> searchPassengers(String keyword) {
        return passengerRepository.search(blankToNull(keyword));
    }

    @Override
    public Optional<Passenger> getPassengerById(Long passengerId) {
        if (passengerId == null) {
            return Optional.empty();
        }
        return passengerRepository.findDetailByPassengerId(passengerId);
    }

    @Override
    public Passenger savePassenger(Passenger passenger) {
        validatePassenger(passenger);
        passenger.setFullName(passenger.getFullName().trim());
        passenger.setIdentityNumber(blankToNull(passenger.getIdentityNumber()));
        return passengerRepository.save(passenger);
    }

    @Override
    public void deletePassenger(Long passengerId) {
        passengerRepository.deleteById(passengerId);
    }

    private void validatePassenger(Passenger passenger) {
        if (passenger == null) {
            throw new IllegalArgumentException("Passenger is required.");
        }
        if (passenger.getFullName() == null || passenger.getFullName().isBlank()) {
            throw new IllegalArgumentException("Passenger full name is required.");
        }
        if (passenger.getFullName().length() > 100) {
            throw new IllegalArgumentException("Passenger full name is too long.");
        }
        if (passenger.getIdentityNumber() != null && passenger.getIdentityNumber().length() > 30) {
            throw new IllegalArgumentException("Identity number is too long.");
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
