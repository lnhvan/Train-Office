package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.CreateBookingRequest;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Passenger;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.Station;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.PassengerRepository;
import com.hsf302.trainoffice.repository.SeatRepository;
import com.hsf302.trainoffice.repository.StationRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.repository.TripStationRepository;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.service.BookingPricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Period;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;
import com.hsf302.trainoffice.service.DiscountPolicyService;


@Service
public class BookingServiceImpl implements com.hsf302.trainoffice.service.BookingService {
    private static final List<TicketStatus> ACTIVE_TICKET_STATUSES = List.of(
            TicketStatus.BOOKED,
            TicketStatus.CONFIRMED
    );

    private final BookingRepository bookingRepository;
    private final PassengerRepository passengerRepository;
    private final SeatRepository seatRepository;
    private final StationRepository stationRepository;
    private final TicketRepository ticketRepository;
    private final TripStationRepository tripStationRepository;
    private final TrainTripRepository trainTripRepository;
    private final BookingPricingService bookingPricingService;
    private final DiscountPolicyService discountPolicyService;


    public BookingServiceImpl(BookingRepository bookingRepository,
                              PassengerRepository passengerRepository,
                              SeatRepository seatRepository,
                              StationRepository stationRepository,
                              TicketRepository ticketRepository,
                              TripStationRepository tripStationRepository,
                              TrainTripRepository trainTripRepository,
                              DiscountPolicyService discountPolicyService,
                              BookingPricingService bookingPricingService) {
        this.bookingRepository = bookingRepository;
        this.passengerRepository = passengerRepository;
        this.seatRepository = seatRepository;
        this.stationRepository = stationRepository;
        this.ticketRepository = ticketRepository;
        this.tripStationRepository = tripStationRepository;
        this.trainTripRepository = trainTripRepository;
        this.bookingPricingService = bookingPricingService;
        this.discountPolicyService = discountPolicyService;
    }

    @Override
    @Transactional
    public Booking createBookingForUser(CreateBookingRequest request, User user) {
        return createBooking(request, user);
    }

