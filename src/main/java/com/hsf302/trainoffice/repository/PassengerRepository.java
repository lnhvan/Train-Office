package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, Long> {
    Optional<Passenger> findFirstByBooking_BookingId(Long bookingId);

    List<Passenger> findByBooking_BookingIdOrderByPassengerIdAsc(Long bookingId);

    @Query("""
            select p from Passenger p
            left join fetch p.booking
            where p.passengerId = :passengerId
            """)
    Optional<Passenger> findDetailByPassengerId(@Param("passengerId") Long passengerId);

    @Query("""
            select p from Passenger p
            left join fetch p.booking b
            where (:keyword is null or lower(p.fullName) like lower(concat('%', :keyword, '%'))
                or lower(p.identityNumber) like lower(concat('%', :keyword, '%')))
            order by p.passengerId desc
            """)
    List<Passenger> search(@Param("keyword") String keyword);
}
