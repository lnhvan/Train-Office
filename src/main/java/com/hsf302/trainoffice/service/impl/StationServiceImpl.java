package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Station;
import com.hsf302.trainoffice.repository.StationRepository;
import com.hsf302.trainoffice.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StationServiceImpl implements StationService {

    public static final int STATIONS_PER_PAGE = 5;

    @Autowired
    private StationRepository stationRepository;

    @Override
    public List<Station> getAllStations() {
        return stationRepository.findAll();
    }

    @Override
    public Page<Station> listAll(int pageNum, String keyword) {
        Pageable pageable = PageRequest.of(pageNum - 1, STATIONS_PER_PAGE);

        if (keyword != null && !keyword.isEmpty()) {
            return stationRepository
                    .findByStationNameContainingIgnoreCaseOrStationCodeContainingIgnoreCase(
                            keyword,
                            keyword,
                            pageable
                    );
        }

        return stationRepository.findAll(pageable);
    }

    @Override
    public Station getStationById(Long id) {
        return stationRepository.findById(id).orElse(null);
    }

    @Override
    public Station createStation(Station station) {
        if (stationRepository.existsByStationCode(
                station.getStationCode())) {
            return null;
        }
        return stationRepository.save(station);
    }

    @Override
    public Station updateStation(Long id, Station station) {
        Station existing =
                stationRepository.findById(id).orElse(null);

        if (existing != null) {

            if (!existing.getStationCode().equals(station.getStationCode())
                    &&
                    stationRepository.existsByStationCode(
                            station.getStationCode())) {
                return null;
            }

            existing.setStationCode(station.getStationCode());
            existing.setStationName(station.getStationName());
            existing.setCity(station.getCity());

            return stationRepository.save(existing);
        }

        return null;
    }

    @Override
    public void deleteStation(Long id) {
        stationRepository.deleteById(id);
    }

    @Override
    public boolean stationExists(String code) {
        return stationRepository.existsByStationCode(code);
    }

    @Override
    public Optional<Station> findById(Long id) {
        return stationRepository.findById(id);
    }
}
