package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.CustomerTripView;
import com.hsf302.trainoffice.dto.StationAvailability;
import com.hsf302.trainoffice.dto.TripCancellationResult;
import com.hsf302.trainoffice.dto.TripSearchForm;
import com.hsf302.trainoffice.dto.TripSchedulePreview;
import com.hsf302.trainoffice.dto.TripSearchResult;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Route;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.RouteRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.repository.TrainRepository;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.repository.TripStationRepository;
import com.hsf302.trainoffice.service.BookingPricingService;
import com.hsf302.trainoffice.event.SeatHoldReleaseEvent;
import com.hsf302.trainoffice.service.RefundService;
import org.springframework.context.ApplicationEventPublisher;
import com.hsf302.trainoffice.service.TrainTripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TrainTripServiceImpl implements TrainTripService {
    private final TrainTripRepository trainTripRepository;
    private final TripStationRepository tripStationRepository;
    private final TrainRepository trainRepository;
    private final RouteRepository routeRepository;
    private final BookingRepository bookingRepository;
    private final TripScheduleBuilder tripScheduleBuilder;
    private final BookingPricingService bookingPricingService;
    private final TicketRepository ticketRepository;
    private final RefundService refundService;
    private final ApplicationEventPublisher eventPublisher;

    public TrainTripServiceImpl(TrainTripRepository trainTripRepository,
                                TripStationRepository tripStationRepository,
                                TrainRepository trainRepository,
                                RouteRepository routeRepository,
                                BookingRepository bookingRepository,
                                TripScheduleBuilder tripScheduleBuilder,
                                BookingPricingService bookingPricingService,
                                TicketRepository ticketRepository,
                                RefundService refundService,
                                ApplicationEventPublisher eventPublisher) {
        this.trainTripRepository = trainTripRepository;
        this.tripStationRepository = tripStationRepository;
        this.trainRepository = trainRepository;
        this.routeRepository = routeRepository;
        this.bookingRepository = bookingRepository;
        this.tripScheduleBuilder = tripScheduleBuilder;
        this.bookingPricingService = bookingPricingService;
        this.ticketRepository = ticketRepository;
        this.refundService = refundService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainTrip> getAllTrips() {
        return trainTripRepository.findAll();
    }
    @Override
    @Transactional(readOnly = true)
    public List<TrainTrip> filterTrips(String keyword, Long trainId, Long routeId, TripStatus status, LocalDate departureDate) {
        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        LocalDateTime startOfDay = departureDate != null ? departureDate.atStartOfDay() : null;
        LocalDateTime endOfDay = departureDate != null ? departureDate.plusDays(1).atStartOfDay() : null;

        return trainTripRepository.searchTrips(cleanKeyword, trainId, routeId, status, startOfDay, endOfDay);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TrainTrip> getTripById(Long id) {
        return trainTripRepository.findByTripId(id);
    }

    @Override
    @Transactional
    public TrainTrip saveTrip(TrainTrip trainTrip) {
        boolean isNew = trainTrip.getTripId() == null;
        attachTrainAndRoute(trainTrip);
        requireDepartureTime(trainTrip);

        // Giờ đến được tính từ lịch dừng; tạm đặt bằng giờ đi để lưu được chuyến trước.
        if (trainTrip.getArrivalTime() == null) {
            trainTrip.setArrivalTime(trainTrip.getDepartureTime());
        }

        TrainTrip saved = trainTripRepository.save(trainTrip);

        // Chuyến mới: tự sinh lịch dừng từ các ga của tuyến và tính giờ theo tốc độ tàu.
        if (isNew) {
            tripScheduleBuilder.addMissingStops(saved);
            saved = trainTripRepository.save(saved);
        }

        return saved;
    }

    @Override
    @Transactional
    public TrainTrip updateTrip(Long id, TrainTrip form) {
        TrainTrip existing = trainTripRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainTripDoesNotExist")));
        Long oldRouteId = existing.getRoute() != null ? existing.getRoute().getRouteId() : null;
        Long oldTrainId = existing.getTrain() != null ? existing.getTrain().getTrainId() : null;
        LocalDateTime oldDeparture = existing.getDepartureTime();

        // Hủy chuyến phải đi qua nút "Hủy chuyến" để hoàn tiền vé đã bán, không đổi trạng thái ở form sửa.
        if (form.getStatus() == TripStatus.CANCELLED && existing.getStatus() != TripStatus.CANCELLED) {
            throw new IllegalArgumentException(I18n.t("err.useCancelTripButtonToCancel"));
        }

        existing.setTrain(form.getTrain());
        existing.setRoute(form.getRoute());
        existing.setDepartureTime(form.getDepartureTime());
        existing.setBasePrice(form.getBasePrice());
        existing.setStatus(form.getStatus());
        attachTrainAndRoute(existing);
        requireDepartureTime(existing);

        boolean routeChanged = !existing.getRoute().getRouteId().equals(oldRouteId);
        boolean trainChanged = !existing.getTrain().getTrainId().equals(oldTrainId);
        boolean departureChanged = !existing.getDepartureTime().equals(oldDeparture);

        if (routeChanged) {
            // Lịch dừng cũ thuộc tuyến cũ -> phải bỏ và sinh lại theo tuyến mới.
            if (bookingRepository.existsByTrainTrip_TripId(id)) {
                throw new IllegalArgumentException(I18n.t("err.cannotChangeRouteOfBookedTrip"));
            }

            tripStationRepository.deleteAll(tripStationRepository.findStopsOfTrip(id));
            tripStationRepository.flush();
        }

        TrainTrip saved = trainTripRepository.save(existing);

        if (routeChanged) {
            tripScheduleBuilder.addMissingStops(saved);
        } else if (trainChanged || departureChanged) {
            // Đổi giờ khởi hành hoặc đổi tàu (khác tốc độ): tính lại giờ mọi ga.
            tripScheduleBuilder.recalculateSchedule(saved);
        }

        return trainTripRepository.save(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TripSchedulePreview previewSchedule(Long trainId, Long routeId, LocalDateTime departureTime) {
        if (trainId == null) {
            throw new IllegalArgumentException(I18n.t("err.trainIsRequired"));
        }
        if (routeId == null) {
            throw new IllegalArgumentException(I18n.t("err.routeIsRequired"));
        }
        if (departureTime == null) {
            throw new IllegalArgumentException(I18n.t("err.tripDepartureTimeRequired"));
        }

        Train train = trainRepository.findById(trainId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainDoesNotExist")));
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.routeDoesNotExist")));

        return tripScheduleBuilder.preview(train, route, departureTime);
    }

    private void requireDepartureTime(TrainTrip trainTrip) {
        if (trainTrip.getDepartureTime() == null) {
            throw new IllegalArgumentException(I18n.t("err.tripDepartureTimeRequired"));
        }
    }

    @Override
    @Transactional
    public TripCancellationResult cancelTrip(Long id) {
        TrainTrip trainTrip = trainTripRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainTripDoesNotExist")));

        if (trainTrip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalArgumentException(I18n.t("err.tripAlreadyCancelled"));
        }

        trainTrip.setStatus(TripStatus.CANCELLED);
        trainTripRepository.save(trainTrip);

        // Vé đã thanh toán: tự tạo yêu cầu hoàn 100% và đưa về COMPLETED (trừ ví). Ví không đủ thì hủy cả thao tác.
        TripCancellationResult refunds = refundService.refundCancelledTrip(id);

        // Vé đang giữ chỗ / đang thanh toán: hủy luôn. Nếu VNPay vẫn báo thành công sau đó,
        // PaymentService sẽ tạo yêu cầu hoàn tiền cho khách.
        List<Ticket> heldTickets = ticketRepository.findByTripAndStatuses(id, List.of(TicketStatus.BOOKED));
        Map<Long, Booking> pendingBookings = new LinkedHashMap<>();

        for (Ticket ticket : heldTickets) {
            ticket.setTicketStatus(TicketStatus.CANCELLED);

            Booking booking = ticket.getBooking();
            if (booking.getBookingStatus() == BookingStatus.PENDING_PAYMENT) {
                pendingBookings.put(booking.getBookingId(), booking);
            }
        }

        ticketRepository.saveAll(heldTickets);

        pendingBookings.values().forEach(booking -> booking.setBookingStatus(BookingStatus.CANCELLED));
        bookingRepository.saveAll(pendingBookings.values());

        // Sau khi commit: xóa mọi khóa ghế của chuyến trong Redis (khách đang chọn ghế, đơn đang thanh toán).
        eventPublisher.publishEvent(SeatHoldReleaseEvent.trip(id, List.copyOf(pendingBookings.keySet())));

        return new TripCancellationResult(refunds.refundedTickets(), refunds.refundedAmount(), heldTickets.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripSearchResult> searchTrips(TripSearchForm form) {
        if (form.getDepartureStationId() == null || form.getArrivalStationId() == null) {
            throw new IllegalArgumentException(I18n.t("err.departureAndArrivalStationsAreRequired"));
        }
        if (form.getDepartureStationId().equals(form.getArrivalStationId())) {
            throw new IllegalArgumentException(I18n.t("err.departureStationAndArrivalStationMustBe"));
        }
        if (form.getDepartureDate() == null) {
            throw new IllegalArgumentException(I18n.t("err.departureDateIsRequired"));
        }
        if (form.getPassengerCount() <= 0) {
            throw new IllegalArgumentException(I18n.t("err.passengerCountMustBeGreaterThan0"));
        }

        LocalDateTime start = form.getDepartureDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return trainTripRepository.findScheduledTripsForSegment(
                        form.getDepartureStationId(),
                        form.getArrivalStationId(),
                        start,
                        end,
                        TripStatus.SCHEDULED)
                .stream()
                .map(trip -> new TripSearchResult(
                        trip,
                        getValidSegment(trip.getTripId(), form.getDepartureStationId(), form.getArrivalStationId()),
                        form.getPassengerCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TripSegment getValidSegment(Long tripId, Long departureStationId, Long arrivalStationId) {
        TripStation departure = tripStationRepository
                .findStop(tripId, departureStationId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.departureStationIsNotPartOfThis")));
        TripStation arrival = tripStationRepository
                .findStop(tripId, arrivalStationId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.arrivalStationIsNotPartOfThis")));
        if (departure.getStationOrder() == null || arrival.getStationOrder() == null
                || departure.getStationOrder() >= arrival.getStationOrder()) {
            throw new IllegalArgumentException(I18n.t("err.departureStationMustBeBeforeArrivalStation2"));
        }
        TripSegment segment = new TripSegment(departure, arrival);
        segment.setBaseFare(bookingPricingService.segmentBaseFare(departure.getTrainTrip(), segment));
        return segment;
    }

    private void attachTrainAndRoute(TrainTrip trainTrip) {
        if (trainTrip.getTrain() == null || trainTrip.getTrain().getTrainId() == null) {
            throw new IllegalArgumentException(I18n.t("err.trainIsRequired"));
        }
        if (trainTrip.getRoute() == null || trainTrip.getRoute().getRouteId() == null) {
            throw new IllegalArgumentException(I18n.t("err.routeIsRequired"));
        }
        Train train = trainRepository.findById(trainTrip.getTrain().getTrainId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.trainDoesNotExist")));
        Route route = routeRepository.findById(trainTrip.getRoute().getRouteId())
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.routeDoesNotExist")));
        trainTrip.setTrain(train);
        trainTrip.setRoute(route);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerTripView> getCustomerTripsByDate(LocalDate departureDate) {
        LocalDate date = departureDate != null ? departureDate : LocalDate.now();

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        return trainTripRepository
                .findByStatusAndDepartureTimeGreaterThanEqualAndDepartureTimeLessThanOrderByDepartureTimeAsc(
                        TripStatus.SCHEDULED,
                        start,
                        end
                )
                .stream()
                .map(this::toCustomerTripView)
                .toList();
    }
    /** Số ngày tới (tính từ hôm nay) dùng để gợi ý ngày có chuyến. */
    private static final int AVAILABILITY_WINDOW_DAYS = 60;

    @Override
    @Transactional(readOnly = true)
    public StationAvailability getStationAvailability(Long departureStationId,
                                                      Long arrivalStationId,
                                                      LocalDate departureDate) {
        LocalDateTime windowStart = LocalDate.now().atStartOfDay();
        LocalDateTime windowEnd = windowStart.plusDays(AVAILABILITY_WINDOW_DAYS);

        // Đã chọn ngày -> lọc ga theo đúng ngày đó; chưa chọn -> mọi chuyến trong 60 ngày tới.
        LocalDateTime fromTime = departureDate != null ? departureDate.atStartOfDay() : windowStart;
        LocalDateTime toTime = departureDate != null ? fromTime.plusDays(1) : windowEnd;

        List<Long> departureStationIds = tripStationRepository.findDepartureStationIdsWithTrips(
                arrivalStationId, fromTime, toTime, TripStatus.SCHEDULED);

        List<Long> arrivalStationIds = tripStationRepository.findArrivalStationIdsWithTrips(
                departureStationId, fromTime, toTime, TripStatus.SCHEDULED);

        List<String> availableDates = tripStationRepository.findTripDepartureTimes(
                        departureStationId, arrivalStationId, windowStart, windowEnd, TripStatus.SCHEDULED)
                .stream()
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .map(LocalDate::toString)
                .toList();

        return new StationAvailability(departureStationIds, arrivalStationIds, availableDates);
    }

    private CustomerTripView toCustomerTripView(TrainTrip trip) {
        List<TripStation> stations = tripStationRepository
                .findStopsOfTrip(trip.getTripId());

        if (stations.size() < 2) {
            return new CustomerTripView(
                    trip,
                    null,
                    null,
                    null,
                    null,
                    false
            );
        }

        TripStation departureStop = stations.get(0);
        TripStation arrivalStop = stations.get(stations.size() - 1);

        return new CustomerTripView(
                trip,
                departureStop.getStation(),
                arrivalStop.getStation(),
                departureStop.getStation().getStationId(),
                arrivalStop.getStation().getStationId(),
                true
        );
    }
}
