package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.common.enums.Region;
import com.hsf302.trainoffice.entity.Station;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface StationService {

    List<Station> getAllStations();
    Page<Station> listAll(int pageNum, String keyword);
    Page<Station> listAll(int pageNum, String keyword, Region region, Long provinceId);
    Station getStationById(Long id);
    Station createStation(Station station);
    Station updateStation(Long id, Station station);
    void deleteStation(Long id);
    boolean stationExists(String code);
    Optional<Station> findById(Long id);
}
