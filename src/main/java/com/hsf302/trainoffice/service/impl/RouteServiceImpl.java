package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Route;
import com.hsf302.trainoffice.repository.RouteRepository;
import com.hsf302.trainoffice.service.RouteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RouteServiceImpl implements RouteService {

    private static final int ROUTES_PER_PAGE = 8;

    private final RouteRepository routeRepository;

    public RouteServiceImpl(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @Override
    public List<Route> getAllRoutes() {
        return routeRepository.findAll(Sort.by("routeCode").ascending());
    }

    @Override
    public Page<Route> listAll(int pageNumber, String keyword) {
        int safePageNumber = Math.max(pageNumber, 1);

        Pageable pageable = PageRequest.of(
                safePageNumber - 1,
                ROUTES_PER_PAGE,
                Sort.by("routeCode").ascending()
        );

        return routeRepository.searchRoutes(normalizeKeyword(keyword), pageable);
    }

    @Override
    public Optional<Route> getRouteById(Long id) {
        return routeRepository.findById(id);
    }

    @Override
    public Route saveRoute(Route route) {
        normalizeRoute(route);

        if (route.getRouteId() == null) {
            if (routeRepository.existsByRouteCode(route.getRouteCode())) {
                throw new IllegalStateException("Route code already exists!");
            }

        } else {
            Optional<Route> oldRoute = routeRepository.findById(route.getRouteId());

            if (oldRoute.isPresent()) {
                if (!oldRoute.get().getRouteCode().equals(route.getRouteCode())
                        && routeRepository.existsByRouteCode(route.getRouteCode())) {
                    throw new IllegalStateException("Route code already exists!");
                }
            }
        }

        return routeRepository.save(route);
    }

    @Override
    public void deleteRoute(Long id) {
        if (!routeRepository.existsById(id)) {
            throw new RuntimeException("Route not found");
        }

        routeRepository.deleteById(id);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private void normalizeRoute(Route route) {
        if (route.getRouteCode() != null) {
            route.setRouteCode(route.getRouteCode().trim().toUpperCase());
        }

        if (route.getRouteName() != null) {
            route.setRouteName(route.getRouteName().trim());
        }

        if (route.getDistanceKm() == null || route.getDistanceKm() < 0) {
            route.setDistanceKm(0.0);
        }
    }
}