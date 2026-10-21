package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationRepository
        extends JpaRepository<Station, Long> {
    Optional<Station> findByStationCode(String stationCode);

    boolean existsByStationCode(String stationCode);

    Optional<Station> findByStationName(String stationName);

    Page<Station>
    findByStationNameContainingIgnoreCaseOrStationCodeContainingIgnoreCase(
            String name,
            String code,
            Pageable pageable
    );
}
