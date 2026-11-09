package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.BookingHoldPolicy;
import java.time.LocalDateTime;

import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.BookingConfirmationView;
import com.hsf302.trainoffice.dto.BookingSession;
import com.hsf302.trainoffice.dto.CreateBookingRequest;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.dto.PassengerInfoForm;
import com.hsf302.trainoffice.dto.SeatAvailabilityView;
import com.hsf302.trainoffice.dto.SeatHold;
import com.hsf302.trainoffice.dto.SeatSelectionForm;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.exception.SeatHoldException;
import com.hsf302.trainoffice.service.BookingFlowService;
import com.hsf302.trainoffice.service.SeatHoldService;
import com.hsf302.trainoffice.service.BookingPricingService;
import com.hsf302.trainoffice.service.BookingService;
import com.hsf302.trainoffice.service.TicketService;
import com.hsf302.trainoffice.service.TrainTripService;
import com.hsf302.trainoffice.service.DiscountPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hsf302.trainoffice.dto.BookingPriceSummary;
import com.hsf302.trainoffice.entity.GroupDiscountPolicy;
import com.hsf302.trainoffice.service.GroupDiscountPolicyService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingFlowServiceImpl implements BookingFlowService {
    /** Giới hạn số ghế mỗi lần đặt (= số hành khách tối đa ở form tìm chuyến). */
    private static final int MAX_SEATS_PER_BOOKING = 10;

    private final TrainTripService trainTripService;
    private final TicketService ticketService;
    private final BookingService bookingService;
    private final BookingPricingService bookingPricingService;
    private final GroupDiscountPolicyService groupDiscountPolicyService;
    private final DiscountPolicyService discountPolicyService;
    private final SeatHoldService seatHoldService;

    public BookingFlowServiceImpl(TrainTripService trainTripService,
                                  TicketService ticketService,
                                  BookingService bookingService,
                                  BookingPricingService bookingPricingService,
                                  GroupDiscountPolicyService groupDiscountPolicyService,
                                  DiscountPolicyService discountPolicyService,
                                  SeatHoldService seatHoldService) {
        this.trainTripService = trainTripService;
        this.ticketService = ticketService;
        this.bookingService = bookingService;
        this.bookingPricingService = bookingPricingService;
        this.groupDiscountPolicyService = groupDiscountPolicyService;
        this.discountPolicyService = discountPolicyService;
        this.seatHoldService = seatHoldService;
    }

    @Override
    @Transactional
    public List<SeatAvailabilityView> getSeatAvailability(Long tripId,
                                                          Long departureStationId,
                                                          Long arrivalStationId,
                                                          Long userId) {
        try {
            // Hủy ngay các đơn đã hết khóa để ghế của họ hiện là trống (không chờ job mỗi phút).
            bookingService.expireBookingsWithoutHold(seatHoldService::isBookingHoldActive);
        } catch (SeatHoldException ignored) {
            // Redis lỗi: sơ đồ ghế bên dưới sẽ báo lỗi cho khách.
        }

        TripSegment segment = trainTripService.getValidSegment(tripId, departureStationId, arrivalStationId);

        Set<Long> soldSeatIds = ticketService.findBlockedSeatIds(
                tripId,
                segment.getDepartureOrder(),
                segment.getArrivalOrder()
        );

        List<Seat> seats = ticketService.getSeatsForTrip(tripId);
        Map<Long, String> owners = seatHoldService.findSeatOwners(
                tripId,
                segment.getDepartureOrder(),
                segment.getArrivalOrder(),
                seats.stream().map(Seat::getSeatId).toList());
        String me = userId == null ? null : SeatHoldService.userOwner(userId);

        return seats.stream()
                .map(seat -> {
                    String owner = owners.get(seat.getSeatId());
                    boolean heldByMe = me != null && me.equals(owner);
                    boolean available = seat.isBookable()
                            && !soldSeatIds.contains(seat.getSeatId())
                            && (owner == null || heldByMe);
                    return new SeatAvailabilityView(seat, available, heldByMe && available);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SeatHold holdSeat(User user,
                             Long tripId,
                             Long departureStationId,
                             Long arrivalStationId,
                             Long seatId,
                             int passengerCount) {
        requireUser(user);

        TripSegment segment = trainTripService.getValidSegment(tripId, departureStationId, arrivalStationId);
        Seat seat = ticketService.getSeatsForTrip(tripId).stream()
                .filter(item -> item.getSeatId().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.seatDoesNotBelongToThisTrain", seatId)));

        if (!seat.isBookable()) {
            throw new IllegalArgumentException(I18n.t("err.seatUnderMaintenance", seat.getSeatNumber()));
        }

        if (!ticketService.isSeatAvailable(seatId, tripId, segment.getDepartureOrder(), segment.getArrivalOrder())) {
            throw new SeatHoldException(SeatHoldException.Reason.SEAT_TAKEN, I18n.t("err.oneOrMoreSelectedSeatsAreNo"));
        }

        int maxSeats = Math.min(MAX_SEATS_PER_BOOKING, Math.max(1, passengerCount));

        return seatHoldService.holdSeat(
                user.getUserId(),
                tripId,
                departureStationId,
                arrivalStationId,
                segment.getDepartureOrder(),
                segment.getArrivalOrder(),
                seatId,
                maxSeats);
    }

    @Override
    public Optional<SeatHold> releaseSeat(User user, Long seatId) {
        requireUser(user);
        return seatHoldService.releaseSeat(user.getUserId(), seatId);
    }

    @Override
    public Optional<SeatHold> findUserHold(User user) {
        if (user == null || user.getUserId() == null) {
            return Optional.empty();
        }
        return seatHoldService.findUserHold(user.getUserId());
    }

    @Override
    public Optional<SeatHold> findBookingHold(Long bookingId) {
        return bookingId == null ? Optional.empty() : seatHoldService.findBookingHold(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingSession buildBookingSession(SeatSelectionForm form, User user) {
        requireUser(user);
        validateSelectedSeats(form, user);
        seatHoldService.requireUserHold(
                user.getUserId(),
                form.getTrainTripId(),
                form.getDepartureStationId(),
                form.getArrivalStationId(),
                form.getSeatIds());

        BookingSession bookingSession = new BookingSession();
        bookingSession.setTrainTripId(form.getTrainTripId());
        bookingSession.setDepartureStationId(form.getDepartureStationId());
        bookingSession.setArrivalStationId(form.getArrivalStationId());
        bookingSession.setPassengerCount(form.getPassengerCount());
        bookingSession.setSeatIds(new ArrayList<>(form.getSeatIds()));
        return bookingSession;
    }

    @Override
    public SeatHold requireActiveHold(BookingSession bookingSession, User user) {
        requireUser(user);
        if (bookingSession == null) {
            throw new SeatHoldException(SeatHoldException.Reason.EXPIRED, I18n.t("err.seatHoldExpired"));
        }
        return seatHoldService.requireUserHold(
                user.getUserId(),
                bookingSession.getTrainTripId(),
                bookingSession.getDepartureStationId(),
                bookingSession.getArrivalStationId(),
                bookingSession.getSeatIds());
    }

    @Override
    public PassengerInfoForm createPassengerInfoForm(int passengerCount) {
        PassengerInfoForm form = new PassengerInfoForm();
        for (int i = 0; i < passengerCount; i++) {
            form.getPassengers().add(new PassengerBookingRequest());
        }
        return form;
    }

    @Override
    public PassengerInfoForm createPassengerInfoForm(int passengerCount, User user) {
        PassengerInfoForm form = createPassengerInfoForm(passengerCount);

        if (user == null) {
            return form;
        }

        String contactName = firstNonBlank(user.getFullName(), user.getEmail());

        form.setContactName(contactName);
        form.setContactEmail(user.getEmail());
        form.setContactPhone("N/A");

        if (!form.getPassengers().isEmpty()) {
            PassengerBookingRequest firstPassenger = form.getPassengers().get(0);

            firstPassenger.setFullName(user.getFullName());
            firstPassenger.setIdentityNumber(user.getIdentityNumber());
            firstPassenger.setDateOfBirth(user.getDateOfBirth());
            firstPassenger.setGender(user.getGender());
        }

        return form;
    }

    @Override
    public void savePassengerInfo(BookingSession bookingSession, PassengerInfoForm form) {
        if (bookingSession == null) {
            throw new IllegalArgumentException(I18n.t("err.bookingSessionIsRequired"));
        }
        if (form.getPassengers().size() != bookingSession.getPassengerCount()) {
            throw new IllegalArgumentException(I18n.t("err.passengerCountMustMatchSelectedSeatCount"));
        }
        form.getPassengers().forEach(passenger -> passenger.setPassengerType(
                discountPolicyService.resolvePassengerType(passenger.getDateOfBirth())
        ));
        bookingSession.setPassengerInfo(form);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingConfirmationView buildConfirmation(BookingSession bookingSession) {
        validateReadyForConfirmation(bookingSession);

        TrainTrip trip = trainTripService.getTripById(bookingSession.getTrainTripId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainTripDoesNotExist")));

        TripSegment segment = trainTripService.getValidSegment(
                bookingSession.getTrainTripId(),
                bookingSession.getDepartureStationId(),
                bookingSession.getArrivalStationId()
        );

        validateSelectedSeatsStillAvailable(bookingSession, segment);

        List<Seat> selectedSeats = selectedSeatsInOrder(bookingSession);

        List<GroupDiscountPolicy> availableGroupDiscountPolicies = groupDiscountPolicyService
                .getActivePolicies()
                .stream()
                .filter(policy -> groupDiscountPolicyService.matchesPassengerCount(policy, selectedSeats.size()))
                .toList();

        BookingPriceSummary priceSummary = bookingPricingService.buildPriceSummary(
                trip,
                segment,
                selectedSeats,
                bookingSession.getPassengerInfo().getPassengers(),
                bookingSession.getSelectedGroupDiscountPolicyId()
        );

        return new BookingConfirmationView(
                bookingSession,
                bookingSession.getPassengerInfo(),
                trip,
                segment,
                selectedSeats,
                priceSummary.getTotalAmount(),
                priceSummary,
                availableGroupDiscountPolicies
        );
    }

    @Override
    public void applyGroupDiscount(BookingSession bookingSession, Long groupDiscountPolicyId) {
        validateReadyForConfirmation(bookingSession);

        if (groupDiscountPolicyId == null) {
            bookingSession.setSelectedGroupDiscountPolicyId(null);
            return;
        }

        GroupDiscountPolicy policy = groupDiscountPolicyService.getPolicyById(groupDiscountPolicyId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.selectedGroupDiscountPolicyDoesNotExist")));

        if (!Boolean.TRUE.equals(policy.getActive())) {
            throw new IllegalArgumentException(I18n.t("err.selectedGroupDiscountPolicyIsInactive"));
        }

        if (!groupDiscountPolicyService.matchesPassengerCount(policy, bookingSession.getPassengerCount())) {
            throw new IllegalArgumentException(I18n.t("err.selectedGroupDiscountPolicyDoesNotMatch"));
        }

        bookingSession.setSelectedGroupDiscountPolicyId(groupDiscountPolicyId);
    }

    @Override
    public CreateBookingRequest buildCreateBookingRequest(BookingSession bookingSession) {
        validateReadyForConfirmation(bookingSession);
        PassengerInfoForm passengerInfo = bookingSession.getPassengerInfo();
        CreateBookingRequest request = new CreateBookingRequest();
        request.setTrainTripId(bookingSession.getTrainTripId());
        request.setDepartureStationId(bookingSession.getDepartureStationId());
        request.setArrivalStationId(bookingSession.getArrivalStationId());
        request.setBookerName(passengerInfo.getContactName());
        request.setBookerPhone(passengerInfo.getContactPhone());
        request.setBookerEmail(passengerInfo.getContactEmail());
        request.setPassengers(passengerInfo.getPassengers());
        request.setSeatIds(bookingSession.getSeatIds());
        request.setGroupDiscountPolicyId(bookingSession.getSelectedGroupDiscountPolicyId());
        return request;
    }

    @Override
    public Booking createBooking(BookingSession bookingSession, User user) {
        if (user == null) {
            throw new IllegalArgumentException(I18n.t("err.pleaseLogInBeforeBooking"));
        }

        CreateBookingRequest request = buildCreateBookingRequest(bookingSession);

        // Khóa còn hạn và đúng ghế thì mới tạo đơn.
        requireActiveHold(bookingSession, user);

        Booking booking = bookingService.createBookingForUser(request, user);

        try {
            // Bấm đặt chỗ: có {@link BookingHoldPolicy#PAYMENT_WINDOW_MINUTES} phút để thanh toán kể từ lúc này.
            LocalDateTime payUntil = booking.getPaymentDeadline() != null
                    ? booking.getPaymentDeadline()
                    : BookingHoldPolicy.paymentDeadline(LocalDateTime.now());
            seatHoldService.transferToBooking(user.getUserId(), booking.getBookingId(), payUntil);
        } catch (SeatHoldException ex) {
            // Khóa vừa hết hạn đúng lúc tạo đơn: hủy đơn để không giữ ghế ngoài Redis.
            bookingService.cancelPendingBooking(booking.getBookingId(), user);
            throw ex;
        }

        return booking;
    }

    private void validateSelectedSeats(SeatSelectionForm form, User user) {
        if (form.getSeatIds().size() != form.getPassengerCount()
                || new HashSet<>(form.getSeatIds()).size() != form.getSeatIds().size()) {
            throw new IllegalArgumentException(I18n.t("err.pleaseSelectExactlySeats", form.getPassengerCount()));
        }
        TripSegment segment = trainTripService.getValidSegment(
                form.getTrainTripId(),
                form.getDepartureStationId(),
                form.getArrivalStationId());
        Set<Long> blockedSeatIds = ticketService.findBlockedSeatIds(
                form.getTrainTripId(),
                segment.getDepartureOrder(),
                segment.getArrivalOrder());
        Map<Long, Seat> validSeats = ticketService.getSeatsForTrip(form.getTrainTripId())
                .stream()
                .collect(Collectors.toMap(Seat::getSeatId, Function.identity()));
        for (Long seatId : form.getSeatIds()) {
            Seat seat = validSeats.get(seatId);
            if (seat == null) {
                throw new IllegalArgumentException(I18n.t("err.seatDoesNotBelongToThisTrain", seatId));
            }
            if (!seat.isBookable()) {
                throw new IllegalArgumentException(I18n.t("err.seatUnderMaintenance", seat.getSeatNumber()));
            }
            if (blockedSeatIds.contains(seatId)) {
                throw new IllegalArgumentException(I18n.t("err.oneOrMoreSelectedSeatsAreNo"));
            }
        }
    }

    private static void requireUser(User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException(I18n.t("err.pleaseLogInBeforeBooking"));
        }
    }

    private void validateReadyForConfirmation(BookingSession bookingSession) {
        if (bookingSession == null || bookingSession.getPassengerInfo() == null) {
            throw new IllegalArgumentException(I18n.t("err.pleaseCompleteBookingInformationFirst"));
        }
    }

    private void validateSelectedSeatsStillAvailable(BookingSession bookingSession, TripSegment segment) {
        Set<Long> blockedSeatIds = ticketService.findBlockedSeatIds(
                bookingSession.getTrainTripId(),
                segment.getDepartureOrder(),
                segment.getArrivalOrder());
        for (Long seatId : bookingSession.getSeatIds()) {
            if (blockedSeatIds.contains(seatId)) {
                throw new IllegalArgumentException(I18n.t("err.oneOrMoreSelectedSeatsAreNo"));
            }
        }
    }

    private List<Seat> selectedSeatsInOrder(BookingSession bookingSession) {
        Map<Long, Seat> seatsById = ticketService.getSeatsForTrip(bookingSession.getTrainTripId())
                .stream()
                .collect(Collectors.toMap(Seat::getSeatId, Function.identity()));
        List<Seat> selectedSeats = bookingSession.getSeatIds()
                .stream()
                .map(seatsById::get)
                .toList();
        if (selectedSeats.contains(null)) {
            throw new IllegalArgumentException(I18n.t("err.oneOrMoreSelectedSeatsDoNot"));
        }
        return selectedSeats;
    }

    private String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }
}
