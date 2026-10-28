package com.hsf302.trainoffice.dto;

import java.util.List;

/**
 * Kết quả cho ô tìm chuyến: ga nào còn chọn được và ngày nào có chuyến,
 * dựa trên những gì người dùng đã nhập (ga đi / ga đến / ngày).
 *
 * @param departureStationIds ga đi có chuyến (tới ga đến đã chọn, trong ngày đã chọn)
 * @param arrivalStationIds   ga đến có chuyến (từ ga đi đã chọn, trong ngày đã chọn)
 * @param availableDates      các ngày (yyyy-MM-dd) trong 60 ngày tới có chuyến cho cặp ga đã chọn
 */
public record StationAvailability(List<Long> departureStationIds,
                                  List<Long> arrivalStationIds,
                                  List<String> availableDates) {
}
