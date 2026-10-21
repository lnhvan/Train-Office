package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RouteRepository extends JpaRepository<Route, Long> {

    boolean existsByRouteCode(String routeCode);

    @Query("""
            select r
            from Route r
            where :keyword is null
               or lower(r.routeCode) like lower(concat('%', :keyword, '%'))
               or lower(r.routeName) like lower(concat('%', :keyword, '%'))
               or str(r.distanceKm) like concat('%', :keyword, '%')
            """)
    Page<Route> searchRoutes(String keyword, Pageable pageable);
}