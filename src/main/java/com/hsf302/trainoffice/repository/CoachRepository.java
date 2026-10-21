package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Coach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CoachRepository extends JpaRepository<Coach, Long> {

    boolean existsByTrain_TrainIdAndCoachNumber(Long trainId, String coachNumber);

    @Query("select distinct c.coachType from Coach c where c.coachType is not null")
    List<String> findDistinctCoachTypes();

    @EntityGraph(attributePaths = {"train"})
    @Query("""
            select c
            from Coach c
            where (:trainId is null or c.train.trainId = :trainId)
              and (
                    :keyword is null
                    or lower(c.coachNumber) like lower(concat('%', :keyword, '%'))
                    or lower(c.coachType) like lower(concat('%', :keyword, '%'))
                    or lower(c.train.trainCode) like lower(concat('%', :keyword, '%'))
                    or lower(c.train.trainName) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<Coach> searchCoaches(String keyword, Long trainId, Pageable pageable);
}
