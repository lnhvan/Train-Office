package com.hsf302.trainoffice.repository;

import com.hsf302.trainoffice.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import com.hsf302.trainoffice.common.enums.BookingStatus;
import java.time.LocalDateTime;
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingNumber(String bookingNumber);

    boolean existsByBookingNumber(String bookingNumber);

    @Query("select b from Booking b where b.bookingNumber = ?1")
    Optional<Booking> findByBookingCode(String bookingCode);

    @Query("select count(b) > 0 from Booking b where b.bookingNumber = ?1")
    boolean existsByBookingCode(String bookingCode);

    @EntityGraph(attributePaths = {
            "trainTrip",
            "trainTrip.train",
            "trainTrip.route",
            "departureStation",
            "arrivalStation"
    })
    List<Booking> findByUser_UserIdOrderByBookingDateDesc(Long userId);

    @EntityGraph(attributePaths = {
            "trainTrip",
            "trainTrip.train",
            "trainTrip.route",
            "departureStation",
            "arrivalStation"
    })
    List<Booking> findByUserIsNullAndBookerEmailIgnoreCaseAndBookerPhoneOrderByBookingDateDesc(String bookerEmail,
                                                                                               String bookerPhone);

    @EntityGraph(attributePaths = {
            "user",
            "trainTrip",
            "trainTrip.train",
            "trainTrip.route",
            "departureStation",
            "arrivalStation"
    })
    Optional<Booking> findBasicDetailsByBookingId(Long bookingId);
    List<Booking> findByBookingStatusAndBookingDateBefore(BookingStatus bookingStatus,
                                                          LocalDateTime bookingDate);
    long countByBookingStatus(BookingStatus bookingStatus);

    List<Booking> findTop5ByOrderByBookingDateDesc();

    @EntityGraph(attributePaths = {
            "user",
            "trainTrip",
            "trainTrip.train",
            "trainTrip.route",
            "departureStation",
            "arrivalStation"
    })
    Page<Booking> findAllByOrderByBookingDateDesc(Pageable pageable);
}
