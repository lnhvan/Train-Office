package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.dto.BookingPriceSummary;
import com.hsf302.trainoffice.dto.FareBreakdownItem;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.entity.DiscountPolicy;
import com.hsf302.trainoffice.entity.GroupDiscountPolicy;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.service.BookingPricingService;
import com.hsf302.trainoffice.service.DiscountPolicyService;
import com.hsf302.trainoffice.service.GroupDiscountPolicyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingPricingServiceImpl implements BookingPricingService {

    private final DiscountPolicyService discountPolicyService;
    private final GroupDiscountPolicyService groupDiscountPolicyService;

    public BookingPricingServiceImpl(DiscountPolicyService discountPolicyService,
                                     GroupDiscountPolicyService groupDiscountPolicyService) {
        this.discountPolicyService = discountPolicyService;
        this.groupDiscountPolicyService = groupDiscountPolicyService;
    }

    @Override
    public BigDecimal ticketPrice(TrainTrip trainTrip, Seat seat) {
        return originalTicketPrice(trainTrip, seat)
                .setScale(0, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal ticketPrice(TrainTrip trainTrip, Seat seat, String passengerType) {
        BigDecimal originalPrice = originalTicketPrice(trainTrip, seat);

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
    public BigDecimal calculateTotal(TrainTrip trainTrip, List<Seat> seats) {
        BigDecimal total = BigDecimal.ZERO;

        for (Seat seat : seats) {
            total = total.add(ticketPrice(trainTrip, seat));
        }

        return total;
    }

    @Override
    public BigDecimal calculateTotal(TrainTrip trainTrip,
                                     List<Seat> seats,
                                     List<PassengerBookingRequest> passengers) {
        return calculateTotal(trainTrip, seats, passengers, null);
    }

    @Override
    public BigDecimal calculateTotal(TrainTrip trainTrip,
                                     List<Seat> seats,
                                     List<PassengerBookingRequest> passengers,
                                     Long groupDiscountPolicyId) {
        return buildPriceSummary(trainTrip, seats, passengers, groupDiscountPolicyId).getTotalAmount();
    }

    @Override
    public BookingPriceSummary buildPriceSummary(TrainTrip trainTrip,
                                                 List<Seat> seats,
                                                 List<PassengerBookingRequest> passengers,
                                                 Long groupDiscountPolicyId) {
        List<FareBreakdownItem> passengerItems = new ArrayList<>();
        BigDecimal passengerSubtotal = BigDecimal.ZERO;

        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);

            PassengerBookingRequest passenger = passengers != null && i < passengers.size()
                    ? passengers.get(i)
                    : null;

            String passengerName = passenger != null && passenger.getFullName() != null
                    ? passenger.getFullName()
                    : "Passenger " + (i + 1);

            String passengerType = passenger != null
                    ? passenger.getPassengerType()
                    : null;

            BigDecimal originalPrice = originalTicketPrice(trainTrip, seat);
            BigDecimal finalPrice = ticketPrice(trainTrip, seat, passengerType);
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
                    : "Default";

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

    private GroupDiscountPolicy resolveSelectedGroupPolicy(Long groupDiscountPolicyId, int passengerCount) {
        if (groupDiscountPolicyId == null) {
            return null;
        }

        GroupDiscountPolicy policy = groupDiscountPolicyService.getPolicyById(groupDiscountPolicyId)
                .orElseThrow(() -> new IllegalArgumentException("Selected group discount policy does not exist."));

        if (!Boolean.TRUE.equals(policy.getActive())) {
            throw new IllegalArgumentException("Selected group discount policy is inactive.");
        }

        if (!groupDiscountPolicyService.matchesPassengerCount(policy, passengerCount)) {
            throw new IllegalArgumentException("Selected group discount policy does not match passenger count.");
        }

        return policy;
    }

    private BigDecimal originalTicketPrice(TrainTrip trainTrip, Seat seat) {
        BigDecimal basePrice = trainTrip.getBasePrice() == null
                ? BigDecimal.ZERO
                : trainTrip.getBasePrice();

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
            case "LOWER" -> "Level 1 / Lower berth";
            case "MIDDLE" -> "Level 2 / Middle berth";
            case "UPPER" -> "Level 3 / Upper berth";
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
