package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.SeatHold;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.exception.SeatHoldException;
import com.hsf302.trainoffice.security.CurrentUser;
import com.hsf302.trainoffice.service.BookingFlowService;
import com.hsf302.trainoffice.service.BookingService;
import com.hsf302.trainoffice.service.SeatHoldService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * API giữ ghế (gọi bằng fetch từ static/js/rail-booking-flow.js và rail-hold-timer.js).
 *
 * <pre>
 * POST   /api/booking/holds/seats          {tripId, departureStationId, arrivalStationId, passengerCount, seatId}
 * DELETE /api/booking/holds/seats/{seatId}
 * GET    /api/booking/holds/current        (?bookingId= cho trang thanh toán)
 * </pre>
 * Trả về {@code {active, remainingMs, seatIds, tripId, departureStationId, arrivalStationId}}.
 * Lỗi: 401 chưa đăng nhập, 409 ghế đã có người giữ / đủ số ghế, 410 hết hạn, 503 Redis lỗi.
 */
@RestController
@RequestMapping("/api/booking/holds")
public class SeatHoldApiController {

    private final BookingFlowService bookingFlowService;
    private final BookingService bookingService;
    private final SeatHoldService seatHoldService;

    public SeatHoldApiController(BookingFlowService bookingFlowService,
                                 BookingService bookingService,
                                 SeatHoldService seatHoldService) {
        this.bookingFlowService = bookingFlowService;
        this.bookingService = bookingService;
        this.seatHoldService = seatHoldService;
    }

    public record HoldSeatRequest(Long tripId,
                                  Long departureStationId,
                                  Long arrivalStationId,
                                  Integer passengerCount,
                                  Long seatId) {
    }

    @PostMapping("/seats")
    public ResponseEntity<Map<String, Object>> holdSeat(@RequestBody HoldSeatRequest request) {
        User user = CurrentUser.get();
        if (user == null) {
            return unauthorized();
        }

        if (request == null || request.tripId() == null || request.departureStationId() == null
                || request.arrivalStationId() == null || request.seatId() == null) {
            return error(HttpStatus.BAD_REQUEST, "INVALID", I18n.t("err.invalidSeatHoldRequest"));
        }

        try {
            SeatHold hold = bookingFlowService.holdSeat(
                    user,
                    request.tripId(),
                    request.departureStationId(),
                    request.arrivalStationId(),
                    request.seatId(),
                    request.passengerCount() == null ? 1 : request.passengerCount());
            return ResponseEntity.ok(body(hold));
        } catch (SeatHoldException ex) {
            return holdError(ex);
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.CONFLICT, "INVALID", ex.getMessage());
        }
    }

    @DeleteMapping("/seats/{seatId}")
    public ResponseEntity<Map<String, Object>> releaseSeat(@PathVariable Long seatId) {
        User user = CurrentUser.get();
        if (user == null) {
            return unauthorized();
        }

        try {
            return ResponseEntity.ok(body(bookingFlowService.releaseSeat(user, seatId).orElse(null)));
        } catch (SeatHoldException ex) {
            return holdError(ex);
        }
    }

    /** Thời gian giữ chỗ còn lại: của khách đang chọn ghế, hoặc của một đơn chờ thanh toán. */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> current(@RequestParam(required = false) Long bookingId) {
        User user = CurrentUser.get();
        if (user == null) {
            return unauthorized();
        }

        try {
            if (bookingId != null) {
                Booking booking = bookingService.getBookingById(bookingId);
                if (booking.getUser() == null || !Objects.equals(booking.getUser().getUserId(), user.getUserId())) {
                    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", I18n.t("err.bookingDoesNotBelongToCurrentUser"));
                }
                return ResponseEntity.ok(body(seatHoldService.findBookingHold(bookingId).orElse(null)));
            }

            Optional<SeatHold> hold = bookingFlowService.findUserHold(user);
            return ResponseEntity.ok(body(hold.orElse(null)));
        } catch (SeatHoldException ex) {
            return holdError(ex);
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> body(SeatHold hold) {
        if (hold == null) {
            return Map.of("active", false, "remainingMs", 0L, "seatIds", List.of());
        }
        return Map.of(
                // Hạn giữ chỗ vẫn chạy kể cả khi khách đã bỏ hết ghế (chọn lại không được thêm giờ).
                "active", hold.remainingMillis() > 0,
                "remainingMs", hold.remainingMillis(),
                "seatIds", hold.seatIds(),
                "tripId", hold.tripId(),
                "departureStationId", hold.departureStationId(),
                "arrivalStationId", hold.arrivalStationId());
    }

    private static ResponseEntity<Map<String, Object>> holdError(SeatHoldException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case SEAT_TAKEN, LIMIT_REACHED -> HttpStatus.CONFLICT;
            case EXPIRED -> HttpStatus.GONE;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return error(status, ex.getReason().name(), ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> unauthorized() {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", I18n.t("err.pleaseLogInToBuyTickets"));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "error", message == null ? "" : message));
    }
}
