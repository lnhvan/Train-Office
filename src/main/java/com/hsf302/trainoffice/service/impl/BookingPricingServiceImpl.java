package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.BookingPriceSummary;
import com.hsf302.trainoffice.dto.FareBreakdownItem;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.DiscountPolicy;
import com.hsf302.trainoffice.entity.GroupDiscountPolicy;
import com.hsf302.trainoffice.entity.RouteStation;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.repository.RouteStationRepository;
import com.hsf302.trainoffice.service.BookingPricingService;
import com.hsf302.trainoffice.service.DiscountPolicyService;
import com.hsf302.trainoffice.service.GroupDiscountPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingPricingServiceImpl implements BookingPricingService {

    /** Giá cơ bản tối thiểu cho một đoạn đi ngắn. */
    static final BigDecimal MIN_SEGMENT_FARE = new BigDecimal("50000");

    /** Giá cơ bản của đoạn được làm tròn tới bội số này. */
    private static final BigDecimal FARE_ROUNDING_UNIT = new BigDecimal("1000");

    private final DiscountPolicyService discountPolicyService;
    private final GroupDiscountPolicyService groupDiscountPolicyService;
    private final RouteStationRepository routeStationRepository;

    public BookingPricingServiceImpl(DiscountPolicyService discountPolicyService,
                                     GroupDiscountPolicyService groupDiscountPolicyService,
                                     RouteStationRepository routeStationRepository) {
        this.discountPolicyService = discountPolicyService;
        this.groupDiscountPolicyService = groupDiscountPolicyService;
        this.routeStationRepository = routeStationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal segmentBaseFare(TrainTrip trainTrip, TripSegment segment) {
        BigDecimal basePrice = trainTrip == null || trainTrip.getBasePrice() == null
                ? BigDecimal.ZERO
                : trainTrip.getBasePrice();

        if (basePrice.signum() <= 0 || segment == null) {
            return basePrice.setScale(0, RoundingMode.HALF_UP);
        }

        double routeKm = routeLengthKm(trainTrip);
        double segmentKm = segmentLengthKm(segment);

        // Thiếu dữ liệu km (tuyến chưa nhập khoảng cách) hoặc đi hết tuyến -> tính đủ giá chuyến.
        if (routeKm <= 0 || segmentKm <= 0 || segmentKm >= routeKm) {
            return basePrice.setScale(0, RoundingMode.HALF_UP);
        }

        BigDecimal prorated = basePrice
                .multiply(BigDecimal.valueOf(segmentKm))
                .divide(BigDecimal.valueOf(routeKm), 0, RoundingMode.HALF_UP);

        BigDecimal rounded = prorated
                .divide(FARE_ROUNDING_UNIT, 0, RoundingMode.HALF_UP)
                .multiply(FARE_ROUNDING_UNIT);

        BigDecimal minimum = MIN_SEGMENT_FARE.min(basePrice);

        return rounded.max(minimum).min(basePrice).setScale(0, RoundingMode.HALF_UP);
    }

    private double segmentLengthKm(TripSegment segment) {
        Double from = distanceOf(segment.getDepartureStop());
        Double to = distanceOf(segment.getArrivalStop());

        if (from == null || to == null) {
            return 0;
        }

        return to - from;
    }

    private Double distanceOf(TripStation stop) {
        if (stop == null || stop.getRouteStation() == null) {
            return null;
        }

        return stop.getRouteStation().getDistanceFromStartKm();
    }

    private double routeLengthKm(TrainTrip trainTrip) {
        if (trainTrip.getRoute() == null || trainTrip.getRoute().getRouteId() == null) {
            return 0;
        }

        return routeStationRepository
                .findByRoute_RouteIdOrderByStationOrderAsc(trainTrip.getRoute().getRouteId())
                .stream()
                .map(RouteStation::getDistanceFromStartKm)
                .filter(km -> km != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);
    }

    @Override
    public BigDecimal ticketPrice(TrainTrip trainTrip, TripSegment segment, Seat seat, String passengerType) {
        return priceForPassenger(originalTicketPrice(segmentBaseFare(trainTrip, segment), seat), passengerType);
    }

    private BigDecimal priceForPassenger(BigDecimal originalPrice, String passengerType) {
        DiscountPolicy policy = discountPolicyService
                .getActivePolicyByCode(passengerType)
                .orElse(null);

        if (policy == null || policy.getDiscountPercent() == null) {
            return originalPrice.setScale(0, RoundingMode.HALF_UP);
        }

        BigDecimal discountAmount = calculatePercentAmount(originalPrice, policy.getDiscountPercent());
        BigDecimal finalPrice = originalPrice.subtract(discountAmount);

        if (finalPrice.compareTo(BigDecimal.ZERO) < 0) {
            finalPrice = BigDecimal.ZERO;
        }

        return finalPrice.setScale(0, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateTotal(TrainTrip trainTrip,
                                     TripSegment segment,
                                     List<Seat> seats,
                                     List<PassengerBookingRequest> passengers,
                                     Long groupDiscountPolicyId) {
        return buildPriceSummary(trainTrip, segment, seats, passengers, groupDiscountPolicyId).getTotalAmount();
    }

    @Override
    public BookingPriceSummary buildPriceSummary(TrainTrip trainTrip,
                                                 TripSegment segment,
                                                 List<Seat> seats,
                                                 List<PassengerBookingRequest> passengers,
                                                 Long groupDiscountPolicyId) {
        BigDecimal baseFare = segmentBaseFare(trainTrip, segment);
        List<FareBreakdownItem> passengerItems = new ArrayList<>();
        BigDecimal passengerSubtotal = BigDecimal.ZERO;

        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);

            PassengerBookingRequest passenger = passengers != null && i < passengers.size()
                    ? passengers.get(i)
                    : null;

            String passengerName = passenger != null && passenger.getFullName() != null
                    ? passenger.getFullName()
                    : I18n.t("booking.passengerN", i + 1);

            String passengerType = passenger != null
                    ? passenger.getPassengerType()
                    : null;

            BigDecimal originalPrice = originalTicketPrice(baseFare, seat);
            BigDecimal finalPrice = priceForPassenger(originalPrice, passengerType);
            BigDecimal passengerDiscountAmount = originalPrice.subtract(finalPrice);

            if (passengerDiscountAmount.compareTo(BigDecimal.ZERO) < 0) {
                passengerDiscountAmount = BigDecimal.ZERO;
            }

            DiscountPolicy passengerPolicy = discountPolicyService
                    .getActivePolicyByCode(passengerType)
                    .orElse(null);

            BigDecimal passengerDiscountPercent = passengerPolicy != null && passengerPolicy.getDiscountPercent() != null
                    ? passengerPolicy.getDiscountPercent()
                    : BigDecimal.ZERO;

            String passengerPolicyName = passengerPolicy != null
                    ? passengerPolicy.getPolicyName()
                    : I18n.t("booking.standardFare");

            String coachNumber = seat.getCoach() != null
                    ? seat.getCoach().getCoachNumber()
                    : "-";

            passengerItems.add(new FareBreakdownItem(
                    i + 1,
                    passengerName,
                    passengerType,
                    passengerPolicyName,
                    coachNumber,
                    compartmentNumber(seat),
                    seat.getSeatNumber(),
                    seat.getSeatType(),
                    seat.getBerthLevel(),
                    formatBerthLevel(seat.getBerthLevel()),
                    berthLevelCssClass(seat.getBerthLevel()),
                    originalPrice.setScale(0, RoundingMode.HALF_UP),
                    passengerDiscountPercent,
                    passengerDiscountAmount.setScale(0, RoundingMode.HALF_UP),
                    finalPrice.setScale(0, RoundingMode.HALF_UP)
            ));

            passengerSubtotal = passengerSubtotal.add(finalPrice);
        }

        GroupDiscountPolicy groupPolicy = resolveSelectedGroupPolicy(groupDiscountPolicyId, seats.size());

        BigDecimal groupDiscountAmount = BigDecimal.ZERO;

        if (groupPolicy != null && groupPolicy.getDiscountPercent() != null) {
            groupDiscountAmount = calculatePercentAmount(passengerSubtotal, groupPolicy.getDiscountPercent());
        }

        BigDecimal totalAmount = passengerSubtotal.subtract(groupDiscountAmount);

        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        return new BookingPriceSummary(
                passengerItems,
                passengerSubtotal.setScale(0, RoundingMode.HALF_UP),
                groupPolicy != null ? groupPolicy.getPolicyId() : null,
                groupPolicy != null ? groupPolicy.getPolicyName() : null,
                groupPolicy != null ? groupPolicy.getDiscountPercent() : BigDecimal.ZERO,
                groupDiscountAmount.setScale(0, RoundingMode.HALF_UP),
                totalAmount.setScale(0, RoundingMode.HALF_UP)
        );
    }

    @Override
    public List<BigDecimal> allocatePaidPrices(BookingPriceSummary summary) {
        List<BigDecimal> finals = summary.getPassengerItems()
                .stream()
                .map(item -> item.getFinalPrice() == null ? BigDecimal.ZERO : item.getFinalPrice())
                .toList();

        BigDecimal subtotal = finals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = summary.getTotalAmount() == null ? subtotal : summary.getTotalAmount();

        // Phần giảm cần chia = chênh lệch thực tế giữa tổng giá vé và tổng tiền đơn (tránh lệch do làm tròn).
        BigDecimal discountToShare = subtotal.subtract(total);

        List<BigDecimal> result = new ArrayList<>();
        if (discountToShare.signum() <= 0 || subtotal.signum() <= 0) {
            result.addAll(finals);
            return result;
        }

        BigDecimal shared = BigDecimal.ZERO;
        int last = finals.size() - 1;

        for (int i = 0; i < finals.size(); i++) {
            BigDecimal share = i == last
                    ? discountToShare.subtract(shared)
                    : discountToShare.multiply(finals.get(i)).divide(subtotal, 0, RoundingMode.DOWN);

            shared = shared.add(share);
            result.add(finals.get(i).subtract(share).max(BigDecimal.ZERO));
        }

        return result;
    }

    private GroupDiscountPolicy resolveSelectedGroupPolicy(Long groupDiscountPolicyId, int passengerCount) {
        if (groupDiscountPolicyId == null) {
            return null;
        }

        GroupDiscountPolicy policy = groupDiscountPolicyService.getPolicyById(groupDiscountPolicyId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.selectedGroupDiscountPolicyDoesNotExist")));

        if (!Boolean.TRUE.equals(policy.getActive())) {
            throw new IllegalArgumentException(I18n.t("err.selectedGroupDiscountPolicyIsInactive"));
        }

        if (!groupDiscountPolicyService.matchesPassengerCount(policy, passengerCount)) {
            throw new IllegalArgumentException(I18n.t("err.selectedGroupDiscountPolicyDoesNotMatch"));
        }

        return policy;
    }

    /** Giá gốc 1 vé = giá cơ bản của đoạn đi + phụ thu ghế/giường. */
    private BigDecimal originalTicketPrice(BigDecimal segmentBaseFare, Seat seat) {
        BigDecimal basePrice = segmentBaseFare == null
                ? BigDecimal.ZERO
                : segmentBaseFare;

        BigDecimal extraPrice = seat.getExtraPrice() == null
                ? BigDecimal.ZERO
                : seat.getExtraPrice();

        return basePrice.add(extraPrice);
    }

    private String compartmentNumber(Seat seat) {
        if (seat == null || seat.getCompartment() == null) {
            return null;
        }

        return seat.getCompartment().getCompartmentNumber();
    }

    private String formatBerthLevel(String berthLevel) {
        if (berthLevel == null || berthLevel.isBlank()) {
            return null;
        }

        String normalized = berthLevel.trim().toUpperCase();

        return switch (normalized) {
            case "LOWER" -> I18n.t("js.berthLower");
            case "MIDDLE" -> I18n.t("js.berthMiddle");
            case "UPPER" -> I18n.t("js.berthUpper");
            default -> normalized.replace("_", " ");
        };
    }

    private String berthLevelCssClass(String berthLevel) {
        if (berthLevel == null || berthLevel.isBlank()) {
            return "";
        }

        String normalized = berthLevel.trim().toLowerCase();

        return switch (normalized) {
            case "lower" -> "lower";
            case "middle" -> "middle";
            case "upper" -> "upper";
            default -> "";
        };
    }

    private BigDecimal calculatePercentAmount(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null) {
            return BigDecimal.ZERO;
        }

        if (percent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (percent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return amount;
        }

        return amount
                .multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
