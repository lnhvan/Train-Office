package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.common.BookingHoldPolicy;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.BookingPriceSummary;
import com.hsf302.trainoffice.dto.CreateBookingRequest;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.dto.TripSegment;
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
import com.hsf302.trainoffice.event.SeatHoldReleaseEvent;
import com.hsf302.trainoffice.service.BookingPricingService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.function.LongPredicate;
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
    private final ApplicationEventPublisher eventPublisher;


    public BookingServiceImpl(BookingRepository bookingRepository,
                              PassengerRepository passengerRepository,
                              SeatRepository seatRepository,
                              StationRepository stationRepository,
                              TicketRepository ticketRepository,
                              TripStationRepository tripStationRepository,
                              TrainTripRepository trainTripRepository,
                              DiscountPolicyService discountPolicyService,
                              BookingPricingService bookingPricingService,
                              ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.passengerRepository = passengerRepository;
        this.seatRepository = seatRepository;
        this.stationRepository = stationRepository;
        this.ticketRepository = ticketRepository;
        this.tripStationRepository = tripStationRepository;
        this.trainTripRepository = trainTripRepository;
        this.bookingPricingService = bookingPricingService;
        this.discountPolicyService = discountPolicyService;
        this.eventPublisher = eventPublisher;
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
        Booking saved = bookingRepository.save(booking);
        eventPublisher.publishEvent(SeatHoldReleaseEvent.booking(bookingId));
        return saved;
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
        Booking saved = bookingRepository.save(booking);
        eventPublisher.publishEvent(SeatHoldReleaseEvent.booking(bookingId));
        return saved;
    }

    private Booking createBooking(CreateBookingRequest request, User user) {
        validateRequest(request);
        if (user != null && user.getUserId() != null
                && bookingRepository.countByUser_UserIdAndBookingStatus(user.getUserId(), BookingStatus.PENDING_PAYMENT)
                >= BookingHoldPolicy.MAX_PENDING_BOOKINGS_PER_USER) {
            throw new IllegalArgumentException(I18n.t("err.tooManyPendingBookings",
                    BookingHoldPolicy.MAX_PENDING_BOOKINGS_PER_USER));
        }
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
        TripSegment segment = validateSegment(trainTrip, departureStation, arrivalStation);
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
            if (!seat.isBookable()) {
                throw new IllegalArgumentException(I18n.t("err.seatUnderMaintenance", seat.getSeatNumber()));
            }
            if (ticketRepository.existsActiveOverlappingSeatBooking(
                    seatId,
                    trainTrip.getTripId(),
                    segment.getDepartureOrder(),
                    segment.getArrivalOrder(),
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
        BookingPriceSummary priceSummary = bookingPricingService.buildPriceSummary(
                booking.getTrainTrip(),
                segment,
                seats,
                request.getPassengers(),
                request.getGroupDiscountPolicyId()
        );
        // Giá lưu trên từng vé = số tiền khách thực trả cho vé đó (đã chia giảm giá nhóm),
        // nên tổng giá vé = tổng tiền đơn và hoàn tiền theo giá vé không bao giờ vượt số đã trả.
        List<BigDecimal> paidPrices = bookingPricingService.allocatePaidPrices(priceSummary);

        booking.setTotalAmount(priceSummary.getTotalAmount());
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
            ticket.setTicketPrice(paidPrices.get(i));
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

    private TripSegment validateSegment(TrainTrip trainTrip, Station departureStation, Station arrivalStation) {
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
        return new TripSegment(departure, arrival);
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

    @Override
    @Transactional
    public List<Long> expireBookingsWithoutHold(LongPredicate holdActive) {
        // Đơn vừa tạo được miễn một chút để khóa Redis kịp chuyển từ khách sang đơn.
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(BookingHoldPolicy.NEW_BOOKING_GRACE_SECONDS);

        List<Booking> pendingBookings = bookingRepository.findByBookingStatusAndBookingDateBefore(
                BookingStatus.PENDING_PAYMENT,
                cutoff
        );

        List<Booking> expiredBookings = new ArrayList<>();

        for (Booking booking : pendingBookings) {
            // Khóa ghế trong Redis còn hạn (đang giữ chỗ / đang thanh toán VNPay): chưa hủy.
            if (holdActive.test(booking.getBookingId())) {
                continue;
            }

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
            expiredBookings.add(booking);
        }

        bookingRepository.saveAll(expiredBookings);

        List<Long> ids = expiredBookings.stream().map(Booking::getBookingId).toList();
        if (!ids.isEmpty()) {
            eventPublisher.publishEvent(SeatHoldReleaseEvent.bookings(ids));
        }
        return ids;
    }
}
