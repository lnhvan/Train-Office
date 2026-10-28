package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.common.enums.Region;
import com.hsf302.trainoffice.entity.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @EntityGraph(attributePaths = {"province"})
    @Query(value = """
            select s from Station s
            left join s.province p
            where (:keyword is null
                   or lower(s.stationCode) like lower(concat('%', :keyword, '%'))
                   or lower(s.stationName) like lower(concat('%', :keyword, '%'))
                   or lower(s.city) like lower(concat('%', :keyword, '%'))
                   or (p is not null and lower(p.provinceName) like lower(concat('%', :keyword, '%'))))
              and (:region is null or (p is not null and p.region = :region))
              and (:provinceId is null or (p is not null and p.provinceId = :provinceId))
            """,
           countQuery = """
            select count(s) from Station s
            left join s.province p
            where (:keyword is null
                   or lower(s.stationCode) like lower(concat('%', :keyword, '%'))
                   or lower(s.stationName) like lower(concat('%', :keyword, '%'))
                   or lower(s.city) like lower(concat('%', :keyword, '%'))
                   or (p is not null and lower(p.provinceName) like lower(concat('%', :keyword, '%'))))
              and (:region is null or (p is not null and p.region = :region))
              and (:provinceId is null or (p is not null and p.provinceId = :provinceId))
            """)
    Page<Station> searchStations(@Param("keyword") String keyword,
                                 @Param("region") Region region,
                                 @Param("provinceId") Long provinceId,
                                 Pageable pageable);
}
