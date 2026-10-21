package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.dto.BookingConfirmationView;
import com.hsf302.trainoffice.dto.BookingSession;
import com.hsf302.trainoffice.dto.CreateBookingRequest;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.dto.PassengerInfoForm;
import com.hsf302.trainoffice.dto.SeatAvailabilityView;
import com.hsf302.trainoffice.dto.SeatSelectionForm;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.service.BookingFlowService;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingFlowServiceImpl implements BookingFlowService {
    private final TrainTripService trainTripService;
    private final TicketService ticketService;
    private final BookingService bookingService;
    private final BookingPricingService bookingPricingService;
    private static final int PAYMENT_HOLD_MINUTES = 15;
    private final GroupDiscountPolicyService groupDiscountPolicyService;
    private final DiscountPolicyService discountPolicyService;

    public BookingFlowServiceImpl(TrainTripService trainTripService,
                                  TicketService ticketService,
                                  BookingService bookingService,
                                  BookingPricingService bookingPricingService,
                                  GroupDiscountPolicyService groupDiscountPolicyService,
                                  DiscountPolicyService discountPolicyService) {
        this.trainTripService = trainTripService;
        this.ticketService = ticketService;
        this.bookingService = bookingService;
        this.bookingPricingService = bookingPricingService;
        this.groupDiscountPolicyService = groupDiscountPolicyService;
        this.discountPolicyService = discountPolicyService;
    }

    @Override
    @Transactional
    public List<SeatAvailabilityView> getSeatAvailability(Long tripId,
                                                          Long departureStationId,
                                                          Long arrivalStationId) {
        bookingService.expirePendingBookingsOlderThan(PAYMENT_HOLD_MINUTES);

        TripSegment segment = trainTripService.getValidSegment(tripId, departureStationId, arrivalStationId);

        Set<Long> blockedSeatIds = ticketService.findBlockedSeatIds(
                tripId,
                segment.getDepartureOrder(),
                segment.getArrivalOrder()
        );

        return ticketService.getSeatsForTrip(tripId)
                .stream()
                .map(seat -> new SeatAvailabilityView(seat, !blockedSeatIds.contains(seat.getSeatId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingSession buildBookingSession(SeatSelectionForm form) {
        validateSelectedSeats(form);
        BookingSession bookingSession = new BookingSession();
        bookingSession.setTrainTripId(form.getTrainTripId());
        bookingSession.setDepartureStationId(form.getDepartureStationId());
        bookingSession.setArrivalStationId(form.getArrivalStationId());
        bookingSession.setPassengerCount(form.getPassengerCount());
        bookingSession.setSeatIds(new ArrayList<>(form.getSeatIds()));
        return bookingSession;
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
            throw new IllegalArgumentException("Booking session is required");
        }
        if (form.getPassengers().size() != bookingSession.getPassengerCount()) {
            throw new IllegalArgumentException("Passenger count must match selected seat count");
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
                .orElseThrow(() -> new IllegalArgumentException("Train trip does not exist"));

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
                .orElseThrow(() -> new IllegalArgumentException("Selected group discount policy does not exist."));

        if (!Boolean.TRUE.equals(policy.getActive())) {
            throw new IllegalArgumentException("Selected group discount policy is inactive.");
        }

        if (!groupDiscountPolicyService.matchesPassengerCount(policy, bookingSession.getPassengerCount())) {
            throw new IllegalArgumentException("Selected group discount policy does not match passenger count.");
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
            throw new IllegalArgumentException("Please log in before booking.");
        }

        CreateBookingRequest request = buildCreateBookingRequest(bookingSession);

        return bookingService.createBookingForUser(request, user);
    }

    private void validateSelectedSeats(SeatSelectionForm form) {
        if (form.getSeatIds().size() != form.getPassengerCount()
                || new HashSet<>(form.getSeatIds()).size() != form.getSeatIds().size()) {
            throw new IllegalArgumentException("Please select exactly " + form.getPassengerCount() + " seats");
        }
        TripSegment segment = trainTripService.getValidSegment(
                form.getTrainTripId(),
                form.getDepartureStationId(),
                form.getArrivalStationId());
        Set<Long> blockedSeatIds = ticketService.findBlockedSeatIds(
                form.getTrainTripId(),
                segment.getDepartureOrder(),
                segment.getArrivalOrder());
        Set<Long> validSeatIds = ticketService.getSeatsForTrip(form.getTrainTripId())
                .stream()
                .map(Seat::getSeatId)
                .collect(Collectors.toSet());
        for (Long seatId : form.getSeatIds()) {
            if (!validSeatIds.contains(seatId)) {
                throw new IllegalArgumentException("Seat does not belong to this train trip: " + seatId);
            }
            if (blockedSeatIds.contains(seatId)) {
                throw new IllegalArgumentException("One or more selected seats are no longer available");
            }
        }
    }

    private void validateReadyForConfirmation(BookingSession bookingSession) {
        if (bookingSession == null || bookingSession.getPassengerInfo() == null) {
            throw new IllegalArgumentException("Please complete booking information first");
        }
    }

    private void validateSelectedSeatsStillAvailable(BookingSession bookingSession, TripSegment segment) {
        Set<Long> blockedSeatIds = ticketService.findBlockedSeatIds(
                bookingSession.getTrainTripId(),
                segment.getDepartureOrder(),
                segment.getArrivalOrder());
        for (Long seatId : bookingSession.getSeatIds()) {
            if (blockedSeatIds.contains(seatId)) {
                throw new IllegalArgumentException("One or more selected seats are no longer available");
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
            throw new IllegalArgumentException("One or more selected seats do not exist");
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
