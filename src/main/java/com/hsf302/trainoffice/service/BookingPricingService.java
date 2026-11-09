package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.BookingPriceSummary;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.TrainTrip;

import java.math.BigDecimal;
import java.util.List;

/**
 * Giá vé = giá cơ bản của đoạn đi + phụ thu ghế/giường, rồi trừ giảm giá đối tượng / nhóm.
 * Giá cơ bản của đoạn = basePrice của chuyến × (km đoạn đi / km cả tuyến),
 * làm tròn tới 1.000đ, tối thiểu 50.000đ (không vượt quá basePrice).
 */
public interface BookingPricingService {

    /** Giá cơ bản cho đoạn ga lên -> ga xuống (chưa gồm phụ thu, chưa giảm giá). */
    BigDecimal segmentBaseFare(TrainTrip trainTrip, TripSegment segment);

    BigDecimal ticketPrice(TrainTrip trainTrip, TripSegment segment, Seat seat, String passengerType);

    BigDecimal calculateTotal(TrainTrip trainTrip,
                              TripSegment segment,
                              List<Seat> seats,
                              List<PassengerBookingRequest> passengers,
                              Long groupDiscountPolicyId);

    BookingPriceSummary buildPriceSummary(TrainTrip trainTrip,
                                          TripSegment segment,
                                          List<Seat> seats,
                                          List<PassengerBookingRequest> passengers,
                                          Long groupDiscountPolicyId);

    /**
     * Số tiền khách thực trả cho từng vé (cùng thứ tự với summary.passengerItems):
     * giá sau giảm đối tượng, trừ phần giảm giá nhóm chia theo tỉ lệ giá vé.
     * Tổng các phần tử luôn bằng summary.totalAmount.
     */
    List<BigDecimal> allocatePaidPrices(BookingPriceSummary summary);
}
