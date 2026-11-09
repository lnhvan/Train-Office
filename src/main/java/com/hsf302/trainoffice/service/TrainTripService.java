package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.TripCancellationResult;
import com.hsf302.trainoffice.dto.TrainTimelineDto;
import com.hsf302.trainoffice.dto.TripTimelineItemDto;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.CustomerTripView;
import com.hsf302.trainoffice.dto.StationAvailability;
import com.hsf302.trainoffice.dto.TripSearchForm;
import com.hsf302.trainoffice.dto.TripSchedulePreview;
import com.hsf302.trainoffice.dto.TripSearchResult;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.TrainTrip;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrainTripService {
    List<TrainTrip> getAllTrips();

    List<TrainTrip> filterTrips(String keyword, Long trainId, Long routeId, TripStatus status, LocalDate departureDate);

    Optional<TrainTrip> getTripById(Long id);

    TrainTrip saveTrip(TrainTrip trainTrip);

    TrainTrip updateTrip(Long id, TrainTrip trainTrip);

    /**
     * Hủy chuyến: vé đã thanh toán được hoàn 100% tự động (COMPLETED, trừ ví admin),
     * vé đang giữ chỗ bị hủy. Ví không đủ tiền thì không hủy gì cả.
     */
    TripCancellationResult cancelTrip(Long id);

    List<TripSearchResult> searchTrips(TripSearchForm form);

    TripSegment getValidSegment(Long tripId, Long departureStationId, Long arrivalStationId);
    List<CustomerTripView> getCustomerTripsByDate(LocalDate departureDate);

    /** Ga đi / ga đến / ngày còn có chuyến, theo những gì người dùng đã chọn ở ô tìm chuyến. */
    StationAvailability getStationAvailability(Long departureStationId, Long arrivalStationId, LocalDate departureDate);

    /** Xem trước giờ đến/đi từng ga (không lưu) khi admin chọn tàu, tuyến và giờ khởi hành. */
    TripSchedulePreview previewSchedule(Long trainId, Long routeId, LocalDateTime departureTime);

    /** Timeline các chuyến của một tàu (SCHEDULED và ONGOING). */
    TrainTimelineDto getTrainTimeline(Long trainId);

    /** Timeline các chuyến tàu trong đội tàu (hoặc theo trainId nếu khác null). */
    List<TripTimelineItemDto> getFleetTimeline(Long trainId);
}
