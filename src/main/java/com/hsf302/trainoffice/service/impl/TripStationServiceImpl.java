package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Station;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.StationRepository;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.repository.TripStationRepository;
import com.hsf302.trainoffice.service.TripStationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TripStationServiceImpl implements TripStationService {
    private final TripStationRepository tripStationRepository;
    private final TrainTripRepository trainTripRepository;
    private final StationRepository stationRepository;

    public TripStationServiceImpl(TripStationRepository tripStationRepository,
                                  TrainTripRepository trainTripRepository,
                                  StationRepository stationRepository) {
        this.tripStationRepository = tripStationRepository;
        this.trainTripRepository = trainTripRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripStation> getStationsForTrip(Long tripId) {
        return tripStationRepository.findByTrainTrip_TripIdOrderByStationOrderAsc(tripId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TripStation> getTripStationById(Long id) {
        return tripStationRepository.findById(id);
    }

    @Override
    @Transactional
    public TripStation saveTripStation(Long tripId, TripStation tripStation) {
        TrainTrip trainTrip = trainTripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Train trip does not exist"));
        Station station = resolveStation(tripStation);
        validateUnique(tripId, null, station.getStationId(), tripStation.getStationOrder());
        tripStation.setTrainTrip(trainTrip);
        tripStation.setStation(station);
        return tripStationRepository.save(tripStation);
    }

    @Override
    @Transactional
    public TripStation updateTripStation(Long tripId, Long tripStationId, TripStation form) {
        TripStation existing = tripStationRepository.findById(tripStationId)
                .orElseThrow(() -> new IllegalArgumentException("Trip station does not exist"));
        if (!existing.getTrainTrip().getTripId().equals(tripId)) {
            throw new IllegalArgumentException("Trip station does not belong to this train trip");
        }
        Station station = resolveStation(form);
        validateUnique(tripId, tripStationId, station.getStationId(), form.getStationOrder());
        existing.setStation(station);
        existing.setStationOrder(form.getStationOrder());
        existing.setArrivalTime(form.getArrivalTime());
        existing.setDepartureTime(form.getDepartureTime());
        return tripStationRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteTripStation(Long tripId, Long tripStationId) {
        TripStation existing = tripStationRepository.findById(tripStationId)
                .orElseThrow(() -> new IllegalArgumentException("Trip station does not exist"));
        if (!existing.getTrainTrip().getTripId().equals(tripId)) {
            throw new IllegalArgumentException("Trip station does not belong to this train trip");
        }
        tripStationRepository.delete(existing);
    }

    private Station resolveStation(TripStation tripStation) {
        if (tripStation.getStation() == null || tripStation.getStation().getStationId() == null) {
            throw new IllegalArgumentException("Station is required");
        }
        if (tripStation.getStationOrder() == null) {
            throw new IllegalArgumentException("Station order is required");
        }
        return stationRepository.findById(tripStation.getStation().getStationId())
                .orElseThrow(() -> new IllegalArgumentException("Station does not exist"));
    }

    private void validateUnique(Long tripId, Long currentTripStationId, Long stationId, Integer stationOrder) {
        List<TripStation> existingStations = tripStationRepository.findByTrainTrip_TripIdOrderByStationOrderAsc(tripId);
        for (TripStation existing : existingStations) {
            if (currentTripStationId != null && currentTripStationId.equals(existing.getTripStationId())) {
                continue;
            }
            if (existing.getStation().getStationId().equals(stationId)) {
                throw new IllegalArgumentException("Station already exists in this train trip");
            }
            if (existing.getStationOrder().equals(stationOrder)) {
                throw new IllegalArgumentException("Station order already exists in this train trip");
            }
        }
    }
}
