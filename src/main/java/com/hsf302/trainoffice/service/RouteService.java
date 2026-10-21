package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Route;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface RouteService {

    List<Route> getAllRoutes();

    Page<Route> listAll(int pageNumber, String keyword);

    Optional<Route> getRouteById(Long id);

    Route saveRoute(Route route);

    void deleteRoute(Long id);
}