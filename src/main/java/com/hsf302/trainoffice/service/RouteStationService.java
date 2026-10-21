package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.RouteStation;

import java.util.List;
import java.util.Optional;

public interface RouteStationService {

    List<RouteStation> getStationsForRoute(Long routeId);

    Optional<RouteStation> getRouteStationById(Long id);

    RouteStation saveRouteStation(Long routeId, RouteStation routeStation);

    RouteStation updateRouteStation(Long routeId, Long routeStationId, RouteStation routeStation);

    void deleteRouteStation(Long routeId, Long routeStationId);

    void reorderStations(Long routeId, List<Long> routeStationIds);
}