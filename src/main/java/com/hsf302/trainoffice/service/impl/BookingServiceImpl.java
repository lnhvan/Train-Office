package com.hsf302.trainoffice.service.impl;

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
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User is required");
        }
        return bookingRepository.findByUser_UserIdOrderByBookingDateDesc(user.getUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> findGuestBookings(String email, String phone) {
        if (email == null || email.isBlank() || phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Email and phone are required");
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
            throw new IllegalArgumentException("User is required");
        }
        Booking booking = getBookingById(bookingId);
        if (booking.getUser() == null || !booking.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("Booking does not belong to current user");
        }
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("Only pending payment bookings can be cancelled");
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
            throw new IllegalArgumentException("Booking does not belong to a guest");
        }
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("Only pending payment bookings can be cancelled");
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
            throw new IllegalArgumentException("One or more seats do not exist");
        }
        Map<Long, Seat> seatsById = fetchedSeats.stream()
                .collect(Collectors.toMap(Seat::getSeatId, Function.identity()));
        List<Seat> seats = new ArrayList<>();
        for (Long seatId : request.getSeatIds()) {
            seats.add(seatsById.get(seatId));
        }
        TrainTrip trainTrip = trainTripRepository.findById(request.getTrainTripId())
                .orElseThrow(() -> new IllegalArgumentException("Train trip does not exist"));
        Station departureStation = stationRepository.findById(request.getDepartureStationId())
                .orElseThrow(() -> new IllegalArgumentException("Departure station does not exist"));
        Station arrivalStation = stationRepository.findById(request.getArrivalStationId())
                .orElseThrow(() -> new IllegalArgumentException("Arrival station does not exist"));
        Segment segment = validateSegment(trainTrip, departureStation, arrivalStation);
        if (trainTrip.getStatus() != TripStatus.SCHEDULED) {
            throw new IllegalArgumentException("Train trip is not available for booking");
        }
        for (Long seatId : request.getSeatIds()) {
            Seat seat = seatsById.get(seatId);
            if (seat.getCoach() == null
                    || seat.getCoach().getTrain() == null
                    || !seat.getCoach().getTrain().getTrainId().equals(trainTrip.getTrain().getTrainId())) {
                throw new IllegalArgumentException("Seat does not belong to this train trip: " + seatId);
            }
            if (ticketRepository.existsActiveOverlappingSeatBooking(
                    seatId,
                    trainTrip.getTripId(),
                    segment.departureOrder,
                    segment.arrivalOrder,
                    ACTIVE_TICKET_STATUSES)) {
                throw new IllegalArgumentException("Seat already booked: " + seatId);
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
            throw new IllegalArgumentException("Booking request is required");
        }

        if (request.getTrainTripId() == null) {
            throw new IllegalArgumentException("Train trip is required");
        }

        if (request.getDepartureStationId() == null || request.getArrivalStationId() == null) {
            throw new IllegalArgumentException("Departure and arrival stations are required");
        }

        if (request.getDepartureStationId().equals(request.getArrivalStationId())) {
            throw new IllegalArgumentException("Departure station and arrival station must be different");
        }

        if (request.getBookerName() == null || request.getBookerName().isBlank()
                || request.getBookerPhone() == null || request.getBookerPhone().isBlank()
                || request.getBookerEmail() == null || request.getBookerEmail().isBlank()) {
            throw new IllegalArgumentException("Booker contact information is required");
        }

        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new IllegalArgumentException("Booking must have at least one passenger");
        }

        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            throw new IllegalArgumentException("Booking must have at least one seat");
        }

        if (request.getPassengers().size() != request.getSeatIds().size()) {
            throw new IllegalArgumentException("Passenger count must match selected seat count");
        }

        if (new HashSet<>(request.getSeatIds()).size() != request.getSeatIds().size()) {
            throw new IllegalArgumentException("Seat cannot duplicate within the same booking");
        }

        Set<String> identities = new HashSet<>();

        for (PassengerBookingRequest passenger : request.getPassengers()) {
            if (passenger.getFullName() == null || passenger.getFullName().isBlank()) {
                throw new IllegalArgumentException("Passenger full name is required");
            }

            validatePassengerAgeAndRelationship(passenger);

            if (passenger.getDateOfBirth() != null) {
                int age = Period.between(passenger.getDateOfBirth(), LocalDate.now()).getYears();

                if (age >= 16) {
                    if (passenger.getIdentityNumber() == null || passenger.getIdentityNumber().isBlank()) {
                        throw new IllegalArgumentException("Passengers from 16 years old must have identity number");
                    }

                    String identity = passenger.getIdentityNumber().trim();

                    if (!identities.add(identity)) {
                        throw new IllegalArgumentException("Passenger identity number cannot duplicate within the same booking");
                    }
                }
            }
        }
    }

    private Segment validateSegment(TrainTrip trainTrip, Station departureStation, Station arrivalStation) {
        TripStation departure = tripStationRepository
                .findByTrainTrip_TripIdAndStation_StationId(trainTrip.getTripId(), departureStation.getStationId())
                .orElseThrow(() -> new IllegalArgumentException("Departure station is not part of this train trip"));
        TripStation arrival = tripStationRepository
                .findByTrainTrip_TripIdAndStation_StationId(trainTrip.getTripId(), arrivalStation.getStationId())
                .orElseThrow(() -> new IllegalArgumentException("Arrival station is not part of this train trip"));
        if (departure.getStationOrder() == null || arrival.getStationOrder() == null
                || departure.getStationOrder() >= arrival.getStationOrder()) {
            throw new IllegalArgumentException("Departure station must be before arrival station on the same train trip");
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
            throw new IllegalArgumentException("Passenger date of birth is required");
        }

        LocalDate today = LocalDate.now();
        if (passenger.getDateOfBirth().isAfter(today)) {
            throw new IllegalArgumentException("Passenger date of birth cannot be in the future");
        }

        int age = Period.between(passenger.getDateOfBirth(), today).getYears();
        passenger.setPassengerType(discountPolicyService.resolvePassengerType(passenger.getDateOfBirth()));

        if (age >= 16) {
            if (passenger.getIdentityNumber() == null || passenger.getIdentityNumber().isBlank()) {
                throw new IllegalArgumentException("Passengers from 16 years old must have identity number");
            }
        }

        if (age < 16) {
            if (passenger.getRelationshipToBooker() == null
                    || passenger.getRelationshipToBooker().isBlank()) {
                throw new IllegalArgumentException("Passenger under 16 must have relationship to booker");
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
