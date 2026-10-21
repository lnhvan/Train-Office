package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.RouteStation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStationRepository extends JpaRepository<RouteStation, Long> {

    @EntityGraph(attributePaths = {"route", "station"})
    List<RouteStation> findByRoute_RouteIdOrderByStationOrderAsc(Long routeId);
}