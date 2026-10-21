package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Coach;
import com.hsf302.trainoffice.entity.Seat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    boolean existsByCoachAndSeatNumber(Coach coach, String seatNumber);

    boolean existsByCoach_CoachIdAndSeatNumber(Long coachId, String seatNumber);

    List<Seat> findByCoach_CoachIdOrderBySeatNumberAsc(Long coachId);

    long countByCoach_CoachId(Long coachId);

    @EntityGraph(attributePaths = {"coach", "coach.train", "compartment"})
    @Query("""
        select s
        from Seat s
             join s.coach c
             join c.train t
             left join s.compartment cp
        where (:trainId is null or t.trainId = :trainId)
          and (:coachId is null or c.coachId = :coachId)
          and (
                :keyword is null
                or lower(s.seatNumber) like lower(concat('%', :keyword, '%'))
                or lower(s.seatType) like lower(concat('%', :keyword, '%'))
                or lower(s.berthLevel) like lower(concat('%', :keyword, '%'))
                or lower(c.coachNumber) like lower(concat('%', :keyword, '%'))
                or lower(t.trainCode) like lower(concat('%', :keyword, '%'))
                or lower(cp.compartmentNumber) like lower(concat('%', :keyword, '%'))
                or lower(str(s.active)) like lower(concat('%', :keyword, '%'))
          )
        order by t.trainCode asc,
                 c.coachNumber asc,
                 cp.compartmentNumber asc,
                 s.seatNumber asc
        """)
    Page<Seat> searchSeats(String keyword, Long trainId, Long coachId, Pageable pageable);

    @Query("""
            select s
            from Seat s
                 join fetch s.coach c
                 left join fetch s.compartment cp
            where c.train.trainId = :trainId
              and s.active = true
            order by c.coachNumber asc, cp.compartmentNumber asc, s.seatNumber asc
            """)
    List<Seat> findActiveSeatsByTrainId(@Param("trainId") Long trainId);

    @Query("""
            select s
            from Seat s
                 join fetch s.coach c
                 left join fetch s.compartment cp
            where c.train.trainId = :trainId
            order by c.coachNumber asc, cp.compartmentNumber asc, s.seatNumber asc
            """)
    List<Seat> findSeatsByTrainId(@Param("trainId") Long trainId);

    long countByCompartment_CompartmentId(Long compartmentId);
}
