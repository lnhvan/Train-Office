package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.BookingConfirmationView;
import com.hsf302.trainoffice.dto.BookingSession;
import com.hsf302.trainoffice.dto.CreateBookingRequest;
import com.hsf302.trainoffice.dto.PassengerInfoForm;
import com.hsf302.trainoffice.dto.SeatAvailabilityView;
import com.hsf302.trainoffice.dto.SeatHold;
import com.hsf302.trainoffice.dto.SeatSelectionForm;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.User;

import java.util.List;
import java.util.Optional;

public interface BookingFlowService {

    /**
     * Sơ đồ ghế của chặng. Ghế đã bán (DB) hoặc đang được người khác giữ (Redis) là không chọn được;
     * ghế {@code userId} đang giữ được đánh dấu {@code heldByMe}.
     */
    List<SeatAvailabilityView> getSeatAvailability(Long tripId,
                                                   Long departureStationId,
                                                   Long arrivalStationId,
                                                   Long userId);

    /** Khách bấm chọn một ghế trên sơ đồ: kiểm tra ghế rồi giữ trong Redis. */
    SeatHold holdSeat(User user,
                      Long tripId,
                      Long departureStationId,
                      Long arrivalStationId,
                      Long seatId,
                      int passengerCount);

    /** Khách bỏ chọn một ghế. */
    Optional<SeatHold> releaseSeat(User user, Long seatId);

    /** Ghế khách đang giữ (còn hạn). */
    Optional<SeatHold> findUserHold(User user);

    /** Khóa ghế của một đơn chờ thanh toán (còn hạn). */
    Optional<SeatHold> findBookingHold(Long bookingId);

    /** Bấm "Tiếp tục": các ghế gửi lên phải đúng các ghế khách đang giữ trong Redis. */
    BookingSession buildBookingSession(SeatSelectionForm form, User user);

    /**
     * Các bước sau chọn ghế: khóa của khách phải còn hạn và khớp phiên đặt vé.
     *
     * @throws com.hsf302.trainoffice.exception.SeatHoldException EXPIRED nếu đã hết hạn
     */
    SeatHold requireActiveHold(BookingSession bookingSession, User user);

    PassengerInfoForm createPassengerInfoForm(int passengerCount);

    PassengerInfoForm createPassengerInfoForm(int passengerCount, User user);

    void savePassengerInfo(BookingSession bookingSession, PassengerInfoForm form);

    void applyGroupDiscount(BookingSession bookingSession, Long groupDiscountPolicyId);

    BookingConfirmationView buildConfirmation(BookingSession bookingSession);

    CreateBookingRequest buildCreateBookingRequest(BookingSession bookingSession);

    /** Tạo đơn rồi chuyển khóa ghế trong Redis từ khách sang đơn (giữ nguyên thời gian còn lại). */
    Booking createBooking(BookingSession bookingSession, User user);
}