    @Override
    @Transactional
    public Booking createBookingForGuest(CreateBookingRequest request) {
        return createBooking(request, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Booking getBookingById(Long bookingId) {
        return bookingRepository.findBasicDetailsByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.bookingDoesNotExist")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException(I18n.t("err.userIsRequired"));
        }
        return bookingRepository.findByUser_UserIdOrderByBookingDateDesc(user.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findGuestBookings(String email, String phone) {
        if (email == null || email.isBlank() || phone == null || phone.isBlank()) {
            throw new IllegalArgumentException(I18n.t("err.emailAndPhoneAreRequired"));
        }
        return bookingRepository.findByUserIsNullAndBookerEmailIgnoreCaseAndBookerPhoneOrderByBookingDateDesc(
                email.trim(),
                phone.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Passenger> getPassengersForBooking(Long bookingId) {
        return passengerRepository.findByBooking_BookingIdOrderByPassengerIdAsc(bookingId);
    }

    @Override
    @Transactional
    public Booking cancelPendingBooking(Long bookingId, User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException(I18n.t("err.userIsRequired"));
        }
        Booking booking = getBookingById(bookingId);
        if (booking.getUser() == null || !booking.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException(I18n.t("err.bookingDoesNotBelongToCurrentUser"));
        }
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException(I18n.t("err.onlyPendingPaymentBookingsCanBeCancelled"));
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        List<Ticket> tickets = ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(bookingId);
        for (Ticket ticket : tickets) {
            ticket.setTicketStatus(TicketStatus.CANCELLED);
        }
        ticketRepository.saveAll(tickets);
        return bookingRepository.save(booking);
    }

    @Override
    @Transactional
    public Booking cancelPendingGuestBooking(Long bookingId) {
        Booking booking = getBookingById(bookingId);
        if (booking.getUser() != null) {
            throw new IllegalArgumentException(I18n.t("err.bookingDoesNotBelongToAGuest"));
        }
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException(I18n.t("err.onlyPendingPaymentBookingsCanBeCancelled"));
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        List<Ticket> tickets = ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(bookingId);
        for (Ticket ticket : tickets) {
            ticket.setTicketStatus(TicketStatus.CANCELLED);
        }
        ticketRepository.saveAll(tickets);
        return bookingRepository.save(booking);
    }

    private Booking createBooking(CreateBookingRequest request, User user) {
        validateRequest(request);
        List<Seat> fetchedSeats = seatRepository.findAllById(request.getSeatIds());
        if (fetchedSeats.size() != request.getSeatIds().size()) {
            throw new IllegalArgumentException(I18n.t("err.oneOrMoreSeatsDoNotExist"));
        }
        Map<Long, Seat> seatsById = fetchedSeats.stream()
                .collect(Collectors.toMap(Seat::getSeatId, Function.identity()));
        List<Seat> seats = new ArrayList<>();
        for (Long seatId : request.getSeatIds()) {
            seats.add(seatsById.get(seatId));
        }
        TrainTrip trainTrip = trainTripRepository.findById(request.getTrainTripId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainTripDoesNotExist")));
        Station departureStation = stationRepository.findById(request.getDepartureStationId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.departureStationDoesNotExist")));
        Station arrivalStation = stationRepository.findById(request.getArrivalStationId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.arrivalStationDoesNotExist")));
        Segment segment = validateSegment(trainTrip, departureStation, arrivalStation);
        if (trainTrip.getStatus() != TripStatus.SCHEDULED) {
            throw new IllegalArgumentException(I18n.t("err.trainTripIsNotAvailableForBooking"));
        }
        for (Long seatId : request.getSeatIds()) {
            Seat seat = seatsById.get(seatId);
            if (seat.getCoach() == null
                    || seat.getCoach().getTrain() == null
                    || !seat.getCoach().getTrain().getTrainId().equals(trainTrip.getTrain().getTrainId())) {
                throw new IllegalArgumentException(I18n.t("err.seatDoesNotBelongToThisTrain", seatId));
            }
            if (ticketRepository.existsActiveOverlappingSeatBooking(
                    seatId,
                    trainTrip.getTripId(),
                    segment.departureOrder,
                    segment.arrivalOrder,
                    ACTIVE_TICKET_STATUSES)) {
                throw new IllegalArgumentException(I18n.t("err.seatAlreadyBooked", seatId));
            }
        }

        Booking booking = new Booking();
        booking.setBookingNumber(generateBookingNumber());
        booking.setUser(user);
        booking.setTrainTrip(trainTrip);
        booking.setDepartureStation(departureStation);
        booking.setArrivalStation(arrivalStation);
        booking.setBookerName(request.getBookerName().trim());
        booking.setBookerPhone(request.getBookerPhone().trim());
        booking.setBookerEmail(request.getBookerEmail().trim());
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking.setBookingDate(LocalDateTime.now());
        booking.setTotalAmount(bookingPricingService.calculateTotal(
                booking.getTrainTrip(),
                seats,
                request.getPassengers(),
                request.getGroupDiscountPolicyId()
        ));
        booking = bookingRepository.save(booking);

        for (int i = 0; i < request.getPassengers().size(); i++) {
            Passenger passenger = toPassenger(request.getPassengers().get(i), booking);
            passenger = passengerRepository.save(passenger);

            Seat seat = seats.get(i);
            Ticket ticket = new Ticket();
            ticket.setTicketCode(booking.getBookingNumber() + "-T" + (i + 1));
            ticket.setBooking(booking);
            ticket.setPassenger(passenger);
            ticket.setTrainTrip(trainTrip);
            ticket.setSeat(seat);
            PassengerBookingRequest passengerRequest = request.getPassengers().get(i);

            ticket.setTicketPrice(bookingPricingService.ticketPrice(
                    booking.getTrainTrip(),
                    seat,
                    passengerRequest.getPassengerType()
            ));
            ticket.setTicketStatus(TicketStatus.BOOKED);
            ticketRepository.save(ticket);
        }

        return booking;
    }

    private void validateRequest(CreateBookingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(I18n.t("err.bookingRequestIsRequired"));
        }

        if (request.getTrainTripId() == null) {
            throw new IllegalArgumentException(I18n.t("err.trainTripIsRequired"));
        }

        if (request.getDepartureStationId() == null || request.getArrivalStationId() == null) {
            throw new IllegalArgumentException(I18n.t("err.departureAndArrivalStationsAreRequired"));
        }

        if (request.getDepartureStationId().equals(request.getArrivalStationId())) {
            throw new IllegalArgumentException(I18n.t("err.departureStationAndArrivalStationMustBe"));
        }

        if (request.getBookerName() == null || request.getBookerName().isBlank()
                || request.getBookerPhone() == null || request.getBookerPhone().isBlank()
                || request.getBookerEmail() == null || request.getBookerEmail().isBlank()) {
            throw new IllegalArgumentException(I18n.t("err.bookerContactInformationIsRequired"));
        }

        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new IllegalArgumentException(I18n.t("err.bookingMustHaveAtLeastOnePassenger"));
        }

        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            throw new IllegalArgumentException(I18n.t("err.bookingMustHaveAtLeastOneSeat"));
        }

        if (request.getPassengers().size() != request.getSeatIds().size()) {
            throw new IllegalArgumentException(I18n.t("err.passengerCountMustMatchSelectedSeatCount"));
        }

        if (new HashSet<>(request.getSeatIds()).size() != request.getSeatIds().size()) {
            throw new IllegalArgumentException(I18n.t("err.seatCannotDuplicateWithinTheSameBooking"));
        }

        Set<String> identities = new HashSet<>();

        for (PassengerBookingRequest passenger : request.getPassengers()) {
            if (passenger.getFullName() == null || passenger.getFullName().isBlank()) {
                throw new IllegalArgumentException(I18n.t("err.passengerFullNameIsRequired"));
            }

            validatePassengerAgeAndRelationship(passenger);

            if (passenger.getDateOfBirth() != null) {
                int age = Period.between(passenger.getDateOfBirth(), LocalDate.now()).getYears();

                if (age >= 16) {
                    if (passenger.getIdentityNumber() == null || passenger.getIdentityNumber().isBlank()) {
                        throw new IllegalArgumentException(I18n.t("err.passengersFrom16YearsOldMustHave"));
                    }

                    String identity = passenger.getIdentityNumber().trim();

                    if (!identities.add(identity)) {
                        throw new IllegalArgumentException(I18n.t("err.passengerIdentityNumberCannotDuplicateWithinThe"));
                    }
                }
            }
        }
    }

    private Segment validateSegment(TrainTrip trainTrip, Station departureStation, Station arrivalStation) {
        TripStation departure = tripStationRepository
                .findStop(trainTrip.getTripId(), departureStation.getStationId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.departureStationIsNotPartOfThis")));
        TripStation arrival = tripStationRepository
                .findStop(trainTrip.getTripId(), arrivalStation.getStationId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.arrivalStationIsNotPartOfThis")));
        if (departure.getStationOrder() == null || arrival.getStationOrder() == null
                || departure.getStationOrder() >= arrival.getStationOrder()) {
            throw new IllegalArgumentException(I18n.t("err.departureStationMustBeBeforeArrivalStation"));
        }
        return new Segment(departure.getStationOrder(), arrival.getStationOrder());
    }

    private Passenger toPassenger(PassengerBookingRequest request, Booking booking) {
        Passenger passenger = new Passenger();

        passenger.setBooking(booking);
        passenger.setFullName(request.getFullName().trim());

        if (request.getIdentityNumber() != null) {
            passenger.setIdentityNumber(request.getIdentityNumber().trim());
        }

        passenger.setDateOfBirth(request.getDateOfBirth());
        passenger.setGender(request.getGender());
        passenger.setPassengerType(request.getPassengerType());
        passenger.setRelationshipToBooker(request.getRelationshipToBooker());

        return passenger;
    }
    private void validatePassengerAgeAndRelationship(PassengerBookingRequest passenger) {
        if (passenger.getDateOfBirth() == null) {
            throw new IllegalArgumentException(I18n.t("err.passengerDateOfBirthIsRequired"));
        }

        LocalDate today = LocalDate.now();
        if (passenger.getDateOfBirth().isAfter(today)) {
            throw new IllegalArgumentException(I18n.t("err.passengerDateOfBirthCannotBeIn"));
        }

        int age = Period.between(passenger.getDateOfBirth(), today).getYears();
        passenger.setPassengerType(discountPolicyService.resolvePassengerType(passenger.getDateOfBirth()));

        if (age >= 16) {
            if (passenger.getIdentityNumber() == null || passenger.getIdentityNumber().isBlank()) {
                throw new IllegalArgumentException(I18n.t("err.passengersFrom16YearsOldMustHave"));
            }
        }

        if (age < 16) {
            if (passenger.getRelationshipToBooker() == null
                    || passenger.getRelationshipToBooker().isBlank()) {
                throw new IllegalArgumentException(I18n.t("err.passengerUnder16MustHaveRelationshipTo"));
            }
        }
    }


    private String generateBookingNumber() {
        String bookingNumber;
        do {
            bookingNumber = "RJ" + System.currentTimeMillis();
        } while (bookingRepository.existsByBookingNumber(bookingNumber));
        return bookingNumber;
    }

    private static class Segment {
        private final Integer departureOrder;
        private final Integer arrivalOrder;

        private Segment(Integer departureOrder, Integer arrivalOrder) {
            this.departureOrder = departureOrder;
            this.arrivalOrder = arrivalOrder;
        }
    }

    @Override
    @Transactional
    public int expirePendingBookingsOlderThan(int minutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(minutes);

        List<Booking> expiredBookings = bookingRepository.findByBookingStatusAndBookingDateBefore(
                BookingStatus.PENDING_PAYMENT,
                cutoff
        );

        for (Booking booking : expiredBookings) {
            booking.setBookingStatus(BookingStatus.CANCELLED);

            List<Ticket> tickets = ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(
                    booking.getBookingId()
            );

            for (Ticket ticket : tickets) {
                if (ticket.getTicketStatus() == TicketStatus.BOOKED) {
                    ticket.setTicketStatus(TicketStatus.CANCELLED);
                }
            }

            ticketRepository.saveAll(tickets);
        }

        bookingRepository.saveAll(expiredBookings);

        return expiredBookings.size();
    }
}
