package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.RefundQuote;
import com.hsf302.trainoffice.dto.TripCancellationResult;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.dto.RefundRequestForm;
import com.hsf302.trainoffice.entity.Refund;
import com.hsf302.trainoffice.entity.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Luồng hoàn tiền:
 * Được duyệt thì hoàn 100% số tiền khách đã trả cho vé.
 * PENDING (khách gửi) -> APPROVED (admin duyệt: vé REFUNDED, ghế được nhả) -> COMPLETED (admin đã chuyển tiền: trừ ví)
 * hoặc PENDING -> REJECTED.
 */
public interface RefundService {

    Refund createRefundRequest(Long ticketId, User user, RefundRequestForm form);

    /** Số tiền hoàn dự kiến nếu khách gửi yêu cầu ngay bây giờ (kiểm tra vé thuộc về khách). */
    RefundQuote quoteRefund(Long ticketId, User user);

    List<Refund> getPendingRefunds();

    /** Yêu cầu cần admin xử lý: chờ duyệt + đã duyệt chờ chuyển tiền. */
    List<Refund> getRefundsToProcess();

    void approveRefund(Long refundId, User adminUser);

    void rejectRefund(Long refundId, User adminUser);

    /** Admin xác nhận đã chuyển khoản cho khách: APPROVED -> COMPLETED và trừ ví. */
    void completeRefund(Long refundId, User adminUser);

    Optional<Refund> getRefundById(Long refundId);

    /**
     * Chuyến bị hủy: hoàn 100% mọi vé đã thanh toán và đưa thẳng về COMPLETED (trừ ví, nhả ghế).
     * Yêu cầu đang chờ duyệt / đã duyệt của chuyến cũng được hoàn tất luôn.
     * Trả về số vé và tổng tiền đã hoàn ({@code cancelledHolds} luôn = 0).
     */
    TripCancellationResult refundCancelledTrip(Long tripId);

    /**
     * VNPay báo thành công nhưng đơn đã bị hủy (vd. admin hủy chuyến trong lúc khách thanh toán):
     * tạo yêu cầu hoàn đã duyệt (APPROVED) cho từng vé, tổng đúng bằng {@code paidAmount},
     * để admin chuyển trả khách rồi bấm hoàn tất.
     */
    void createRefundsForLatePayment(Booking booking, BigDecimal paidAmount);
}
