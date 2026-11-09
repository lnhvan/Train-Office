package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.common.BookingHoldPolicy;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.PaymentMethod;
import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.PaymentRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.service.AdminWalletService;
import com.hsf302.trainoffice.service.MailService;
import com.hsf302.trainoffice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.hsf302.trainoffice.service.RefundService;
import com.hsf302.trainoffice.service.SeatHoldService;
import com.hsf302.trainoffice.event.SeatHoldReleaseEvent;
import com.hsf302.trainoffice.exception.SeatHoldException;
import org.springframework.context.ApplicationEventPublisher;
import com.hsf302.trainoffice.util.VnpayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final MailService mailService;

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final AdminWalletService adminWalletService;
    private final RefundService refundService;
    private final SeatHoldService seatHoldService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.secret-key}")
    private String secretKey;

    @Value("${vnpay.pay-url}")
    private String payUrl;

    @Value("${vnpay.return-url}")
    private String returnUrl;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              BookingRepository bookingRepository,
                              TicketRepository ticketRepository,
                              AdminWalletService adminWalletService,
                              RefundService refundService,
                              SeatHoldService seatHoldService,
                              ApplicationEventPublisher eventPublisher,
                              MailService mailService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.adminWalletService = adminWalletService;
        this.refundService = refundService;
        this.seatHoldService = seatHoldService;
        this.eventPublisher = eventPublisher;
        this.mailService = mailService;
    }

    // =========================================================
    // ADMIN PAYMENT MANAGEMENT
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        if (status == null) {
            return paymentRepository.findAll();
        }

        return paymentRepository.findByPaymentStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByBookingId(Long bookingId) {
        if (bookingId == null) {
            return paymentRepository.findAll();
        }

        return paymentRepository.findByBooking_BookingId(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentById(UUID id) {
        return paymentRepository.findById(id);
    }

    @Override
    @Transactional
    public Payment savePayment(Payment payment) {
        Payment existing = payment.getPaymentId() == null
                ? null
                : paymentRepository.findById(payment.getPaymentId()).orElse(null);

        // Thanh toán đã thành công đã được cộng vào ví: không cho sửa trạng thái / số tiền (dùng hoàn tiền nếu cần).
        if (existing != null && existing.getPaymentStatus() == PaymentStatus.SUCCESS) {
            boolean statusChanged = payment.getPaymentStatus() != PaymentStatus.SUCCESS;
            boolean amountChanged = payment.getAmount() == null
                    || existing.getAmount() == null
                    || payment.getAmount().compareTo(existing.getAmount()) != 0;

            if (statusChanged || amountChanged) {
                throw new IllegalStateException(I18n.t("err.successfulPaymentIsLocked"));
            }
        }

        // Chỉ VNPay mới chuyển thanh toán sang thành công; admin không tự đánh dấu được.
        boolean becomesSuccess = payment.getPaymentStatus() == PaymentStatus.SUCCESS
                && (existing == null || existing.getPaymentStatus() != PaymentStatus.SUCCESS);

        if (becomesSuccess) {
            throw new IllegalStateException(I18n.t("err.adminCannotMarkPaymentSuccess"));
        }

        if (payment.getPaymentStatus() == null) {
            payment.setPaymentStatus(PaymentStatus.PENDING);
        }

        if (payment.getTransactionCode() == null || payment.getTransactionCode().isBlank()) {
            payment.setTransactionCode(generateTransactionCode());
        }

        // Form sửa không gửi thời điểm thanh toán: giữ nguyên giá trị cũ.
        if (existing != null && payment.getPaidAt() == null) {
            payment.setPaidAt(existing.getPaidAt());
        }

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment markPaymentFailed(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(I18n.t("err.paymentNotFoundWithId", id)));

        // Tiền đã vào ví, đơn đã thanh toán: không đảo ngược ở đây, phải đi qua hoàn tiền.
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException(I18n.t("err.successfulPaymentIsLocked"));
        }

        payment.setPaymentStatus(PaymentStatus.FAILED);

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public void deletePayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(I18n.t("err.paymentNotFoundWithId", id)));

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException(I18n.t("err.successfulPaymentIsLocked"));
        }

        paymentRepository.deleteById(id);
    }

    // =========================================================
    // CUSTOMER VNPAY CHECKOUT
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Booking getBookingForPayment(Long bookingId) {
        return bookingRepository.findBasicDetailsByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.bookingDoesNotExist2")));
    }

    @Override
    @Transactional
    public String createVnpayPaymentUrl(Long bookingId, String clientIp) {
        Booking booking = bookingRepository.findBasicDetailsByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.bookingDoesNotExist2")));

        if (booking.getBookingStatus() == BookingStatus.PAID) {
            throw new IllegalStateException(I18n.t("err.bookingHasAlreadyBeenPaid"));
        }

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(I18n.t("err.onlyPendingPaymentBookingCanBePaid"));
        }

        if (booking.getUser() == null) {
            throw new IllegalStateException(I18n.t("err.guestBookingPaymentIsDisabledPleaseLog"));
        }

        if (booking.getTotalAmount() == null
                || booking.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(I18n.t("err.invalidBookingAmount"));
        }

        if (booking.getTrainTrip() == null || booking.getTrainTrip().getStatus() != TripStatus.SCHEDULED) {
            throw new IllegalStateException(I18n.t("err.tripNoLongerAvailableForPayment"));
        }

        LocalDateTime now = LocalDateTime.now();

        // Khóa ghế trong Redis đã hết hạn: ghế đã nhả, không cho thanh toán (job sẽ hủy đơn).
        // Còn hạn thì gia hạn tới hết hạn link VNPay + dự phòng: người khác không đặt được,
        // job nhả chỗ không hủy đơn trong lúc khách đang thanh toán.
        LocalDateTime vnpayExpireAt = now.plusMinutes(BookingHoldPolicy.VNPAY_PAYMENT_MINUTES);
        LocalDateTime lockUntil = vnpayExpireAt.plusMinutes(BookingHoldPolicy.ORDER_LOCK_GRACE_MINUTES);

        if (!seatHoldService.extendBookingHold(booking.getBookingId(), lockUntil)) {
            throw new IllegalStateException(I18n.t("err.bookingHoldExpired"));
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setAmount(booking.getTotalAmount());
        payment.setPaymentStatus(PaymentStatus.PENDING);

        payment = paymentRepository.save(payment);

        String txnRef = payment.getPaymentId().toString();

        long vnpayAmount = booking.getTotalAmount()
                .setScale(0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        String resolvedIp = clientIp == null || clientIp.isBlank()
                ? "127.0.0.1"
                : clientIp;

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(vnpayAmount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan booking " + booking.getBookingNumber());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", resolvedIp);
        params.put("vnp_CreateDate", now.format(VnpayUtils.VNPAY_DATE_FORMAT));
        // VNPay từ chối thanh toán sau thời điểm này, nên không có tiền về sau khi ghế đã nhả.
        params.put("vnp_ExpireDate", vnpayExpireAt.format(VnpayUtils.VNPAY_DATE_FORMAT));

        String query = VnpayUtils.buildSignedQuery(params, secretKey);

        return payUrl + "?" + query;
    }

    @Override
    @Transactional
    public Payment handleVnpayReturn(Map<String, String> vnpayParams) {
        String txnRef = vnpayParams.get("vnp_TxnRef");

        if (txnRef == null || txnRef.isBlank()) {
            throw new IllegalArgumentException(I18n.t("err.missingVnpayTransactionReference"));
        }

        Payment payment = null;
        try {
            payment = paymentRepository.findById(UUID.fromString(txnRef)).orElse(null);
        } catch (IllegalArgumentException ignored) {
        }

        if (payment == null) {
            payment = paymentRepository.findByTransactionCode(txnRef)
                    .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.paymentTransactionDoesNotExist")));
        }

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return payment;
        }

        String secureHash = vnpayParams.get("vnp_SecureHash");

        if (secureHash == null || secureHash.isBlank()) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            return paymentRepository.save(payment);
        }

        boolean validSignature = VnpayUtils.validateSignature(vnpayParams, secureHash, secretKey);

        if (!validSignature) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            return paymentRepository.save(payment);
        }

        String responseCode = vnpayParams.get("vnp_ResponseCode");

        if ("00".equals(responseCode)) {
            validatePaidAmount(payment, vnpayParams);

            String vnpayTransactionNo = vnpayParams.get("vnp_TransactionNo");
            if (vnpayTransactionNo != null && !vnpayTransactionNo.isBlank()) {
                payment.setTransactionCode(vnpayTransactionNo);
            }

            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(VnpayUtils.parsePayDate(vnpayParams.get("vnp_PayDate")));

            Booking booking = payment.getBooking();

            if (booking == null) {
                throw new IllegalStateException(I18n.t("err.paymentDoesNotHaveBooking"));
            }

            // Tiền đã thực sự bị trừ của khách: luôn ghi nhận vào ví.
            adminWalletService.credit(
                    payment.getAmount(),
                    payment.getTransactionCode(),
                    I18n.t("wallet.tx.paymentVnpay", booking.getBookingNumber()));

            if (booking.getBookingStatus() == BookingStatus.PENDING_PAYMENT) {
                booking.setBookingStatus(BookingStatus.PAID);
                bookingRepository.save(booking);

                confirmTicketsOfBooking(booking.getBookingId());

                // Vé đã CONFIRMED trong DB: bỏ khóa tạm trong Redis sau khi commit.
                eventPublisher.publishEvent(SeatHoldReleaseEvent.booking(booking.getBookingId()));

                // Gửi email vé điện tử & xác nhận thanh toán thành công tới người đặt vé
                sendPaymentSuccessEmailSafely(booking, payment);
            } else {
                // Đơn đã bị hủy trong lúc khách thanh toán (vd. admin hủy chuyến): không xuất vé,
                // tạo sẵn yêu cầu hoàn tiền đã duyệt để admin chuyển trả khách.
                paymentRepository.save(payment);
                refundService.createRefundsForLatePayment(booking, payment.getAmount());
            }
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            restoreHoldAfterFailedPayment(payment.getBooking());
        }

        return paymentRepository.save(payment);
    }

    private void sendPaymentSuccessEmailSafely(Booking booking, Payment payment) {
        try {
            List<Ticket> tickets = ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(booking.getBookingId());
            mailService.sendPaymentSuccessEmail(booking, payment, tickets);
        } catch (Exception ex) {
            log.error("Lỗi khi gửi email xác nhận thanh toán cho booking #{}: {}",
                    booking.getBookingNumber(), ex.getMessage(), ex);
        }
    }

    private void confirmTicketsOfBooking(Long bookingId) {
        List<Ticket> tickets = ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(bookingId);

        for (Ticket ticket : tickets) {
            if (ticket.getTicketStatus() == TicketStatus.BOOKED) {
                ticket.setTicketStatus(TicketStatus.CONFIRMED);
            }
        }

        ticketRepository.saveAll(tickets);
    }

    /** Thanh toán thất bại / khách hủy trên VNPay: khóa ghế quay về hạn giữ chỗ ban đầu. */
    private void restoreHoldAfterFailedPayment(Booking booking) {
        if (booking == null || booking.getBookingId() == null) {
            return;
        }

        try {
            seatHoldService.restoreBookingHoldAfterFailedPayment(booking.getBookingId());
        } catch (SeatHoldException ignored) {
            // Redis lỗi: khóa vẫn tự hết hạn theo TTL đã gia hạn.
        }
    }

    private void validatePaidAmount(Payment payment, Map<String, String> vnpayParams) {
        String amountParam = vnpayParams.get("vnp_Amount");

        if (amountParam == null || amountParam.isBlank()) {
            throw new IllegalArgumentException(I18n.t("err.missingVnpayAmount"));
        }

        BigDecimal paidAmount = new BigDecimal(amountParam)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (paidAmount.compareTo(payment.getAmount()) != 0) {
            throw new IllegalArgumentException(I18n.t("err.paidAmountDoesNotMatchBookingAmount"));
        }
    }

    private String generateTransactionCode() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}