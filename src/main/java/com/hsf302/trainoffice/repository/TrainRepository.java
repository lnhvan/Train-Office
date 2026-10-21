package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Train;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface TrainRepository extends JpaRepository<Train, Long> {

    Optional<Train> findByTrainCode(String trainCode);

    boolean existsByTrainCode(String trainCode);

    @Query("select distinct t.trainType from Train t where t.trainType is not null")
    List<String> findDistinctTrainTypes();

    @Query("""
            select t
            from Train t
            where :keyword is null
               or lower(t.trainCode) like lower(concat('%', :keyword, '%'))
               or lower(t.trainName) like lower(concat('%', :keyword, '%'))
               or lower(t.trainType) like lower(concat('%', :keyword, '%'))
               or lower(str(t.status)) like lower(concat('%', :keyword, '%'))
            """)
    Page<Train> searchTrains(String keyword, Pageable pageable);
}
