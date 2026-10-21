package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.TripStation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripStationRepository extends JpaRepository<TripStation, Long> {
    @EntityGraph(attributePaths = {"station", "trainTrip"})
    Optional<TripStation> findByTrainTrip_TripIdAndStation_StationId(Long tripId, Long stationId);

    @EntityGraph(attributePaths = {"station", "trainTrip"})
    List<TripStation> findByTrainTrip_TripIdOrderByStationOrderAsc(Long tripId);

    boolean existsByTrainTrip_TripIdAndStation_StationId(Long tripId, Long stationId);

    boolean existsByTrainTrip_TripIdAndStationOrder(Long tripId, Integer stationOrder);
}
