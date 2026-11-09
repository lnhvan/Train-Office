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
import com.hsf302.trainoffice.common.enums.TripStatus;
import java.time.LocalDateTime;
import java.util.Collection;
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingNumber(String bookingNumber);

    boolean existsByBookingNumber(String bookingNumber);

    boolean existsByTrainTrip_TripId(Long tripId);

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

    long countByUser_UserIdAndBookingStatus(Long userId, BookingStatus bookingStatus);

    /**
     * Đặt chỗ thành công trên các chuyến chưa hoàn thành.
     * Chỉ tính booking có trạng thái PAID và chuyến tàu chưa COMPLETED.
     */
    @Query("""
            select count(b) from Booking b
            where b.bookingStatus in :statuses
              and b.trainTrip.status not in :excludedTripStatuses
            """)
    long countPaidBookingsOnActiveTrips(
            @org.springframework.data.repository.query.Param("statuses") Collection<BookingStatus> statuses,
            @org.springframework.data.repository.query.Param("excludedTripStatuses") Collection<TripStatus> excludedTripStatuses);

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

    @Query("""
            select b from Booking b
            left join fetch b.trainTrip tt
            left join fetch tt.train
            left join fetch tt.route
            left join fetch b.departureStation
            left join fetch b.arrivalStation
            where (:status is null or b.bookingStatus = :status)
              and (:keyword is null
                   or lower(b.bookingNumber) like lower(concat('%', :keyword, '%'))
                   or lower(b.bookerName) like lower(concat('%', :keyword, '%'))
                   or lower(b.bookerEmail) like lower(concat('%', :keyword, '%'))
                   or lower(b.bookerPhone) like lower(concat('%', :keyword, '%')))
            order by b.bookingDate desc
            """)
    List<Booking> searchBookingsAdmin(
            @org.springframework.data.repository.query.Param("status") BookingStatus status,
            @org.springframework.data.repository.query.Param("keyword") String keyword);
}
