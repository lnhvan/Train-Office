package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.PaymentMethod;
import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.PaymentRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.service.AdminWalletService;
import com.hsf302.trainoffice.service.PaymentService;
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

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final AdminWalletService adminWalletService;

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
                              AdminWalletService adminWalletService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.adminWalletService = adminWalletService;
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
    public Optional<Payment> getPaymentById(Long id) {
        return paymentRepository.findById(id);
    }

    @Override
    @Transactional
    public Payment savePayment(Payment payment) {
        if (payment.getPaymentStatus() == null) {
            payment.setPaymentStatus(PaymentStatus.PENDING);
        }

        if (payment.getTransactionCode() == null || payment.getTransactionCode().isBlank()) {
            payment.setTransactionCode(generateTransactionCode());
        }

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS && payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment markPaymentSuccess(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with ID: " + id));

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return payment;
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());

        Booking booking = payment.getBooking();

        if (booking != null) {
            booking.setBookingStatus(BookingStatus.PAID);
            bookingRepository.save(booking);

            confirmTicketsOfBooking(booking.getBookingId());

            adminWalletService.addToBalance(payment.getAmount());
        }

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment markPaymentFailed(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with ID: " + id));

        payment.setPaymentStatus(PaymentStatus.FAILED);

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public void deletePayment(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new RuntimeException("Payment not found with ID: " + id);
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
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist."));
    }

    @Override
    @Transactional
    public String createVnpayPaymentUrl(Long bookingId, String clientIp) {
        Booking booking = bookingRepository.findBasicDetailsByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking does not exist."));

        if (booking.getBookingStatus() == BookingStatus.PAID) {
            throw new IllegalStateException("Booking has already been paid.");
        }

        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending payment booking can be paid.");
        }

        if (booking.getUser() == null) {
            throw new IllegalStateException("Guest booking payment is disabled. Please log in before booking.");
        }

        if (booking.getTotalAmount() == null
                || booking.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Invalid booking amount.");
        }

        String txnRef = VnpayUtils.generateTxnRef();

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setAmount(booking.getTotalAmount());
        payment.setTransactionCode(txnRef);
        payment.setPaymentStatus(PaymentStatus.PENDING);

        paymentRepository.save(payment);

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
        params.put("vnp_CreateDate", LocalDateTime.now().format(VnpayUtils.VNPAY_DATE_FORMAT));

        String query = VnpayUtils.buildSignedQuery(params, secretKey);

        return payUrl + "?" + query;
    }

    @Override
    @Transactional
    public Payment handleVnpayReturn(Map<String, String> vnpayParams) {
        String txnRef = vnpayParams.get("vnp_TxnRef");

        if (txnRef == null || txnRef.isBlank()) {
            throw new IllegalArgumentException("Missing VNPay transaction reference.");
        }

        Payment payment = paymentRepository.findByTransactionCode(txnRef)
                .orElseThrow(() -> new IllegalArgumentException("Payment transaction does not exist."));

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

            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(VnpayUtils.parsePayDate(vnpayParams.get("vnp_PayDate")));

            Booking booking = payment.getBooking();

            if (booking == null) {
                throw new IllegalStateException("Payment does not have booking.");
            }

            booking.setBookingStatus(BookingStatus.PAID);
            bookingRepository.save(booking);

            confirmTicketsOfBooking(booking.getBookingId());

            adminWalletService.addToBalance(payment.getAmount());
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
        }

        return paymentRepository.save(payment);
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

    private void validatePaidAmount(Payment payment, Map<String, String> vnpayParams) {
        String amountParam = vnpayParams.get("vnp_Amount");

        if (amountParam == null || amountParam.isBlank()) {
            throw new IllegalArgumentException("Missing VNPay amount.");
        }

        BigDecimal paidAmount = new BigDecimal(amountParam)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (paidAmount.compareTo(payment.getAmount()) != 0) {
            throw new IllegalArgumentException("Paid amount does not match booking amount.");
        }
    }

    private String generateTransactionCode() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}