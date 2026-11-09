package com.hsf302.trainoffice.service.impl;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.RefundStatus;
import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.RefundQuote;
import com.hsf302.trainoffice.dto.RefundRequestForm;
import com.hsf302.trainoffice.dto.TripCancellationResult;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Refund;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.RefundRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.repository.TripStationRepository;
import com.hsf302.trainoffice.service.AdminWalletService;
import com.hsf302.trainoffice.service.RefundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final TicketRepository ticketRepository;
    private final BookingRepository bookingRepository;
    private final TripStationRepository tripStationRepository;
    private final AdminWalletService adminWalletService;

    public RefundServiceImpl(RefundRepository refundRepository,
                             TicketRepository ticketRepository,
                             BookingRepository bookingRepository,
                             TripStationRepository tripStationRepository,
                             AdminWalletService adminWalletService) {
        this.refundRepository = refundRepository;
        this.ticketRepository = ticketRepository;
        this.bookingRepository = bookingRepository;
        this.tripStationRepository = tripStationRepository;
        this.adminWalletService = adminWalletService;
    }

    @Override
    @Transactional
    public Refund createRefundRequest(Long ticketId, User user, RefundRequestForm form) {
        if (form == null) {
            throw new IllegalArgumentException(I18n.t("err.refundInformationIsRequired"));
        }

        Ticket ticket = loadRefundableTicket(ticketId, user);
        RefundQuote quote = buildQuote(ticket);

        boolean duplicated = refundRepository.existsByTicket_TicketIdAndRefundStatusIn(
                ticketId,
                List.of(RefundStatus.PENDING, RefundStatus.APPROVED, RefundStatus.COMPLETED)
        );

        if (duplicated) {
            throw new IllegalStateException(I18n.t("err.refundRequestAlreadyExistsForThisTicket"));
        }

        Refund refund = new Refund();
        refund.setRefundCode(generateRefundCode(0));
        refund.setTicket(ticket);
        refund.setRefundAmount(quote.refundAmount());
        refund.setRefundReason(form.getReason().trim());
        refund.setCustomerName(form.getCustomerName().trim());
        refund.setCustomerEmail(form.getCustomerEmail());
        refund.setCustomerPhone(form.getCustomerPhone().trim());
        refund.setBankName(form.getBankName().trim());
        refund.setBankAccountNumber(form.getBankAccountNumber().trim());
        refund.setBankAccountHolder(form.getBankAccountHolder().trim());
        refund.setRefundStatus(RefundStatus.PENDING);

        return refundRepository.save(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundQuote quoteRefund(Long ticketId, User user) {
        return buildQuote(loadRefundableTicket(ticketId, user));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Refund> getRefundById(Long refundId) {
        if (refundId == null) {
            return Optional.empty();
        }

        return refundRepository.findById(refundId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> getPendingRefunds() {
        return refundRepository.findByRefundStatusOrderByRefundIdDesc(RefundStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> getRefundsToProcess() {
        return refundRepository.findByRefundStatusInOrderByRefundIdDesc(
                List.of(RefundStatus.PENDING, RefundStatus.APPROVED));
    }

    @Override
    @Transactional
    public void approveRefund(Long refundId, User adminUser) {
        requireAdmin(adminUser);
        Refund refund = findRefund(refundId);

        if (refund.getRefundStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException(I18n.t("err.refundRequestHasAlreadyBeenProcessed"));
        }

        Ticket ticket = refund.getTicket();

        if (ticket.getTicketStatus() != TicketStatus.CONFIRMED) {
            throw new IllegalStateException(I18n.t("err.onlyConfirmedTicketsCanBeRefunded"));
        }

        refund.setRefundStatus(RefundStatus.APPROVED);
        refund.setProcessedAt(LocalDateTime.now());
        refundRepository.save(refund);

        // Vé REFUNDED không còn được tính là ghế đã bán -> ghế tự nhả cho khách khác đặt.
        ticket.setTicketStatus(TicketStatus.REFUNDED);
        ticketRepository.save(ticket);

        updateBookingStatusIfAllTicketsRefunded(ticket.getBooking());
    }

    @Override
    @Transactional
    public void rejectRefund(Long refundId, User adminUser) {
        requireAdmin(adminUser);
        Refund refund = findRefund(refundId);

        if (refund.getRefundStatus() != RefundStatus.PENDING) {
            throw new IllegalStateException(I18n.t("err.refundRequestHasAlreadyBeenProcessed"));
        }

        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setProcessedAt(LocalDateTime.now());

        refundRepository.save(refund);
    }

    @Override
    @Transactional
    public void completeRefund(Long refundId, User adminUser) {
        requireAdmin(adminUser);
        Refund refund = findRefund(refundId);

        if (refund.getRefundStatus() != RefundStatus.APPROVED) {
            throw new IllegalStateException(I18n.t("err.refundMustBeApprovedBeforeComplete"));
        }

        // Trừ ví trước: số dư không đủ thì ném lỗi, cả transaction không đổi gì.
        adminWalletService.debit(
                refund.getRefundAmount(),
                refund.getRefundCode(),
                I18n.t("wallet.tx.refundTransferred", refund.getRefundCode()));

        refund.setRefundStatus(RefundStatus.COMPLETED);
        refund.setRefundedAt(LocalDateTime.now());
        refundRepository.save(refund);
    }

    @Override
    @Transactional
    public TripCancellationResult refundCancelledTrip(Long tripId) {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> handledTicketIds = new HashSet<>();
        Map<Long, Booking> touchedBookings = new LinkedHashMap<>();
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;

        // 1. Yêu cầu khách đã gửi (chờ duyệt / đã duyệt chờ chuyển tiền): hoàn tất luôn.
        List<Refund> openRefunds = refundRepository.findByTripAndStatuses(
                tripId, List.of(RefundStatus.PENDING, RefundStatus.APPROVED));

        for (Refund refund : openRefunds) {
            Ticket ticket = refund.getTicket();
            TicketStatus ticketStatus = ticket.getTicketStatus();

            boolean customerRequest = refund.getRefundStatus() == RefundStatus.PENDING
                    && ticketStatus == TicketStatus.CONFIRMED;
            boolean approvedRequest = refund.getRefundStatus() == RefundStatus.APPROVED
                    && ticketStatus == TicketStatus.REFUNDED;
            // Hoàn khoản thanh toán trùng (vé vẫn CONFIRMED): hoàn tất, nhưng tiền vé gốc vẫn hoàn ở bước 2.
            boolean duplicatePayment = refund.getRefundStatus() == RefundStatus.APPROVED
                    && ticketStatus == TicketStatus.CONFIRMED;

            if (!customerRequest && !approvedRequest && !duplicatePayment) {
                // Vd. hoàn cho thanh toán về muộn (vé đã hủy): admin vẫn tự chuyển tiền rồi bấm hoàn tất.
                continue;
            }

            completeAutomatically(refund, ticket, now, !duplicatePayment);
            if (!duplicatePayment) {
                handledTicketIds.add(ticket.getTicketId());
            }
            touchedBookings.put(ticket.getBooking().getBookingId(), ticket.getBooking());
            count++;
            total = total.add(refund.getRefundAmount());
        }

        // 2. Vé đã thanh toán chưa có yêu cầu: tạo yêu cầu hoàn 100% và hoàn tất.
        List<Ticket> confirmedTickets = ticketRepository.findByTripAndStatuses(
                tripId, List.of(TicketStatus.CONFIRMED));

        String reason = I18n.t("refund.reason.tripCancelled");

        for (Ticket ticket : confirmedTickets) {
            if (!handledTicketIds.add(ticket.getTicketId())) {
                continue;
            }

            Refund refund = newSystemRefund(ticket, paidAmount(ticket), reason, count);
            completeAutomatically(refund, ticket, now, true);
            touchedBookings.put(ticket.getBooking().getBookingId(), ticket.getBooking());
            count++;
            total = total.add(refund.getRefundAmount());
        }

        touchedBookings.values().forEach(this::updateBookingStatusIfAllTicketsRefunded);

        return new TripCancellationResult(count, total, 0);
    }

    @Override
    @Transactional
    public void createRefundsForLatePayment(Booking booking, BigDecimal paidAmount) {
        if (booking == null || booking.getBookingId() == null
                || paidAmount == null || paidAmount.signum() <= 0) {
            return;
        }

        List<Ticket> tickets = ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(booking.getBookingId());

        if (tickets.isEmpty()) {
            return;
        }

        String reason = I18n.t("refund.reason.latePayment", booking.getBookingNumber());
        LocalDateTime now = LocalDateTime.now();
        BigDecimal remaining = paidAmount;

        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);
            boolean last = i == tickets.size() - 1;

            // Vé cuối nhận phần còn lại để tổng các yêu cầu đúng bằng số tiền khách đã trả.
            BigDecimal amount = last ? remaining : paidAmount(ticket).min(remaining);
            remaining = remaining.subtract(amount);

            if (amount.signum() <= 0) {
                continue;
            }

            Refund refund = newSystemRefund(ticket, amount, reason, i);
            refund.setRefundStatus(RefundStatus.APPROVED);
            refund.setProcessedAt(now);
            refundRepository.save(refund);
        }
    }

    // ---------------------------------------------------------------------

    /** Yêu cầu hoàn do hệ thống tạo: lấy thông tin liên hệ từ đơn, chưa có tài khoản ngân hàng. */
    private Refund newSystemRefund(Ticket ticket, BigDecimal amount, String reason, int sequence) {
        Booking booking = ticket.getBooking();

        Refund refund = new Refund();
        refund.setRefundCode(generateRefundCode(sequence));
        refund.setTicket(ticket);
        refund.setRefundAmount(amount);
        refund.setRefundReason(reason);
        refund.setCustomerName(booking.getBookerName());
        refund.setCustomerEmail(booking.getBookerEmail());
        refund.setCustomerPhone(booking.getBookerPhone());
        return refund;
    }

    /** Đưa yêu cầu về COMPLETED và trừ ví; {@code refundTicket} = vé chuyển REFUNDED (ghế được nhả). */
    private void completeAutomatically(Refund refund, Ticket ticket, LocalDateTime now, boolean refundTicket) {
        adminWalletService.debit(
                refund.getRefundAmount(),
                refund.getRefundCode(),
                I18n.t("wallet.tx.refundTripCancelled", refund.getRefundCode()));

        if (refund.getProcessedAt() == null) {
            refund.setProcessedAt(now);
        }
        refund.setRefundStatus(RefundStatus.COMPLETED);
        refund.setRefundedAt(now);
        refundRepository.save(refund);

        if (refundTicket && ticket.getTicketStatus() != TicketStatus.REFUNDED) {
            ticket.setTicketStatus(TicketStatus.REFUNDED);
            ticketRepository.save(ticket);
        }
    }

    /** Vé của chính khách, đã thanh toán, đã xác nhận, chuyến còn chạy. */
    private Ticket loadRefundableTicket(Long ticketId, User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException(I18n.t("err.pleaseLogInToRequestRefund"));
        }

        Ticket ticket = ticketRepository.findWithDetailsByTicketId(ticketId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.ticketDoesNotExist")));

        Booking booking = ticket.getBooking();

        if (booking == null || booking.getUser() == null) {
            throw new IllegalStateException(I18n.t("err.guestRefundIsNotSupported"));
        }

        if (!Objects.equals(booking.getUser().getUserId(), user.getUserId())) {
            throw new IllegalStateException(I18n.t("err.ticketDoesNotBelongToCurrentUser"));
        }

        if (booking.getBookingStatus() != BookingStatus.PAID) {
            throw new IllegalStateException(I18n.t("err.onlyPaidBookingsCanBeRefunded"));
        }

        if (ticket.getTicketStatus() != TicketStatus.CONFIRMED) {
            throw new IllegalStateException(I18n.t("err.onlyConfirmedTicketsCanBeRefunded"));
        }

        if (ticket.getTrainTrip() == null) {
            throw new IllegalStateException(I18n.t("err.ticketDoesNotHaveTrainTrip"));
        }

        if (ticket.getTrainTrip().getStatus() != TripStatus.SCHEDULED) {
            throw new IllegalStateException(I18n.t("err.onlyScheduledTripsCanBeRefunded"));
        }

        LocalDateTime boarding = boardingTime(ticket);
        if (boarding != null && !boarding.isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(I18n.t("err.cannotRefundAfterTrainDepartureTime"));
        }

        return ticket;
    }

    /** Được duyệt thì hoàn 100% số tiền khách đã trả cho vé. */
    private RefundQuote buildQuote(Ticket ticket) {
        BigDecimal paid = paidAmount(ticket);
        return new RefundQuote(paid, paid);
    }

    /** Giờ tàu rời ga lên của khách; không có lịch dừng thì lấy giờ khởi hành của chuyến. */
    private LocalDateTime boardingTime(Ticket ticket) {
        Booking booking = ticket.getBooking();

        if (booking != null && booking.getDepartureStation() != null && ticket.getTrainTrip() != null) {
            LocalDateTime stopTime = tripStationRepository
                    .findStop(ticket.getTrainTrip().getTripId(), booking.getDepartureStation().getStationId())
                    .map(stop -> stop.getDepartureTime())
                    .orElse(null);

            if (stopTime != null) {
                return stopTime;
            }
        }

        return ticket.getTrainTrip() != null ? ticket.getTrainTrip().getDepartureTime() : null;
    }

    /**
     * Số tiền khách thực trả cho một vé.
     * Vé mới đã lưu sẵn giá sau giảm giá nhóm; với vé tạo trước đó (giá vé chưa trừ giảm giá nhóm)
     * thì chia tổng tiền đơn theo tỉ lệ giá vé, làm tròn xuống, không vượt giá vé.
     */
    private BigDecimal paidAmount(Ticket ticket) {
        BigDecimal ticketPrice = ticket.getTicketPrice() == null ? BigDecimal.ZERO : ticket.getTicketPrice();
        Booking booking = ticket.getBooking();

        if (booking == null || booking.getTotalAmount() == null || ticketPrice.signum() <= 0) {
            return ticketPrice;
        }

        BigDecimal sumOfTicketPrices = ticketRepository
                .findByBooking_BookingIdOrderByTicketIdAsc(booking.getBookingId())
                .stream()
                .map(item -> item.getTicketPrice() == null ? BigDecimal.ZERO : item.getTicketPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sumOfTicketPrices.compareTo(booking.getTotalAmount()) <= 0) {
            return ticketPrice;
        }

        return ticketPrice
                .multiply(booking.getTotalAmount())
                .divide(sumOfTicketPrices, 0, RoundingMode.DOWN)
                .min(ticketPrice);
    }

    private void requireAdmin(User adminUser) {
        if (adminUser == null || adminUser.getUserId() == null) {
            throw new IllegalArgumentException(I18n.t("err.adminIsRequired"));
        }
    }

    private Refund findRefund(Long refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException(I18n.t("err.refundRequestDoesNotExist")));
    }

    private void updateBookingStatusIfAllTicketsRefunded(Booking booking) {
        if (booking == null || booking.getBookingId() == null) {
            return;
        }

        List<Ticket> tickets = ticketRepository.findByBooking_BookingIdOrderByTicketIdAsc(
                booking.getBookingId()
        );

        boolean allRefunded = !tickets.isEmpty()
                && tickets.stream().allMatch(ticket -> ticket.getTicketStatus() == TicketStatus.REFUNDED);

        if (allRefunded) {
            booking.setBookingStatus(BookingStatus.REFUNDED);
            bookingRepository.save(booking);
        }
    }

    /** Mã hoàn tiền; {@code sequence} tránh trùng mã khi tạo nhiều yêu cầu trong cùng một mili giây. */
    private String generateRefundCode(int sequence) {
        return "RF" + System.currentTimeMillis() + String.format("%03d", sequence % 1000);
    }
}
