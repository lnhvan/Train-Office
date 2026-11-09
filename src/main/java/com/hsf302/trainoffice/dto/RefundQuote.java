package com.hsf302.trainoffice.dto;

import java.math.BigDecimal;

/**
 * Số tiền hoàn dự kiến cho một vé: nếu được duyệt, khách được hoàn 100% số tiền đã trả cho vé.
 *
 * @param paidAmount   số tiền khách thực trả cho vé (đã trừ giảm giá đối tượng và phần giảm giá nhóm)
 * @param refundAmount số tiền sẽ hoàn (= paidAmount)
 */
public record RefundQuote(
        BigDecimal paidAmount,
        BigDecimal refundAmount
) {
}
