package com.hsf302.trainoffice.dto;

import java.math.BigDecimal;

/**
 * Kết quả hủy chuyến.
 *
 * @param refundedTickets số vé đã thanh toán được hoàn tiền tự động (COMPLETED)
 * @param refundedAmount  tổng tiền đã hoàn (đã trừ ví admin)
 * @param cancelledHolds  số vé đang giữ chỗ/chưa thanh toán bị hủy
 */
public record TripCancellationResult(int refundedTickets, BigDecimal refundedAmount, int cancelledHolds) {
}
